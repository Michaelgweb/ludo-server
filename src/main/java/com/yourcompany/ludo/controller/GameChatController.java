package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.service.GameChatService;
import com.yourcompany.ludo.service.GameChatService.ChatMsg;
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
public class GameChatController {

    private static final Logger log = LoggerFactory.getLogger(GameChatController.class);

    private final GameChatService chat;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate ws;

    public GameChatController(GameChatService chat, JwtUtil jwtUtil, SimpMessagingTemplate ws) {
        this.chat = chat;
        this.jwtUtil = jwtUtil;
        this.ws = ws;
    }

    public record ChatRequest(String text) {}

    /** body: {"text": "..."}। কিছুই সেভ হয় না, শুধু দুজনকে পাঠানো হয় */
    @PostMapping("/chat/{sessionId}")
    public ResponseEntity<?> send(@PathVariable Long sessionId,
                                  @RequestBody ChatRequest req,
                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new SecurityException("Missing Authorization header");
            }
            String gameId = jwtUtil.getGameIdFromToken(authHeader.substring(7));

            ChatMsg m = chat.validate(sessionId, gameId, req.text());

            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "CHAT");
            payload.put("sessionId", sessionId);
            payload.put("from", m.from());
            payload.put("text", m.text());
            payload.put("id", m.id());

            ws.convertAndSend("/topic/game/" + sessionId, payload);
            return ResponseEntity.ok(payload);

        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing Authorization header"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("chat failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "সার্ভারে সমস্যা হয়েছে"));
        }
    }
}
