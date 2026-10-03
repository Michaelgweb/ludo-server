package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.service.GameFlowService;
import com.yourcompany.ludo.service.GameFlowService.RollResult;
import com.yourcompany.ludo.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class DiceController {

    private static final Logger log = LoggerFactory.getLogger(DiceController.class);

    private final GameFlowService flow;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate messagingTemplate;

    public DiceController(GameFlowService flow, JwtUtil jwtUtil, SimpMessagingTemplate messagingTemplate) {
        this.flow = flow;
        this.jwtUtil = jwtUtil;
        this.messagingTemplate = messagingTemplate;
    }

    /** ডাইস রোল। প্রথম রোলে দুজনের ফি কাটে (GameFlowService এ) */
    @PostMapping("/roll/{sessionId}")
    public ResponseEntity<?> rollDice(@PathVariable Long sessionId,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            RollResult r = flow.rollDice(sessionId, gameId);     // কমিট হয়ে ফিরে আসে

            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("event", r.cancelled() ? "GAME_CANCELLED" : "DICE_ROLLED");
            payload.put("dice", r.dice());
            payload.put("rolledBy", r.player());
            payload.put("nextPlayer", r.nextPlayer());
            payload.put("message", r.message());

            if (!r.cancelled()) {
                // আপনার আগের ক্লায়েন্ট এই টপিকেই শোনে (sessionId দিয়ে)
                messagingTemplate.convertAndSend("/topic/game/" + sessionId, payload);
            }
            return ResponseEntity.ok(payload);

        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("rollDice failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "সার্ভারে সমস্যা হয়েছে"));
        }
    }

    /**
     * ইউজার নিজে ব্যাক/কুইট দিলে এটা কল করবে। নিয়ম GameFlowService.leave এ:
     * দুজন রোল করার আগে হলে বাতিল/রিফান্ড, পরে হলে সে হারবে।
     */
    @PostMapping("/leave/{sessionId}")
    public ResponseEntity<?> leave(@PathVariable Long sessionId,
                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            flow.leave(sessionId, gameId);
            return ResponseEntity.ok(Map.of("status", "LEFT"));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("leave failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "সার্ভারে সমস্যা হয়েছে"));
        }
    }

    private String gameIdFrom(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing Authorization header");
        }
        return jwtUtil.getGameIdFromToken(authHeader.substring(7));
    }
}
