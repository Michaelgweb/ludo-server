package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    private int entryFee;

    private boolean matched;

    private LocalDateTime requestTime;

    // Getters and setters

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public int getEntryFee() {
        return entryFee;
    }

    public void setEntryFee(int entryFee) {
        this.entryFee = entryFee;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchRequest)) return false;
        MatchRequest that = (MatchRequest) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

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

