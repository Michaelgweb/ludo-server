package com.yourcompany.ludo.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class BonusHistoryDto {

    private String type;
    private BigDecimal amount;
    private String sourceGameId;
    private String userGameId;
    private String status; // PENDING / COMPLETED
    private Instant createdAt;

    public BonusHistoryDto() {}

    public BonusHistoryDto(String type, BigDecimal amount, String sourceGameId, String userGameId, String status, Instant createdAt) {
        this.type = type;
        this.amount = amount;
        this.sourceGameId = sourceGameId;
        this.userGameId = userGameId;
        this.status = status;
        this.createdAt = createdAt;
    }

    // ===== Getters & Setters =====
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getSourceGameId() { return sourceGameId; }
    public void setSourceGameId(String sourceGameId) { this.sourceGameId = sourceGameId; }

    public String getUserGameId() { return userGameId; }
    public void setUserGameId(String userGameId) { this.userGameId = userGameId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "BonusHistoryDto{" +
                "type='" + type + '\'' +
                ", amount=" + amount +
                ", sourceGameId='" + sourceGameId + '\'' +
                ", userGameId='" + userGameId + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
