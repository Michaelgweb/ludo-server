package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.service.GameFlowService;
import com.yourcompany.ludo.service.GameFlowService.RollResult;
import com.yourcompany.ludo.service.GameMoveService;
import com.yourcompany.ludo.service.GameMoveService.MoveResult;
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
    private final GameMoveService moves;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate messagingTemplate;

    public DiceController(GameFlowService flow, GameMoveService moves,
                          JwtUtil jwtUtil, SimpMessagingTemplate messagingTemplate) {
        this.flow = flow;
        this.moves = moves;
        this.jwtUtil = jwtUtil;
        this.messagingTemplate = messagingTemplate;
    }

    public record MoveRequest(int tokenIndex) {}

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
            payload.put("canMove", r.canMove());                 // true হলে rolledBy কে /move কল করতে হবে
            payload.put("message", r.message());

            if (!r.cancelled()) {
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

    /** টোকেন চাল। body: {"tokenIndex": 0..3}। যাচাই, কাটা ও জেতা সবই সার্ভারে */
    @PostMapping("/move/{sessionId}")
    public ResponseEntity<?> move(@PathVariable Long sessionId,
                                  @RequestBody MoveRequest req,
                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            MoveResult r = moves.move(sessionId, gameId, req.tokenIndex());

            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("event", "TOKEN_MOVED");
            payload.put("movedBy", r.player());
            payload.put("token", r.token());
            payload.put("from", r.from());
            payload.put("to", r.to());
            payload.put("captured", r.captured());
            payload.put("nextPlayer", r.nextPlayer());
            payload.put("finished", r.finished());
            payload.put("winnerGameId", r.winnerGameId());
            payload.put("player1Tokens", r.player1Tokens());
            payload.put("player2Tokens", r.player2Tokens());

            messagingTemplate.convertAndSend("/topic/game/" + sessionId, payload);
            return ResponseEntity.ok(payload);

        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("move failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "সার্ভারে সমস্যা হয়েছে"));
        }
    }

    /**
     * ম্যাচের বর্তমান অবস্থা। অ্যাপ রিস্টার্ট বা WebSocket রিকানেক্টের পর
     * ক্লায়েন্ট এটা কল করে টোকেন, পালা ও পেন্ডিং চাল ফিরে পায়।
     */
    @GetMapping("/state/{sessionId}")
    public ResponseEntity<?> state(@PathVariable Long sessionId,
                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String gameId = gameIdFrom(authHeader);
            return ResponseEntity.ok(moves.state(sessionId, gameId));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        } catch (IllegalArgumentException e) {
            if ("Session not found".equals(e.getMessage())) {
                return ResponseEntity.status(404).body(Map.of("error", "Session not found"));
            }
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        } catch (Exception e) {
            log.error("state failed", e);
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
