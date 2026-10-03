package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * প্রতিটি কয়েন মুভমেন্টের লগ। (sessionId, userId, txType) ইউনিক,
 * তাই একই ম্যাচে একই ইউজারকে দুবার WIN/REFUND/ENTRY_FEE দেওয়া DB লেভেলেই আটকে যাবে।
 * COMMISSION এর userId = null (প্ল্যাটফর্ম)।
 */
@Entity
@Table(name = "wallet_transactions",
        indexes = {
                @Index(name = "idx_wt_user", columnList = "user_id"),
                @Index(name = "idx_wt_session", columnList = "session_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wt_session_user_type", columnNames = {"session_id", "user_id", "tx_type"}))
public class WalletTransaction {

    public enum TxType { ENTRY_FEE, REFUND, WIN, COMMISSION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private TxType txType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public WalletTransaction() {}

    public WalletTransaction(Long userId, Long sessionId, TxType txType,
                             BigDecimal amount, BigDecimal balanceAfter) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.txType = txType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSessionId() { return sessionId; }
    public TxType getTxType() { return txType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
