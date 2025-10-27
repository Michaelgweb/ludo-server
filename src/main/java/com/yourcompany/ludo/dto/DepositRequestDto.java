package com.yourcompany.ludo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DepositRequestDto {

    private Long id;
    private String mobile;
    private String gameId;
    private BigDecimal amount; // updated from Double
    private String method;
    private String transactionId;
    private String senderNumber;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestedAt;

    public static DepositRequestDto fromEntity(DepositRequest entity) {
        DepositRequestDto dto = new DepositRequestDto();

        if (entity == null) return dto;

        User user = entity.getUser();

        dto.setId(entity.getId());
        dto.setMobile(user != null && user.getMobile() != null ? user.getMobile() : "");
        dto.setGameId(user != null && user.getGameId() != null ? user.getGameId() : "");
        dto.setAmount(entity.getAmount() != null ? entity.getAmount() : BigDecimal.ZERO); // updated
        dto.setMethod(entity.getMethod() != null ? entity.getMethod() : "");
        dto.setTransactionId(entity.getTransactionId() != null ? entity.getTransactionId() : "");
        dto.setSenderNumber(entity.getSenderNumber() != null ? entity.getSenderNumber() : "");
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : "PENDING");
        dto.setRequestedAt(entity.getRequestedAt());

        return dto;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getSenderNumber() { return senderNumber; }
    public void setSenderNumber(String senderNumber) { this.senderNumber = senderNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
