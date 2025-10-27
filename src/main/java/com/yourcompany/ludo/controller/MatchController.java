package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.MatchRequestDto;
import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.MatchService;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int COUNTDOWN_SECONDS = 10;

    @PostMapping("/start")
    public ResponseEntity<?> startMatch(@RequestBody MatchRequestDto requestDto,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error", "অথরাইজেশন হেডার নেই"));
            }

            String token = authHeader.replace("Bearer ", "");
            String gameId = jwtUtil.getGameIdFromToken(token);

            User user = userService.findByGameId(gameId)
                    .orElseThrow(() -> new RuntimeException("ব্যবহারকারী পাওয়া যায়নি।"));

            boolean hasActiveMatch = gameSessionRepository.findActiveSessionsByPlayerGameId(gameId)
                    .stream()
                    .anyMatch(session -> "MATCH_FOUND".equals(session.getStatus()) || "ONGOING".equals(session.getStatus()));

            if (hasActiveMatch) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "আপনার একটি সক্রিয় ম্যাচ আছে। আগে শেষ করুন।"));
            }

            BigDecimal entryFee = BigDecimal.valueOf(requestDto.getEntryFee());
            GameSession session = matchService.tryMatch(user, entryFee);

            if (session != null && session.getPlayer1() != null && session.getPlayer2() != null) {
                long matchStartMillis = Instant.now().plusSeconds(COUNTDOWN_SECONDS).toEpochMilli();
                session.setMatchStartTimestamp(matchStartMillis);
                session.setStatus("MATCH_FOUND");
                gameSessionRepository.save(session);

                Map<String, Object> payload = new HashMap<>();
                payload.put("sessionId", session.getId());
                payload.put("player1GameId", session.getPlayer1().getGameId());
                payload.put("player2GameId", session.getPlayer2().getGameId());
                payload.put("entryFee", session.getEntryFee());
                payload.put("totalPot", session.getTotalPot());
                payload.put("status", session.getStatus());
                payload.put("matchStartTimestamp", matchStartMillis);

                messagingTemplate.convertAndSend("/topic/match/session/" + session.getId(), payload);

                return ResponseEntity.ok(payload);
            }

            return ResponseEntity.ok(Map.of("status", "WAITING"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }



    @GetMapping("/opponent/{sessionId}")
    public ResponseEntity<?> getOpponentProfile(@PathVariable Long sessionId,
                                                @RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        }

        String token = authHeader.replace("Bearer ", "");
        String myGameId = jwtUtil.getGameIdFromToken(token);

        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        User opponent = null;
        if (session.getPlayer1() != null && session.getPlayer1().getGameId().equals(myGameId)) {
            opponent = session.getPlayer2();
        } else if (session.getPlayer2() != null && session.getPlayer2().getGameId().equals(myGameId)) {
            opponent = session.getPlayer1();
        }

        if (opponent == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Opponent not found"));
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("gameId", opponent.getGameId());
        profile.put("displayName", opponent.getDisplayName());
        profile.put("avatarUrl", opponent.getAvatarUrl());
        profile.put("balance", opponent.getBalance());

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> getMatchStatus(@PathVariable Long sessionId,
                                            @RequestHeader("Authorization") String authHeader) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        return ResponseEntity.ok(buildMatchPayload(session));
    }

    private void scheduleGameStart(Long sessionId, long matchStartMillis) {
        long delayMillis = Math.max(0, matchStartMillis - Instant.now().toEpochMilli());

        scheduler.schedule(() -> {
            try {
                GameSession session = gameSessionRepository.findById(sessionId).orElse(null);
                if (session == null || "CANCELLED".equals(session.getStatus()) || "ONGOING".equals(session.getStatus())) return;

                session.setStatus("ONGOING");
                if (session.getStartTime() == null) session.setStartTime(java.time.LocalDateTime.now());
                gameSessionRepository.save(session);

                Map<String, Object> gameStartPayload = new HashMap<>();
                gameStartPayload.put("sessionId", session.getId());
                gameStartPayload.put("event", "GAME_STARTED");
                gameStartPayload.put("startingPlayerGameId", session.getPlayer1() != null ? session.getPlayer1().getGameId() : null);

                if (session.getPlayer1() != null)
                    messagingTemplate.convertAndSend("/topic/game/" + session.getPlayer1().getGameId(), gameStartPayload);
                if (session.getPlayer2() != null)
                    messagingTemplate.convertAndSend("/topic/game/" + session.getPlayer2().getGameId(), gameStartPayload);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdownScheduler() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }

    private Map<String, Object> buildMatchPayload(GameSession session) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", session.getId());
        data.put("player1GameId", session.getPlayer1() != null ? session.getPlayer1().getGameId() : null);
        data.put("player2GameId", session.getPlayer2() != null ? session.getPlayer2().getGameId() : null);
        data.put("entryFee", session.getEntryFee());
        data.put("totalPot", session.getTotalPot());
        data.put("status", session.getStatus());
        data.put("startTime", session.getStartTime());
        data.put("endTime", session.getEndTime());
        data.put("matchStartTimestamp", session.getMatchStartTimestamp());
        return data;
    }
}
