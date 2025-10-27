package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
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

    private int entryFee;
    private int totalPot;
    private int platformFee;

    private String status; // e.g., "ONGOING", "FINISHED", "CANCELLED"

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // কাউন্টডাউন সিঙ্কের জন্য ম্যাচ শুরু হওয়ার টাইমস্ট্যাম্প (milliseconds since epoch)
    @Column(name = "match_start_timestamp")
    private Long matchStartTimestamp;

    @Column(name = "player1_dice_count")
    private int player1DiceCount = 0;

    @Column(name = "player2_dice_count")
    private int player2DiceCount = 0;

    @Column(name = "fee_deducted")
    private boolean feeDeducted = false;

    // ===== Getters and Setters =====
    public Long getId() { return id; }

    public User getPlayer1() { return player1; }
    public void setPlayer1(User player1) { this.player1 = player1; }

    public User getPlayer2() { return player2; }
    public void setPlayer2(User player2) { this.player2 = player2; }

    public User getWinner() { return winner; }
    public void setWinner(User winner) { this.winner = winner; }

    public int getEntryFee() { return entryFee; }
    public void setEntryFee(int entryFee) { this.entryFee = entryFee; }

    public int getTotalPot() { return totalPot; }
    public void setTotalPot(int totalPot) { this.totalPot = totalPot; }

    public int getPlatformFee() { return platformFee; }
    public void setPlatformFee(int platformFee) { this.platformFee = platformFee; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getMatchStartTimestamp() { return matchStartTimestamp; }
    public void setMatchStartTimestamp(Long matchStartTimestamp) { this.matchStartTimestamp = matchStartTimestamp; }

    public int getPlayer1DiceCount() { return player1DiceCount; }
    public void setPlayer1DiceCount(int player1DiceCount) { this.player1DiceCount = player1DiceCount; }

    public int getPlayer2DiceCount() { return player2DiceCount; }
    public void setPlayer2DiceCount(int player2DiceCount) { this.player2DiceCount = player2DiceCount; }

    public boolean isFeeDeducted() { return feeDeducted; }
    public void setFeeDeducted(boolean feeDeducted) { this.feeDeducted = feeDeducted; }

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
                ", player1=" + (player1 != null ? player1.getId() : null) +
                ", player2=" + (player2 != null ? player2.getId() : null) +
                ", winner=" + (winner != null ? winner.getId() : null) +
                ", entryFee=" + entryFee +
                ", totalPot=" + totalPot +
                ", platformFee=" + platformFee +
                ", status='" + status + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", matchStartTimestamp=" + matchStartTimestamp +
                ", player1DiceCount=" + player1DiceCount +
                ", player2DiceCount=" + player2DiceCount +
                ", feeDeducted=" + feeDeducted +
                '}';
    }
}
