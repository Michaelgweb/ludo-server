package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Random;

@RestController
@RequestMapping("/api/game")
public class DiceController {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Random random = new Random();

    @PostMapping("/roll/{sessionId}")
    public ResponseEntity<?> rollDice(@PathVariable Long sessionId,
                                      @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String gameId = jwtUtil.getGameIdFromToken(token);

        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Game session not found"));

        if (!"ONGOING".equals(session.getStatus())) {
            return ResponseEntity.badRequest().body("Game is not active");
        }

        int currentPlayerNum;
        if (session.getPlayer1().getGameId().equals(gameId)) {
            currentPlayerNum = 1;
        } else if (session.getPlayer2().getGameId().equals(gameId)) {
            currentPlayerNum = 2;
        } else {
            return ResponseEntity.badRequest().body("You are not in this game");
        }

        Integer currentPlayer = session.getCurrentPlayer();
        if (currentPlayer == null || !currentPlayer.equals(currentPlayerNum)) {
            return ResponseEntity.badRequest().body("It's not your turn!");
        }

        int roll = random.nextInt(6) + 1;

        // Consecutive sixes
        if (roll == 6) {
            session.setConsecutiveSixCount(session.getConsecutiveSixCount() + 1);
            if (session.getConsecutiveSixCount() >= 3) {
                roll = random.nextInt(5) + 1; // 1–5
                session.setConsecutiveSixCount(0);
            }
        } else {
            session.setConsecutiveSixCount(0);
        }

        session.setLastDiceValue(roll);

        // Update dice count for current player
        if (currentPlayerNum == 1) {
            session.setPlayer1DiceCount(session.getPlayer1DiceCount() + 1);
        } else {
            session.setPlayer2DiceCount(session.getPlayer2DiceCount() + 1);
        }

        // Entry fee deduction: only after both players rolled at least once and fee not yet deducted
        if (!session.isFeeDeducted()
                && session.getPlayer1DiceCount() >= 1
                && session.getPlayer2DiceCount() >= 1) {

            BigDecimal entryFee = session.getEntryFee() != null ? session.getEntryFee() : BigDecimal.ZERO;

            userService.deductBalance(session.getPlayer1().getGameId(), entryFee);
            userService.deductBalance(session.getPlayer2().getGameId(), entryFee);

            session.setFeeDeducted(true);
        }

        // Change turn if roll != 6
        if (roll != 6) {
            session.setCurrentPlayer(currentPlayerNum == 1 ? 2 : 1);
        }

        // Save session and notify
        gameSessionRepository.save(session);
        messagingTemplate.convertAndSend("/topic/game/" + sessionId, session);

        return ResponseEntity.ok(session);
    }
}
