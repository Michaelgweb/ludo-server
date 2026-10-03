package com.yourcompany.ludo.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** অ্যাডমিনের ডিপোজিট রিসিভ নম্বর (আনলিমিটেড, প্রতি মেথডে একাধিক) */
@Entity
@Table(name = "admin_payment_accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"method", "account_number"}))
public class AdminPaymentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "account_number", nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private boolean active = true;

    /** কতবার ইউজারকে দেখানো হয়েছে; কম যেটা সেটাই পরের বার যাবে (round-robin) */
    @Column(nullable = false)
    private long assignCount = 0;

    private LocalDateTime lastAssignedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getAssignCount() { return assignCount; }
    public void setAssignCount(long assignCount) { this.assignCount = assignCount; }
    public LocalDateTime getLastAssignedAt() { return lastAssignedAt; }
    public void setLastAssignedAt(LocalDateTime t) { this.lastAssignedAt = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
