package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.GameResultDto;
import com.yourcompany.ludo.dto.MatchRequestDto;
import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.GameResult;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.service.GameService;
import com.yourcompany.ludo.service.MatchService;
import com.yourcompany.ludo.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/game")
public class GameController {

    private final MatchService matchmakingService;
    private final GameService gameService;
    private final UserRepository userRepo;
    private final GameSessionRepository gameRepo;

    @Autowired
    private NotificationService notificationService;

    public GameController(MatchService matchmakingService, GameService gameService,
                          UserRepository userRepo, GameSessionRepository gameRepo) {
        this.matchmakingService = matchmakingService;
        this.gameService = gameService;
        this.userRepo = userRepo;
        this.gameRepo = gameRepo;
    }

    // Matchmaking
    @PostMapping("/match")
    public ResponseEntity<?> match(@RequestBody MatchRequestDto dto,
                                   @AuthenticationPrincipal User user) {
        BigDecimal entryFee = BigDecimal.valueOf(dto.getEntryFee());
        GameSession session = matchmakingService.tryMatch(user, entryFee);
        return ResponseEntity.ok(session != null ? session : "Waiting for opponent");
    }

    // Finish game by winner
    @PostMapping("/finish")
    public ResponseEntity<?> finish(@RequestBody GameResultDto dto) {
        GameSession session = gameRepo.findById(dto.getGameId()).orElseThrow();
        User winner = userRepo.findById(dto.getWinnerId()).orElseThrow();
        gameService.finishGame(session, winner);
        return ResponseEntity.ok("Game finished, winner credited");
    }

    // Game history for user
    @GetMapping("/history/{userId}")
    public ResponseEntity<?> history(@PathVariable Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        List<GameSession> games = gameRepo.findByPlayer1OrPlayer2(user, user);
        return ResponseEntity.ok(games);
    }

    // Finish game with full result (winner + loser + prize)
    @PostMapping("/game/finish")
    public ResponseEntity<?> finishGame(@RequestBody GameResult result) {
        gameService.finish(result);

        User winner = result.getWinner();
        if (winner != null) {
            notificationService.sendNotification(
                    winner.getId(),
                    "Game Won",
                    "You won the game and earned " + result.getPrize()
            );
        }

        User loser = result.getLoser();
        if (loser != null) {
            notificationService.sendNotification(
                    loser.getId(),
                    "Game Lost",
                    "You lost the game. Better luck next time!"
            );
        }

        return ResponseEntity.ok(result);
    }

}
