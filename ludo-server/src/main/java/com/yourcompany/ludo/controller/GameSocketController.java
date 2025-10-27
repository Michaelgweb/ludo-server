package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.GameBoardState;
import com.yourcompany.ludo.dto.GameMoveRequest;
import com.yourcompany.ludo.engine.GameAutoMoveTask;
import com.yourcompany.ludo.engine.LudoGameEngine;
import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

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

    @MessageMapping("/game/move")
    public void handleMove(GameMoveRequest move) {
        games.putIfAbsent(move.getGameId(), new LudoGameEngine());
        LudoGameEngine engine = games.get(move.getGameId());

        logger.info("[GameSocketController] Player {} made a move in game {}", move.getPlayerId(), move.getGameId());
        engine.moveToken(move.getPlayerId(), move.getTokenIndex(), move.getDiceValue());

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
        if (engine.isGameOver()) {
            GameSession session = gameRepo.findById(gameId).orElse(null);
            if (session != null && session.getWinner() == null) {
                int winnerIndex = engine.getWinner();
                if (winnerIndex != -1) {
                    gameService.finishGame(session,
                            winnerIndex == 0 ? session.getPlayer1() : session.getPlayer2());
                    logger.info("[GameSocketController] Game {} finished, winner credited.", gameId);
                }
            }
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
