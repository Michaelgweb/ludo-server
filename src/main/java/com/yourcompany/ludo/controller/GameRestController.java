package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.GameMoveRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@Tag(name = "game-controller", description = "Ludo gameplay REST API (wraps WebSocket)")
public class GameRestController {

    private final GameSocketController socketController;

    public GameRestController(GameSocketController socketController) {
        this.socketController = socketController;
    }

    @PostMapping("/move")
    public ResponseEntity<String> move(@RequestBody GameMoveRequest move) {
        socketController.handleMove(move);
        return ResponseEntity.ok("Move accepted via REST");
    }

    @PostMapping("/leave")
    public ResponseEntity<String> leave(@RequestBody GameMoveRequest move) {
        socketController.handleLeave(move);
        return ResponseEntity.ok("Player left via REST");
    }
}
