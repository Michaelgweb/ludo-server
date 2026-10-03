package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** মোবাইল থেকে আসা "টাকা পেয়েছি" SMS (parsed) */
@Entity
@Table(name = "payment_sms",
        uniqueConstraints = @UniqueConstraint(name = "uk_sms_method_trx", columnNames = {"method", "trx_id"}))
public class PaymentSms {

    public enum Status { WAITING, MATCHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "trx_id", nullable = false, length = 50)
    private String trxId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 20)
    private String senderNumber;

    @Column(length = 1000)
    private String rawMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.WAITING;

    private Long matchedDepositId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getTrxId() { return trxId; }
    public void setTrxId(String trxId) { this.trxId = trxId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getSenderNumber() { return senderNumber; }
    public void setSenderNumber(String v) { this.senderNumber = v; }
    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String v) { this.rawMessage = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Long getMatchedDepositId() { return matchedDepositId; }
    public void setMatchedDepositId(Long v) { this.matchedDepositId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
