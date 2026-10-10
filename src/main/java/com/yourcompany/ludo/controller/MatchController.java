package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.MatchRequestDto;
import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.MatchService;
import com.yourcompany.ludo.service.TelegramService;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    private final MatchService matchService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameSessionRepository gameSessionRepository;
    private final TelegramService telegram;

    public MatchController(MatchService matchService,
                           UserService userService,
                           JwtUtil jwtUtil,
                           SimpMessagingTemplate messagingTemplate,
                           GameSessionRepository gameSessionRepository,
                           TelegramService telegram) {
        this.matchService = matchService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.messagingTemplate = messagingTemplate;
        this.gameSessionRepository = gameSessionRepository;
        this.telegram = telegram;
    }

    // ---------------------------------------------------------------
    // ম্যাচ খোঁজা
    // ---------------------------------------------------------------
    @PostMapping("/start")
    public ResponseEntity<?> startMatch(@RequestBody MatchRequestDto requestDto,
                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            User user = userService.findByGameId(gameId)
                    .orElseThrow(() -> new IllegalArgumentException("ব্যবহারকারী পাওয়া যায়নি।"));

            BigDecimal entryFee = BigDecimal.valueOf(requestDto.getEntryFee());
            GameSession session = matchService.tryMatch(user, entryFee);

            if (session == null) {
                // কেউ অপেক্ষায় ঢুকেছে (ম্যাচ সেভ হয়ে গেছে, এরপর নোটিফাই)
                notifyTelegram("⏳ প্লেয়ার ওয়েটিং\n"
                        + "User: " + gameId + "\n"
                        + "Amount: " + entryFee.stripTrailingZeros().toPlainString() + " টাকা\n"
                        + "Status: WAITING");
                return ResponseEntity.ok(Map.of("status", "WAITING"));
            }

            Map<String, Object> payload = buildMatchPayload(session);
            // দুজনকেই sessionId পুশ। প্রথম অপেক্ষমাণ জন এখান থেকেই জানবে
            messagingTemplate.convertAndSend("/topic/user/" + session.getPlayer1().getGameId(), payload);
            messagingTemplate.convertAndSend("/topic/user/" + session.getPlayer2().getGameId(), payload);
            // আগের ক্লায়েন্টের সাথে সামঞ্জস্য
            messagingTemplate.convertAndSend("/topic/match/session/" + session.getId(), payload);

            notifyTelegram("✅ ম্যাচ হয়েছে\n"
                    + "Session: " + session.getId() + "\n"
                    + "Player 1: " + session.getPlayer1().getGameId() + "\n"
                    + "Player 2: " + session.getPlayer2().getGameId() + "\n"
                    + "Entry Fee: " + session.getEntryFee() + " টাকা\n"
                    + "Total Pot: " + session.getTotalPot() + " টাকা\n"
                    + "Status: " + session.getStatus());

            return ResponseEntity.ok(payload);

        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "অথরাইজেশন হেডার নেই"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));   // আমাদের নিজের লেখা মেসেজ
        } catch (Exception e) {
            log.error("startMatch failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "সার্ভারে সমস্যা হয়েছে, আবার চেষ্টা করুন"));
        }
    }

    /** সার্চ স্ক্রিন বন্ধ করলে অপেক্ষা বাতিল */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelWaiting(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            User user = userService.findByGameId(gameId)
                    .orElseThrow(() -> new IllegalArgumentException("ব্যবহারকারী পাওয়া যায়নি।"));
            matchService.cancelWaiting(user);

            notifyTelegram("🚫 অপেক্ষা বাতিল\n"
                    + "User: " + gameId + "\n"
                    + "Status: CANCELLED");

            return ResponseEntity.ok(Map.of("status", "CANCELLED"));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "অথরাইজেশন হেডার নেই"));
        } catch (Exception e) {
            log.error("cancelWaiting failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "সার্ভারে সমস্যা হয়েছে"));
        }
    }

    /** অ্যাপ রিস্টার্ট বা মেসেজ মিস হলে চলমান ম্যাচ ফিরে পাওয়ার জন্য */
    @GetMapping("/current")
    public ResponseEntity<?> currentMatch(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            List<GameSession> active = gameSessionRepository.findActiveSessionsByPlayerGameId(gameId);
            if (active.isEmpty()) return ResponseEntity.ok(Map.of("status", "NONE"));
            return ResponseEntity.ok(buildMatchPayload(active.get(0)));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "অথরাইজেশন হেডার নেই"));
        }
    }

    // ---------------------------------------------------------------
    // প্রতিপক্ষের প্রোফাইল (ব্যালেন্স দেখানো হয় না)
    // ---------------------------------------------------------------
    @GetMapping("/opponent/{sessionId}")
    public ResponseEntity<?> getOpponentProfile(@PathVariable Long sessionId,
                                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String myGameId = gameIdFrom(authHeader);
            GameSession session = gameSessionRepository.findById(sessionId).orElse(null);
            if (session == null) return ResponseEntity.status(404).body(Map.of("error", "Session not found"));

            int me = session.slotOf(myGameId);          // খেলোয়াড় না হলে IllegalArgumentException
            User opponent = me == 1 ? session.getPlayer2() : session.getPlayer1();
            if (opponent == null) return ResponseEntity.status(404).body(Map.of("error", "Opponent not found"));

            Map<String, Object> profile = new HashMap<>();
            profile.put("gameId", opponent.getGameId());
            profile.put("displayName", opponent.getDisplayName());
            profile.put("avatarUrl", opponent.getAvatarUrl());
            return ResponseEntity.ok(profile);

        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
    }

    // ---------------------------------------------------------------
    // ম্যাচের স্ট্যাটাস (শুধু ওই ম্যাচের খেলোয়াড়)
    // ---------------------------------------------------------------
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<?> getMatchStatus(@PathVariable Long sessionId,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            GameSession session = gameSessionRepository.findById(sessionId).orElse(null);
            if (session == null) return ResponseEntity.status(404).body(Map.of("error", "Session not found"));

            session.slotOf(gameId);                     // খেলোয়াড় না হলে exception
            return ResponseEntity.ok(buildMatchPayload(session));

        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** টেলিগ্রাম যাক বা না যাক, ম্যাচের কাজে কোনো প্রভাব পড়বে না */
    private void notifyTelegram(String text) {
        try {
            telegram.send(text);
        } catch (Exception e) {
            log.warn("Telegram notify failed: {}", e.getMessage());
        }
    }

    private String gameIdFrom(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing Authorization header");
        }
        return jwtUtil.getGameIdFromToken(authHeader.substring(7));
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
        data.put("serverTime", System.currentTimeMillis());   // ক্লায়েন্টের ঘড়ি ভুল হলেও কাউন্টডাউন ঠিক রাখতে
        return data;
    }
}
