package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User implements UserDetails {

    public enum Role { USER, ADMIN }
    public enum Status { ONLINE, OFFLINE }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String mobile;

    @Column(nullable = false, unique = true, length = 20)
    private String gameId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal depositBalance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal withdrawBalance = BigDecimal.ZERO;

    @Column(name = "lifetime_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal lifetimeEarnings = BigDecimal.ZERO;

    @Column(name = "lifetime_withdraw", nullable = false, precision = 19, scale = 2)
    private BigDecimal lifetimeWithdraw = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column
    private String displayName;

    @Column
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OFFLINE;

    @Column
    private LocalDateTime lastActive;

    @Column(unique = true, length = 6, nullable = false)
    private String referralCode;

    @Column(length = 20)
    private String referredBy;

    @Column(nullable = false)
    private boolean firstDepositBonusGiven = false;

    @Column(nullable = false)
    private boolean signupBonusClaimed = false;

    @Column(nullable = false)
    private boolean referralBonusClaimed = false;

    @Column(nullable = false)
    private boolean referrerBonusGiven = false;

    public User() {
        this.referralCode = generateReferralCode();
    }

    // ---------------- Wallet operations ----------------

    /** মোট ব্যালেন্স = deposit + withdraw */
    @Transient
    public BigDecimal getBalance() {
        return getDepositBalance().add(getWithdrawBalance());
    }

    /** এন্ট্রি ফি কাটা: আগে deposit থেকে, বাকিটা withdraw থেকে */
    public void deduct(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        if (getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        BigDecimal fromDeposit = getDepositBalance().min(amount);
        this.depositBalance = getDepositBalance().subtract(fromDeposit);
        this.withdrawBalance = getWithdrawBalance().subtract(amount.subtract(fromDeposit));
    }

    /** রিফান্ড / ডিপোজিট যোগ */
    public void addToDepositBalance(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.depositBalance = getDepositBalance().add(amount);
        }
    }

    /** জেতা টাকা যোগ (উইথড্র করা যাবে) */
    public void addToWithdrawBalance(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.withdrawBalance = getWithdrawBalance().add(amount);
        }
    }

    public void addLifetimeEarnings(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.lifetimeEarnings = getLifetimeEarnings().add(amount);
        }
    }

    // ---------------- Getters & Setters ----------------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    @Override
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public BigDecimal getDepositBalance() { return depositBalance != null ? depositBalance : BigDecimal.ZERO; }
    public void setDepositBalance(BigDecimal v) { this.depositBalance = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getWithdrawBalance() { return withdrawBalance != null ? withdrawBalance : BigDecimal.ZERO; }
    public void setWithdrawBalance(BigDecimal v) { this.withdrawBalance = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getLifetimeEarnings() { return lifetimeEarnings != null ? lifetimeEarnings : BigDecimal.ZERO; }
    public void setLifetimeEarnings(BigDecimal v) { this.lifetimeEarnings = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getLifetimeWithdraw() { return lifetimeWithdraw != null ? lifetimeWithdraw : BigDecimal.ZERO; }
    public void setLifetimeWithdraw(BigDecimal v) { this.lifetimeWithdraw = v != null ? v : BigDecimal.ZERO; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getLastActive() { return lastActive; }
    public void setLastActive(LocalDateTime lastActive) { this.lastActive = lastActive; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String code) { this.referralCode = code != null ? code : generateReferralCode(); }

    public String getReferredBy() { return referredBy; }
    public void setReferredBy(String referredBy) { this.referredBy = referredBy; }

    public boolean isFirstDepositBonusGiven() { return firstDepositBonusGiven; }
    public void setFirstDepositBonusGiven(boolean v) { this.firstDepositBonusGiven = v; }

    public boolean isSignupBonusClaimed() { return signupBonusClaimed; }
    public void setSignupBonusClaimed(boolean v) { this.signupBonusClaimed = v; }

    public boolean isReferralBonusClaimed() { return referralBonusClaimed; }
    public void setReferralBonusClaimed(boolean v) { this.referralBonusClaimed = v; }

    public boolean isReferrerBonusGiven() { return referrerBonusGiven; }
    public void setReferrerBonusGiven(boolean v) { this.referrerBonusGiven = v; }

    // ---------------- UserDetails ----------------
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(this.role.name()));
    }

    @Override
    public String getUsername() { return gameId; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

    // ---------------- Utility ----------------
    private static String generateReferralCode() {
        return String.format("%06d", RANDOM.nextInt(900000) + 100000);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "User{id=" + id + ", gameId='" + gameId + "', depositBalance=" + depositBalance
                + ", withdrawBalance=" + withdrawBalance + ", role=" + role + ", status=" + status + "}";
    }
}
