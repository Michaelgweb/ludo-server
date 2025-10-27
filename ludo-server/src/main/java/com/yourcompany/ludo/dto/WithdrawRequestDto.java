package com.yourcompany.ludo.dto;

import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.model.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class WithdrawRequestDto {
    private Long id;
    private String gameId;
    private String mobile;
    private Double amount;
    private String method;
    private String receiverNumber;
    private String transactionId;
    private String status;
    private Boolean countedInLifetime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestedAt;

    public static WithdrawRequestDto fromEntity(WithdrawRequest req) {
        WithdrawRequestDto dto = new WithdrawRequestDto();
        User user = req.getUser();

        dto.setId(req.getId());
        dto.setGameId(user != null ? user.getGameId() : "");
        dto.setMobile(user != null ? user.getMobile() : "");

        // ✅ Primitive double সরাসরি set করা হচ্ছে
        dto.setAmount(req.getAmount());

        dto.setMethod(req.getMethod() != null ? req.getMethod() : "");
        dto.setReceiverNumber(req.getReceiverNumber() != null ? req.getReceiverNumber() : "");
        dto.setTransactionId(req.getTransactionId() != null ? req.getTransactionId() : "");
        dto.setStatus(req.getStatus() != null ? req.getStatus().name() : "PENDING");
        dto.setCountedInLifetime(req.getCountedInLifetime() != null ? req.getCountedInLifetime() : false);

        dto.setRequestedAt(req.getRequestedAt());

        return dto;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getReceiverNumber() { return receiverNumber; }
    public void setReceiverNumber(String receiverNumber) { this.receiverNumber = receiverNumber; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getCountedInLifetime() { return countedInLifetime; }
    public void setCountedInLifetime(Boolean countedInLifetime) { this.countedInLifetime = countedInLifetime; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}