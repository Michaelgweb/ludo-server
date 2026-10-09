package com.yourcompany.ludo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    public enum Role { USER, STAFF, ADMIN }
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

    // ---------------- একটাই ব্যালেন্স (ডিপোজিট + জেতা টাকা) ----------------
    @Column(name = "balance", nullable = false, precision = 19, scale = 2,
            columnDefinition = "numeric(19,2) default 0")
    private BigDecimal balance = BigDecimal.ZERO;

    // ---------------- টার্নওভার ব্যালেন্স ----------------
    // ডিপোজিটে বাড়ে, গেম খেললে ১০০% কমে। ০ হলে উত্তোলন করা যাবে।
    @Column(name = "turnover_balance", nullable = false, precision = 19, scale = 2,
            columnDefinition = "numeric(19,2) default 0")
    private BigDecimal turnoverBalance = BigDecimal.ZERO;

    // ---------------- লাইভ টাইম আয় (শুধু দেখানোর জন্য) ----------------
    @Column(name = "lifetime_earnings", nullable = false, precision = 19, scale = 2)
    private BigDecimal lifetimeEarnings = BigDecimal.ZERO;

    // ---------------- লাইভ টাইম উত্তোলন (শুধু দেখানোর জন্য) ----------------
    // উত্তোলন সফল (অ্যাডমিন অ্যাপ্রুভ) হলে বাড়ে। ব্যালেন্সে কোনো প্রভাব নেই।
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

    // ---------------- লগইন, ডিভাইস ও লোকেশন তথ্য ----------------
    @Column(name = "login_count")
    private Integer loginCount = 0;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    private String deviceId;
    private String deviceName;
    private String deviceModel;
    private String osName;
    private String osVersion;
    private String browser;

    @Column(length = 512)
    private String userAgent;

    private String ipAddress;
    private String lastLoginIp;
    private String country;
    private String city;
    private Double latitude;
    private Double longitude;

    @Column(columnDefinition = "boolean default false")
    private Boolean blocked = false;

    @Column(columnDefinition = "boolean default false")
    private Boolean phoneVerified = false;

    @Column(name = "total_devices")
    private Integer totalDevices = 0;

    public User() {
        this.referralCode = generateReferralCode();
    }

    // ================= Wallet operations =================

    /** ডিপোজিট (অ্যাপ্রুভের পর): ব্যালেন্সও বাড়ে, টার্নওভারও সমান বাড়ে */
    public void addDeposit(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.balance = getBalance().add(amount);
            this.turnoverBalance = getTurnoverBalance().add(amount);
        }
    }

    /** জেতা টাকা: শুধু ব্যালেন্স বাড়ে, টার্নওভার বাড়ে না */
    public void addWinnings(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.balance = getBalance().add(amount);
        }
    }

    /** গেমের এন্ট্রি ফি: ব্যালেন্স থেকে কাটে, টার্নওভার ১০০% কমে (০ এর নিচে যাবে না) */
    public void deduct(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        if (getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.balance = getBalance().subtract(amount);
        this.turnoverBalance = getTurnoverBalance().subtract(amount).max(BigDecimal.ZERO);
    }

    /** উত্তোলনের শর্ত: টার্নওভার ০ হতে হবে */
    public boolean canWithdraw() {
        return getTurnoverBalance().signum() <= 0;
    }

    /** উত্তোলনের আগে চেক */
    public void assertCanWithdraw(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        if (!canWithdraw()) {
            throw new IllegalStateException(
                    "Complete your turnover first. Remaining turnover: " + getTurnoverBalance());
        }
        if (getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
    }

    /** ১. উত্তোলন রিকোয়েস্ট দেওয়ার সময়: শুধু ব্যালেন্স কাটে (লাইফটাইম বাড়ে না) */
    public void holdForWithdraw(BigDecimal amount) {
        assertCanWithdraw(amount);
        this.balance = getBalance().subtract(amount);
    }

    /** ২. উত্তোলন সফল হলে (অ্যাডমিন অ্যাপ্রুভ): লাইভ টাইম উত্তোলন বাড়ে, ইউজারকে শুধু দেখানোর জন্য */
    public void markWithdrawSuccess(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.lifetimeWithdraw = getLifetimeWithdraw().add(amount);
        }
    }

    /** ৩. উত্তোলন বাতিল/রিজেক্ট হলে: টাকা ব্যালেন্সে ফেরত (টার্নওভার বদলায় না) */
    public void refundWithdraw(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.balance = getBalance().add(amount);
        }
    }

    /** জেতা টাকা যোগ হলে লাইফটাইম আয়ও বাড়ে (শুধু দেখানোর জন্য) */
    public void addLifetimeEarnings(BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            this.lifetimeEarnings = getLifetimeEarnings().add(amount);
        }
    }

    // ---- পুরনো কোড যেন না ভাঙে (নতুন লজিকে ঘোরানো) ----

    /** @deprecated addDeposit() ব্যবহার করুন। ডিপোজিট/বোনাস/রিফান্ড: ব্যালেন্স + টার্নওভার দুটোই বাড়ে */
    @Deprecated
    public void addToDepositBalance(BigDecimal amount) { addDeposit(amount); }

    /** @deprecated addWinnings() ব্যবহার করুন। জেতা টাকা: শুধু ব্যালেন্স বাড়ে */
    @Deprecated
    public void addToWithdrawBalance(BigDecimal amount) { addWinnings(amount); }

    /** @deprecated এখন একটাই ব্যালেন্স, getBalance() ব্যবহার করুন */
    @Deprecated
    @JsonIgnore
    public BigDecimal getDepositBalance() { return getBalance(); }

    /** @deprecated getWithdrawableBalance() ব্যবহার করুন */
    @Deprecated
    @JsonIgnore
    public BigDecimal getWithdrawBalance() { return getWithdrawableBalance(); }

    /** এই মুহূর্তে উত্তোলনযোগ্য টাকা (টার্নওভার বাকি থাকলে ০) */
    @Transient
    @JsonIgnore
    public BigDecimal getWithdrawableBalance() {
        return canWithdraw() ? getBalance() : BigDecimal.ZERO;
    }

    // ---------------- Device helper ----------------
    public void increaseDeviceCount() {
        this.totalDevices = getTotalDevices() + 1;
    }

    // ---------------- Getters & Setters ----------------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    @Override
    @JsonIgnore
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public BigDecimal getBalance() { return balance != null ? balance : BigDecimal.ZERO; }
    public void setBalance(BigDecimal v) { this.balance = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getTurnoverBalance() { return turnoverBalance != null ? turnoverBalance : BigDecimal.ZERO; }
    public void setTurnoverBalance(BigDecimal v) { this.turnoverBalance = v != null ? v : BigDecimal.ZERO; }

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

    public int getLoginCount() { return loginCount != null ? loginCount : 0; }
    public void setLoginCount(int v) { this.loginCount = v; }

    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime t) { this.lastLoginTime = t; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }

    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public boolean isBlocked() { return Boolean.TRUE.equals(blocked); }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public Boolean getPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public Integer getTotalDevices() { return totalDevices != null ? totalDevices : 0; }
    public void setTotalDevices(Integer totalDevices) { this.totalDevices = totalDevices; }

    // ---------------- UserDetails ----------------
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
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
        return "User{id=" + id + ", gameId='" + gameId + "', balance=" + balance
                + ", turnoverBalance=" + turnoverBalance
                + ", lifetimeWithdraw=" + lifetimeWithdraw
                + ", role=" + role + ", status=" + status + "}";
    }
}
