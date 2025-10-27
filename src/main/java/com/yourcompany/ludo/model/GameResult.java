package com.yourcompany.ludo.model;

import java.math.BigDecimal;

public class GameResult {
    private GameSession gameSession;
    private User winner;
    private User loser;
    private BigDecimal prize;

    public GameSession getGameSession() {
        return gameSession;
    }
    public void setGameSession(GameSession gameSession) {
        this.gameSession = gameSession;
    }

    public User getWinner() {
        return winner;
    }
    public void setWinner(User winner) {
        this.winner = winner;
    }

    public User getLoser() {
        return loser;
    }
    public void setLoser(User loser) {
        this.loser = loser;
    }

    public BigDecimal getPrize() {
        return prize;
    }
    public void setPrize(BigDecimal prize) {
        this.prize = prize;
    }
}
