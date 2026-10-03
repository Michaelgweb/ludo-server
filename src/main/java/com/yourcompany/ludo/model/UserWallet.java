package com.yourcompany.ludo.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_wallets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "method", "account_number"}))
public class UserWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "account_number", nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
