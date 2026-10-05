package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "game_session", indexes = {
        @Index(name = "idx_gs_status_start", columnList = "status, match_start_timestamp"),
        @Index(name = "idx_gs_status_fee_start", columnList = "status, fee_deducted, startTime"),
        @Index(name = "idx_gs_p1_status", columnList = "player1_id, status"),
        @Index(name = "idx_gs_p2_status", columnList = "player2_id, status")
})
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // গেম শুরু ও টাকা লেনদেনে সরাসরি লাগে, তাই EAGER
    @ManyToOne(fetch = FetchType.EAGER)
    private User player1;

    @ManyToOne(fetch = FetchType.EAGER)
    private User player2;

    // শুধু গেম শেষে সেট হয়
    @ManyToOne(fetch = FetchType.LAZY)
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

    // প্রথম রোলের সময় (এই সময়েই দুজনের ফি কাটা হয়)
    @Column(name = "first_roll_at")
    private LocalDateTime firstRollAt;

    // epoch millis (UTC), কাউন্টডাউন শেষের সময়
    @Column(name = "match_start_timestamp")
    private Long matchStartTimestamp;

    @Column(name = "fee_deducted", nullable = false)
    private boolean feeDeducted = false;

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

    /** প্ল্যাটফর্ম কমিশন = দুজনের ফি - প্রাইজ */
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

    public boolean isFeeDeducted() { return feeDeducted; }
    public void setFeeDeducted(boolean feeDeducted) { this.feeDeducted = feeDeducted; }

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
                + ", totalPot=" + totalPot + "}";
    }
}
