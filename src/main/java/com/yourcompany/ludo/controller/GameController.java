package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * শুধু হিস্ট্রি। ম্যাচমেকিং এখন MatchController (/api/match/start),
 * রোল/কুইট DiceController, আর জয়/পরাজয়ের টাকা GameFlowService।
 */
@RestController
@RequestMapping("/game")
public class GameController {

    private final GameSessionRepository gameRepo;

    public GameController(GameSessionRepository gameRepo) {
        this.gameRepo = gameRepo;
    }

    /** নিজের ম্যাচ হিস্ট্রি (অন্যের userId দিয়ে দেখা যাবে না, পাসওয়ার্ড/ব্যালেন্স যাবে না) */
    @GetMapping("/history")
    public ResponseEntity<?> history(@AuthenticationPrincipal User user) {
        List<GameSession> games = gameRepo.findByPlayer1OrPlayer2(user, user);
        List<Map<String, Object>> out = games.stream().map(g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("sessionId", g.getId());
            m.put("status", g.getStatus());
            m.put("entryFee", g.getEntryFee());
            m.put("totalPot", g.getTotalPot());
            m.put("startTime", g.getStartTime());
            m.put("endTime", g.getEndTime());
            m.put("player1GameId", g.getPlayer1() != null ? g.getPlayer1().getGameId() : null);
            m.put("player2GameId", g.getPlayer2() != null ? g.getPlayer2().getGameId() : null);
            m.put("winnerGameId", g.getWinner() != null ? g.getWinner().getGameId() : null);
            m.put("won", g.getWinner() != null && g.getWinner().getId().equals(user.getId()));
            return m;
        }).toList();
        return ResponseEntity.ok(out);
    }
}
