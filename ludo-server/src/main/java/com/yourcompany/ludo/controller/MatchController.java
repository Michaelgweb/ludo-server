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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // single-thread scheduler for delayed start tasks (simple and safe)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 🎯 ম্যাচ শুরু (match found → set matchStartTimestamp এবং schedule actual start)
     */
    @PostMapping("/start")
    public ResponseEntity<?> startMatch(@RequestBody MatchRequestDto requestDto,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
            }

            String token = authHeader.replace("Bearer ", "");
            String gameId = jwtUtil.getGameIdFromToken(token);

            User user = userService.findByGameId(gameId)
                    .orElseThrow(() -> new RuntimeException("User not found."));

            // নতুন: ইউজারের ACTIVE ম্যাচ আছে কিনা চেক (MATCH_FOUND বা ONGOING)
            List<GameSession> activeSessions = gameSessionRepository.findActiveSessionsByPlayerGameId(gameId);
            boolean hasActiveMatch = activeSessions.stream()
                    .anyMatch(session -> "MATCH_FOUND".equals(session.getStatus()) || "ONGOING".equals(session.getStatus()));

            if (hasActiveMatch) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "You already have an active match. Finish or cancel it before starting a new one."));
            }

            // tryMatch returns either a waiting/created session or a matched session (with player2)
            GameSession session = matchService.tryMatch(user, requestDto.getEntryFee().intValue());

            // যদি ম্যাচ দুইজন প্লেয়ারের মধ্যে হয়ে যায় → schedule start
            if (session != null && session.getPlayer1() != null && session.getPlayer2() != null) {

                // set matchStartTimestamp (10 sec later) — adjust as needed
                long matchStartTimestamp = System.currentTimeMillis() + 10_000L;
                session.setMatchStartTimestamp(matchStartTimestamp);
                session.setStartTime(LocalDateTime.now());
                session.setStatus("MATCH_FOUND");
                gameSessionRepository.save(session);

                Map<String, Object> payload = buildMatchPayload(session);

                // Notify both players that match was found and when it will start
                messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer1().getGameId(), payload);
                messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer2().getGameId(), payload);

                // schedule the actual game start (set to ONGOING and broadcast game-start)
                scheduleGameStart(session.getId(), matchStartTimestamp);

                return ResponseEntity.ok(payload);
            }

            // যদি এখনো opponent না পাওয়া যায় → WAITING ফেরত
            return ResponseEntity.ok(Map.of("status", "WAITING"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Schedule a task to flip session -> ONGOING at matchStartTimestamp, notify both players,
     * and broadcast a /topic/game/{sessionId} "gameStart" payload so frontends can actually begin.
     */
    private void scheduleGameStart(Long sessionId, long matchStartTimestamp) {
        long delayMillis = matchStartTimestamp - System.currentTimeMillis();
        if (delayMillis < 0) delayMillis = 0;

        scheduler.schedule(() -> {
            try {
                GameSession session = gameSessionRepository.findById(sessionId).orElse(null);
                if (session == null) return;

                if ("CANCELLED".equals(session.getStatus()) || "ONGOING".equals(session.getStatus())) {
                    return;
                }

                session.setStatus("ONGOING");
                if (session.getStartTime() == null) {
                    session.setStartTime(LocalDateTime.now());
                }
                gameSessionRepository.save(session);

                Map<String, Object> matchUpdate = new HashMap<>();
                matchUpdate.put("gameId", session.getId());
                matchUpdate.put("status", session.getStatus());
                matchUpdate.put("matchStartTimestamp", session.getMatchStartTimestamp());

                if (session.getPlayer1() != null) {
                    messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer1().getGameId(), matchUpdate);
                }
                if (session.getPlayer2() != null) {
                    messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer2().getGameId(), matchUpdate);
                }

                Map<String, Object> gameStartPayload = new HashMap<>();
                gameStartPayload.put("sessionId", session.getId());
                gameStartPayload.put("event", "GAME_STARTED");
                gameStartPayload.put("startingPlayerGameId", session.getPlayer1() != null ? session.getPlayer1().getGameId() : null);

                messagingTemplate.convertAndSend("/topic/game/" + session.getId(), gameStartPayload);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 🎯 ডাইস রোল - ফি কাটার লজিক সহ
     */
    @PostMapping("/rollDice/{sessionId}")
    public ResponseEntity<?> rollDice(@PathVariable Long sessionId,
                                      @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
            }

            String token = authHeader.replace("Bearer ", "");
            String gameId = jwtUtil.getGameIdFromToken(token);

            GameSession session = gameSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            if (!"ONGOING".equals(session.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Match not active"));
            }

            boolean isPlayer1 = session.getPlayer1() != null && session.getPlayer1().getGameId().equals(gameId);
            if (isPlayer1) {
                session.setPlayer1DiceCount(session.getPlayer1DiceCount() + 1);
            } else if (session.getPlayer2() != null && session.getPlayer2().getGameId().equals(gameId)) {
                session.setPlayer2DiceCount(session.getPlayer2DiceCount() + 1);
            } else {
                return ResponseEntity.status(403).body(Map.of("error", "Not your match"));
            }

            // fee deduction logic
            if (!session.isFeeDeducted()
                    && session.getPlayer1DiceCount() >= 2
                    && session.getPlayer2DiceCount() >= 2) {

                userService.deductBalance(session.getPlayer1(), session.getEntryFee());
                userService.deductBalance(session.getPlayer2(), session.getEntryFee());
                session.setFeeDeducted(true);
            }

            gameSessionRepository.save(session);

            return ResponseEntity.ok(Map.of(
                    "player1DiceCount", session.getPlayer1DiceCount(),
                    "player2DiceCount", session.getPlayer2DiceCount(),
                    "feeDeducted", session.isFeeDeducted()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 🎯 ম্যাচ স্ট্যাটাস চেক (sessionId দিয়ে)
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> getMatchStatusById(@PathVariable Long sessionId) {
        return gameSessionRepository.findById(sessionId)
                .map(session -> ResponseEntity.ok(buildMatchPayload(session)))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "No match found.")));
    }

    /**
     * 🎯 লগইন করা প্লেয়ার এর Active ম্যাচ চেক + ৬০ সেকেন্ড Auto Cancel
     */
    @GetMapping("/status")
    public ResponseEntity<?> getMatchStatusForPlayer(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
            }

            String token = authHeader.replace("Bearer ", "");
            String gameId = jwtUtil.getGameIdFromToken(token);

            List<GameSession> sessions = gameSessionRepository.findActiveSessionsByPlayerGameId(gameId);

            if (sessions.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "No active match found."));
            }

            GameSession session = sessions.get(0); // first active session

            // Auto-cancel logic (60 seconds inactivity)
            if (session.getStartTime() != null &&
                    LocalDateTime.now().isAfter(session.getStartTime().plusSeconds(10)) &&
                    session.getPlayer1DiceCount() == 0 &&
                    session.getPlayer2DiceCount() == 0) {

                session.setStatus("CANCELLED");
                gameSessionRepository.save(session);

                Map<String, Object> payload = new HashMap<>();
                payload.put("gameId", session.getId());
                payload.put("status", "CANCELLED");
                payload.put("message", "Match auto-cancelled due to inactivity.");

                if (session.getPlayer1() != null) {
                    messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer1().getGameId(), payload);
                }
                if (session.getPlayer2() != null) {
                    messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer2().getGameId(), payload);
                }
                return ResponseEntity.ok(payload);
            }

            return ResponseEntity.ok(buildMatchPayload(session));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 🎯 Common payload builder
     */
    private Map<String, Object> buildMatchPayload(GameSession session) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId", session.getId());
        data.put("player1", session.getPlayer1() != null ? session.getPlayer1().getGameId() : null);
        data.put("player2", session.getPlayer2() != null ? session.getPlayer2().getGameId() : null);
        data.put("entryFee", session.getEntryFee());
        data.put("status", session.getStatus());
        data.put("startTime", session.getStartTime());
        data.put("endTime", session.getEndTime());
        data.put("matchStartTimestamp", session.getMatchStartTimestamp());
        return data;
    }

    @PreDestroy
    public void shutdownScheduler() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
