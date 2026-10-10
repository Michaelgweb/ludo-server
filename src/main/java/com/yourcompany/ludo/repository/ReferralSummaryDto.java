package com.yourcompany.ludo.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReferralSummaryDto(
        long totalReferred,
        long pendingCount,
        long completedCount,
        BigDecimal pendingAmount,
        BigDecimal completedAmount,
        List<ReferralItemDto> items
) {}
