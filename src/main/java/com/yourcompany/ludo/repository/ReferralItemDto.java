package com.yourcompany.ludo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * একটি রেফারেলের তথ্য।
 * ইউজার ভিউতে referrer* ও referredGameId থাকে না (null, JSON এ আসে না),
 * আর referredMobile মাস্ক করা থাকে। অ্যাডমিন ভিউতে সব থাকে।
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferralItemDto(
        String referrerGameId,
        String referrerName,
        String referredGameId,
        String referredName,
        String referredMobile,
        String status,          // PENDING | COMPLETED
        BigDecimal amount,
        Instant createdAt
) {}
