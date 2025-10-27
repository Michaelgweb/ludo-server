package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    private User player1;

    @ManyToOne(fetch = FetchType.EAGER)
    private User player2;

    @ManyToOne(fetch = FetchType.EAGER)
    private User winner;

    private BigDecimal entryFee = BigDecimal.ZERO;

    private BigDecimal totalPot = BigDecimal.ZERO;

    private String status; // MATCH_FOUND, ONGOING, FINISHED, CANCELLED
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Stored as epoch millis
    @Column(name = "match_start_timestamp")
    private Long matchStartTimestamp;

    @Column(name = "player1_dice_count", nullable = false)
    private int player1DiceCount = 0;

    @Column(name = "player2_dice_count", nullable = false)
    private int player2DiceCount = 0;

    @Column(name = "fee_deducted")
    private boolean feeDeducted = false;

    @Column(name = "current_player", nullable = false)
    private int currentPlayer = 1;

    @Column(name = "last_dice_value")
    private Integer lastDiceValue = 0;

    @Column(name = "dice_owner")
    private Integer diceOwner;

    @Column(name = "consecutive_six_count", nullable = false)
    private int consecutiveSixCount = 0;

    @ElementCollection
    @CollectionTable(name = "player1_tokens", joinColumns = @JoinColumn(name = "game_session_id"))
    @Column(name = "token_position")
    private List<Integer> player1Tokens = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "player2_tokens", joinColumns = @JoinColumn(name = "game_session_id"))
    @Column(name = "token_position")
    private List<Integer> player2Tokens = new ArrayList<>();

    public GameSession() {
        this.currentPlayer = 1;
        this.lastDiceValue = 0;
        this.consecutiveSixCount = 0;
        this.player1DiceCount = 0;
        this.player2DiceCount = 0;
        this.entryFee = BigDecimal.ZERO;
        this.totalPot = BigDecimal.ZERO;
        this.player1Tokens = new ArrayList<>();
        this.player2Tokens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            this.player1Tokens.add(0);
            this.player2Tokens.add(0);
        }
    }

    // EntryFee অনুযায়ী prize সেট করা
    public void setPrizeByEntryFee() {
        if (this.entryFee == null) return;

        switch (this.entryFee.intValue()) {
            case 11 -> this.totalPot = BigDecimal.valueOf(20);
            case 23 -> this.totalPot = BigDecimal.valueOf(40);
            case 45 -> this.totalPot = BigDecimal.valueOf(80);
            case 113 -> this.totalPot = BigDecimal.valueOf(200);
            case 217 -> this.totalPot = BigDecimal.valueOf(400);
            default -> this.totalPot = this.entryFee.multiply(BigDecimal.valueOf(1.8));
        }
    }

    // Getters & Setters
    public Long getId() { return id; }
    public User getPlayer1() { return player1; }
    public void setPlayer1(User player1) { this.player1 = player1; }
    public User getPlayer2() { return player2; }
    public void setPlayer2(User player2) { this.player2 = player2; }
    public User getWinner() { return winner; }
    public void setWinner(User winner) { this.winner = winner; }
    public BigDecimal getEntryFee() { return entryFee; }
    public void setEntryFee(BigDecimal entryFee) { this.entryFee = entryFee; }
    public BigDecimal getTotalPot() { return totalPot; }
    public void setTotalPot(BigDecimal totalPot) { this.totalPot = totalPot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    // epoch millis conversion
    public Long getMatchStartTimestamp() { return matchStartTimestamp; }

    // LocalDateTime থেকে set করলে auto convert হবে
    public void setMatchStartTimestamp(LocalDateTime matchStartTime) {
        if (matchStartTime != null) {
            this.matchStartTimestamp = matchStartTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        } else {
            this.matchStartTimestamp = null;
        }
    }

    // Convenience setter - সরাসরি epoch millis assign করা যাবে
    public void setMatchStartTimestamp(long epochMillis) {
        this.matchStartTimestamp = epochMillis;
    }

    public int getPlayer1DiceCount() { return player1DiceCount; }
    public void setPlayer1DiceCount(int player1DiceCount) { this.player1DiceCount = player1DiceCount; }
    public int getPlayer2DiceCount() { return player2DiceCount; }
    public void setPlayer2DiceCount(int player2DiceCount) { this.player2DiceCount = player2DiceCount; }
    public boolean isFeeDeducted() { return feeDeducted; }
    public void setFeeDeducted(boolean feeDeducted) { this.feeDeducted = feeDeducted; }
    public int getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(int currentPlayer) { this.currentPlayer = currentPlayer; }
    public Integer getLastDiceValue() { return lastDiceValue; }
    public void setLastDiceValue(Integer lastDiceValue) { this.lastDiceValue = lastDiceValue; }
    public Integer getDiceOwner() { return diceOwner; }
    public void setDiceOwner(Integer diceOwner) { this.diceOwner = diceOwner; }
    public int getConsecutiveSixCount() { return consecutiveSixCount; }
    public void setConsecutiveSixCount(int consecutiveSixCount) { this.consecutiveSixCount = consecutiveSixCount; }
    public List<Integer> getPlayer1Tokens() { return player1Tokens; }
    public void setPlayer1Tokens(List<Integer> player1Tokens) { this.player1Tokens = player1Tokens; }
    public List<Integer> getPlayer2Tokens() { return player2Tokens; }
    public void setPlayer2Tokens(List<Integer> player2Tokens) { this.player2Tokens = player2Tokens; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameSession)) return false;
        GameSession that = (GameSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "GameSession{" +
                "id=" + id +
                ", currentPlayer=" + currentPlayer +
                ", lastDiceValue=" + lastDiceValue +
                ", diceOwner=" + diceOwner +
                ", player1Tokens=" + player1Tokens +
                ", player2Tokens=" + player2Tokens +
                ", entryFee=" + entryFee +
                ", totalPot=" + totalPot +
                ", matchStartTimestamp=" + matchStartTimestamp +
                '}';
    }
}
