package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.GameResultDto;
import com.yourcompany.ludo.dto.MatchRequestDto;
import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.service.GameService;
import com.yourcompany.ludo.service.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/game")
public class GameController {

    private final MatchService matchmakingService;
    private final GameService gameService;
    private final UserRepository userRepo;
    private final GameSessionRepository gameRepo;

    public GameController(MatchService matchmakingService, GameService gameService,
                          UserRepository userRepo, GameSessionRepository gameRepo) {
        this.matchmakingService = matchmakingService;
        this.gameService = gameService;
        this.userRepo = userRepo;
        this.gameRepo = gameRepo;
    }

    @PostMapping("/match")
    public ResponseEntity<?> match(@RequestBody MatchRequestDto dto,
                                   @AuthenticationPrincipal User user) {
        GameSession session = matchmakingService.tryMatch(user, dto.getEntryFee().intValue());
        return ResponseEntity.ok(session != null ? session : "Waiting for opponent");
    }

    @PostMapping("/finish")
    public ResponseEntity<?> finish(@RequestBody GameResultDto dto) {
        GameSession session = gameRepo.findById(dto.getGameId()).orElseThrow();
        User winner = userRepo.findById(dto.getWinnerId()).orElseThrow();
        gameService.finishGame(session, winner);
        return ResponseEntity.ok("Game finished, winner credited");
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<?> history(@PathVariable Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        List<GameSession> games = gameRepo.findByPlayer1OrPlayer2(user, user);
        return ResponseEntity.ok(games);
    }
}
