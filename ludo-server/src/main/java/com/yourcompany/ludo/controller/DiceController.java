package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/game")
public class DiceController {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/roll/{sessionId}")
    public ResponseEntity<?> rollDice(@PathVariable Long sessionId,
                                      @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String gameId = jwtUtil.getGameIdFromToken(token);

        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Game session not found"));

        if (!session.getStatus().equals("ONGOING")) {
            return ResponseEntity.badRequest().body("Game is not active");
        }

        if (session.getPlayer1().getGameId().equals(gameId)) {
            session.setPlayer1DiceCount(session.getPlayer1DiceCount() + 1);
        } else if (session.getPlayer2().getGameId().equals(gameId)) {
            session.setPlayer2DiceCount(session.getPlayer2DiceCount() + 1);
        } else {
            return ResponseEntity.badRequest().body("You are not in this game");
        }

        // চেক করো দুইজন মিলে ৪ বার ডাইস রোল করেছে কিনা
        if (!session.isFeeDeducted()
                && session.getPlayer1DiceCount() >= 2
                && session.getPlayer2DiceCount() >= 2) {

            // ফি কাটার লজিক
            User p1 = session.getPlayer1();
            User p2 = session.getPlayer2();
            p1.setBalance(p1.getBalance() - session.getEntryFee());
            p2.setBalance(p2.getBalance() - session.getEntryFee());
            userService.save(p1);
            userService.save(p2);

            session.setFeeDeducted(true);
        }

        gameSessionRepository.save(session);
        return ResponseEntity.ok(session);
    }
}
