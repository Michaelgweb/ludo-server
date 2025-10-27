package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.GameBoardState;
import com.yourcompany.ludo.dto.GameMoveRequest;
import com.yourcompany.ludo.engine.GameAutoMoveTask;
import com.yourcompany.ludo.engine.LudoGameEngine;
import com.yourcompany.ludo.model.GameResult;
import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

@Controller
public class GameSocketController {

    private static final Logger logger = LoggerFactory.getLogger(GameSocketController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<Long, LudoGameEngine> games = new HashMap<>();
    private final Map<Long, Timer> gameTimers = new HashMap<>();
    private final GameSessionRepository gameRepo;
    private final GameService gameService;

    public GameSocketController(SimpMessagingTemplate messagingTemplate,
                                GameSessionRepository gameRepo,
                                GameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameRepo = gameRepo;
        this.gameService = gameService;
    }

    // 🎯 Send initial game state to everyone
    public void sendInitialState(Long gameId) {
        games.putIfAbsent(gameId, new LudoGameEngine());
        LudoGameEngine engine = games.get(gameId);

        GameBoardState state = new GameBoardState(
                engine.getPlayerTokens(),
                engine.getCurrentPlayer(),
                engine.isGameOver()
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId, state);
        logger.info("[GameSocketController] Initial state sent for game {}", gameId);
    }

    // 🎯 Notify single player (for MATCH_FOUND)
    public void notifyPlayer(Long playerId, Long gameId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "MATCH_FOUND");
        payload.put("gameId", gameId);
        messagingTemplate.convertAndSend("/topic/match/" + playerId, payload);
        logger.info("[GameSocketController] Player {} notified for game {}", playerId, gameId);
    }

    @MessageMapping("/game/move")
    public void handleMove(GameMoveRequest move) {
        GameSession session = gameRepo.findById(move.getGameId()).orElseThrow();
        games.putIfAbsent(move.getGameId(), new LudoGameEngine());
        LudoGameEngine engine = games.get(move.getGameId());

        // Update dice count
        if (move.getPlayerId() == 0) {
            session.setPlayer1DiceCount(session.getPlayer1DiceCount() + 1);
        } else {
            session.setPlayer2DiceCount(session.getPlayer2DiceCount() + 1);
        }
        gameRepo.save(session);

        // Auto cancel if no two rolls in 2 minutes
        if (session.getStartTime() != null &&
                (session.getPlayer1DiceCount() < 2 || session.getPlayer2DiceCount() < 2) &&
                session.getStartTime().isBefore(LocalDateTime.now().minusMinutes(2))) {
            session.setStatus("CANCELLED");
            gameRepo.save(session);
            return;
        }

        boolean moved = engine.moveToken(move.getPlayerId(), move.getTokenIndex(), move.getDiceValue());
        if (!moved) return;

        checkGameOverAndUpdateDB(move.getGameId(), engine);

        GameBoardState state = new GameBoardState(
                engine.getPlayerTokens(),
                engine.getCurrentPlayer(),
                engine.isGameOver()
        );

        messagingTemplate.convertAndSend("/topic/game/" + move.getGameId(), state);
        resetAutoMoveTimer(move.getGameId(), engine);
    }

    private void resetAutoMoveTimer(Long gameId, LudoGameEngine engine) {
        if (gameTimers.containsKey(gameId)) {
            gameTimers.get(gameId).cancel();
        }
        Timer timer = new Timer();
        timer.schedule(new GameAutoMoveTask(gameId, engine, messagingTemplate, this), 30_000);
        gameTimers.put(gameId, timer);
    }

    public void checkGameOverAndUpdateDB(Long gameId, LudoGameEngine engine) {
        if (!engine.isGameOver()) return;

        GameSession session = gameRepo.findById(gameId).orElse(null);
        if (session == null || "COMPLETED".equals(session.getStatus())) return;

        int winnerIndex = engine.getWinner();

        if (winnerIndex == -1) {
            gameService.finishDraw(session);
            logger.info("[GameSocketController] Game {} finished as DRAW.", gameId);
        } else {
            GameResult result = new GameResult();
            result.setGameSession(session);
            if (winnerIndex == 0) {
                result.setWinner(session.getPlayer1());
                result.setLoser(session.getPlayer2());
            } else {
                result.setWinner(session.getPlayer2());
                result.setLoser(session.getPlayer1());
            }
            gameService.finish(result);
            logger.info("[GameSocketController] Game {} finished. Winner credited.", gameId);
        }
    }

    @MessageMapping("/game/leave")
    public void handleLeave(GameMoveRequest move) {
        games.putIfAbsent(move.getGameId(), new LudoGameEngine());
        LudoGameEngine engine = games.get(move.getGameId());
        engine.forceGameOverDueToLeave();

        checkGameOverAndUpdateDB(move.getGameId(), engine);

        GameBoardState state = new GameBoardState(
                engine.getPlayerTokens(),
                engine.getCurrentPlayer(),
                true
        );
        messagingTemplate.convertAndSend("/topic/game/" + move.getGameId(), state);
    }
}
