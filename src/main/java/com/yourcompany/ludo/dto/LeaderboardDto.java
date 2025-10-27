package com.yourcompany.ludo.dto;

public class LeaderboardDto {
    private Long userId;
    private String email;
    private long winCount;

    public LeaderboardDto(Long userId, String email, long winCount) {
        this.userId = userId;
        this.email = email;
        this.winCount = winCount;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public long getWinCount() {
        return winCount;
    }
}
