package com.yourcompany.ludo.service.impl;

import com.yourcompany.ludo.dto.LeaderboardDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<LeaderboardDto> getTopWinners(int topN) {
        List<Object[]> result = gameSessionRepository.countWinsByUser();

        return result.stream()
                .map(row -> {
                    Long userId = (Long) row[0];
                    Long winCount = (Long) row[1];
                    Optional<User> userOpt = userRepository.findById(userId);
                    return userOpt.map(user ->
                            new LeaderboardDto(user.getId(), user.getMobile(), winCount) // getMobile() ব্যবহার
                    ).orElse(null);
                })
                .filter(Objects::nonNull)
                .limit(topN)
                .collect(Collectors.toList());
    }
}
