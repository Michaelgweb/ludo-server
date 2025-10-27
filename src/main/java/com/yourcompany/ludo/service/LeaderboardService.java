package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.LeaderboardDto;
import java.util.List;

public interface LeaderboardService {
    List<LeaderboardDto> getTopWinners(int topN);
}
