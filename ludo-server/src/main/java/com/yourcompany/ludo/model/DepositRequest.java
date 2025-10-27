
package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "deposit_requests")
public class DepositRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String method;

    private String senderNumber;

    private String transactionId;

    private Double amount;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    // ✅ EAGER লোড যাতে গেম আইডি ও মোবাইল সবসময় পাওয়া যায়
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt = LocalDateTime.now();

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    // ✅ Getter এবং Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getSenderNumber() { return senderNumber; }
    public void setSenderNumber(String senderNumber) { this.senderNumber = senderNumber; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
