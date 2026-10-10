package com.yourcompany.ludo.dto;

import com.yourcompany.ludo.model.DepositRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DepositRequestDto(
        Long id,
        String gameId,
        String mobile,
        BigDecimal amount,
        String method,
        String paymentAccountNumber,
        String transactionId,
        String userTransactionId,
        String status,
        boolean autoApproved,
        String note,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        LocalDateTime processedAt) {

    public static DepositRequestDto fromEntity(DepositRequest d) {
        return new DepositRequestDto(
                d.getId(),
                d.getUser() != null ? d.getUser().getGameId() : null,
                d.getUser() != null ? d.getUser().getMobile() : null,
                d.getAmount(), d.getMethod(), d.getPaymentAccountNumber(),
                d.getTransactionId(), d.getUserTransactionId(),
                d.getStatus().name(), d.isAutoApproved(), d.getNote(),
                d.getCreatedAt(), d.getSubmittedAt(), d.getProcessedAt());
    }
}
