package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "match_request", indexes = {
        // ম্যাচ খোঁজার কোয়েরি: একই ফি, অপেক্ষমাণ, সবচেয়ে পুরনো আগে
        @Index(name = "idx_mr_wait", columnList = "entry_fee, matched, request_time"),
        // একই ইউজারের অপেক্ষমাণ রিকোয়েস্ট মোছার জন্য
        @Index(name = "idx_mr_user", columnList = "user_id, matched")
})
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(name = "entry_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal entryFee;

    private boolean matched;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

    // Getters and Setters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public BigDecimal getEntryFee() { return entryFee; }
    public void setEntryFee(BigDecimal entryFee) { this.entryFee = entryFee; }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }

    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchRequest that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "MatchRequest{" +
                "id=" + id +
                ", user=" + (user != null ? user.getId() : null) +
                ", entryFee=" + entryFee +
                ", matched=" + matched +
                ", requestTime=" + requestTime +
                '}';
    }
}
