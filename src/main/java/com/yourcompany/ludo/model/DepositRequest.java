package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deposit_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dep_txn", columnNames = {"transaction_id"}),
                @UniqueConstraint(name = "uk_dep_method_usertxn", columnNames = {"method", "user_transaction_id"})
        },
        indexes = @Index(name = "idx_dep_status", columnList = "status"))
public class DepositRequest {

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String method;

    /** রোটেশনে ইউজারকে যে অ্যাডমিন নম্বর দেওয়া হয়েছে */
    @Column(name = "payment_account_number", nullable = false, length = 20)
    private String paymentAccountNumber;

    private Long adminAccountId;

    /** সার্ভারের নিজস্ব ID (DEP0000000) */
    @Column(name = "transaction_id", nullable = false, length = 20)
    private String transactionId;

    /** ইউজারের দেওয়া bKash/Nagad/Rocket TrxID */
    @Column(name = "user_transaction_id", length = 50)
    private String userTransactionId;

    @Column(length = 20)
    private String senderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private boolean autoApproved = false;

    @Column(length = 500)
    private String note;

    private Long processedById;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime submittedAt;
    private LocalDateTime processedAt;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPaymentAccountNumber() { return paymentAccountNumber; }
    public void setPaymentAccountNumber(String v) { this.paymentAccountNumber = v; }
    public Long getAdminAccountId() { return adminAccountId; }
    public void setAdminAccountId(Long v) { this.adminAccountId = v; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { this.transactionId = v; }
    public String getUserTransactionId() { return userTransactionId; }
    public void setUserTransactionId(String v) { this.userTransactionId = v; }
    public String getSenderNumber() { return senderNumber; }
    public void setSenderNumber(String v) { this.senderNumber = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isAutoApproved() { return autoApproved; }
    public void setAutoApproved(boolean v) { this.autoApproved = v; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getProcessedById() { return processedById; }
    public void setProcessedById(Long v) { this.processedById = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime v) { this.submittedAt = v; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime v) { this.processedAt = v; }
}
