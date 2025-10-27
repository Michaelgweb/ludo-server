package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.LeaderboardDto;
import com.yourcompany.ludo.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    @GetMapping("/top")
    public List<LeaderboardDto> getTopPlayers() {
        return leaderboardService.getTopWinners(20);  // Top 20
    }
}
