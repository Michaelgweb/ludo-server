package com.yourcompany.ludo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "withdraw_requests")
public class WithdrawRequest {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private String method;

    @Column(name = "receiver_number", nullable = false)
    private String receiverNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @Column(name = "requested_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "counted_in_lifetime", nullable = false)
    private Boolean countedInLifetime = false;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getReceiverNumber() { return receiverNumber; }
    public void setReceiverNumber(String receiverNumber) { this.receiverNumber = receiverNumber; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public Boolean getCountedInLifetime() { return countedInLifetime; }
    public void setCountedInLifetime(Boolean countedInLifetime) { this.countedInLifetime = countedInLifetime; }
}
