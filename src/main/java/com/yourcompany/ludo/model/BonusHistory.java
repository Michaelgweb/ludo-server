package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bonus_history")
public class BonusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;  // Example: "REFERRAL", "SIGNUP", "DEPOSIT"

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "user_game_id", nullable = false, length = 12)
    private String userGameId;

    @Column(name = "source_game_id", length = 12)
    private String sourceGameId;

    @Column(name = "status", nullable = false, length = 12)
    private String status; // Example: "PENDING", "COMPLETED"

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Default constructor → status = PENDING
    public BonusHistory() {
        this.createdAt = Instant.now();
        this.status = "PENDING"; // Default pending
    }

    // Constructor with parameters, status optional
    public BonusHistory(String type, BigDecimal amount, String userGameId, String sourceGameId, String status) {
        this.type = type;
        this.amount = amount;
        this.userGameId = userGameId;
        this.sourceGameId = sourceGameId;
        this.status = status != null ? status : "PENDING"; // Default pending
        this.createdAt = Instant.now();
    }

    // ===== Getters & Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getUserGameId() { return userGameId; }
    public void setUserGameId(String userGameId) { this.userGameId = userGameId; }

    public String getSourceGameId() { return sourceGameId; }
    public void setSourceGameId(String sourceGameId) { this.sourceGameId = sourceGameId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "BonusHistory{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", amount=" + amount +
                ", userGameId='" + userGameId + '\'' +
                ", sourceGameId='" + sourceGameId + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
