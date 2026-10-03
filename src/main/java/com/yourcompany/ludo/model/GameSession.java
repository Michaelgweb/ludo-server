package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal entryFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPot = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status = GameStatus.MATCH_FOUND;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // প্রথম ডাইস রোলের সময় (এই সময়েই দুজনের ফি কাটা হয়)
    @Column(name = "first_roll_at")
    private LocalDateTime firstRollAt;

    // epoch millis (UTC)
    @Column(name = "match_start_timestamp")
    private Long matchStartTimestamp;

    @Column(name = "player1_dice_count", nullable = false)
    private int player1DiceCount = 0;

    @Column(name = "player2_dice_count", nullable = false)
    private int player2DiceCount = 0;

    @Column(name = "fee_deducted", nullable = false)
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
        for (int i = 0; i < 4; i++) {
            player1Tokens.add(0);
            player2Tokens.add(0);
        }
    }

    // ---------------- Business helpers ----------------

    /** entryFee অনুযায়ী প্রাইজ (totalPot) সেট করে */
    public void setPrizeByEntryFee() {
        if (entryFee == null) return;
        switch (entryFee.intValue()) {
            case 11 -> totalPot = BigDecimal.valueOf(20);
            case 23 -> totalPot = BigDecimal.valueOf(40);
            case 45 -> totalPot = BigDecimal.valueOf(80);
            case 113 -> totalPot = BigDecimal.valueOf(200);
            case 217 -> totalPot = BigDecimal.valueOf(400);
            default -> totalPot = entryFee.multiply(BigDecimal.valueOf(1.8))
                    .setScale(2, RoundingMode.DOWN);
        }
    }

    /** প্ল্যাটফর্ম কমিশন = দুজনের ফি - প্রাইজ (২৩ হলে ৪৬-৪০ = ৬) */
    @Transient
    public BigDecimal getCommission() {
        return entryFee.multiply(BigDecimal.valueOf(2)).subtract(totalPot);
    }

    /** গেমআইডি দেখে স্লট ১ বা ২ ফেরত দেয়, খেলোয়াড় না হলে exception */
    public int slotOf(String gameId) {
        if (player1 != null && player1.getGameId().equals(gameId)) return 1;
        if (player2 != null && player2.getGameId().equals(gameId)) return 2;
        throw new IllegalArgumentException("You are not a player of this game");
    }

    public boolean isBothRolled() {
        return player1DiceCount >= 1 && player2DiceCount >= 1;
    }

    // ---------------- Getters & Setters ----------------
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

    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public LocalDateTime getFirstRollAt() { return firstRollAt; }
    public void setFirstRollAt(LocalDateTime firstRollAt) { this.firstRollAt = firstRollAt; }

    public Long getMatchStartTimestamp() { return matchStartTimestamp; }
    public void setMatchStartTimestamp(Long epochMillis) { this.matchStartTimestamp = epochMillis; }

    public int getPlayer1DiceCount() { return player1DiceCount; }
    public void setPlayer1DiceCount(int v) { this.player1DiceCount = v; }

    public int getPlayer2DiceCount() { return player2DiceCount; }
    public void setPlayer2DiceCount(int v) { this.player2DiceCount = v; }

    public boolean isFeeDeducted() { return feeDeducted; }
    public void setFeeDeducted(boolean feeDeducted) { this.feeDeducted = feeDeducted; }

    public int getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(int currentPlayer) { this.currentPlayer = currentPlayer; }

    public Integer getLastDiceValue() { return lastDiceValue; }
    public void setLastDiceValue(Integer lastDiceValue) { this.lastDiceValue = lastDiceValue; }

    public Integer getDiceOwner() { return diceOwner; }
    public void setDiceOwner(Integer diceOwner) { this.diceOwner = diceOwner; }

    public int getConsecutiveSixCount() { return consecutiveSixCount; }
    public void setConsecutiveSixCount(int v) { this.consecutiveSixCount = v; }

    public List<Integer> getPlayer1Tokens() { return player1Tokens; }
    public void setPlayer1Tokens(List<Integer> t) { this.player1Tokens = t; }

    public List<Integer> getPlayer2Tokens() { return player2Tokens; }
    public void setPlayer2Tokens(List<Integer> t) { this.player2Tokens = t; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameSession that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "GameSession{id=" + id + ", status=" + status + ", entryFee=" + entryFee
                + ", totalPot=" + totalPot + ", currentPlayer=" + currentPlayer + "}";
    }
}
