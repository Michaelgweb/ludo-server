package com.yourcompany.ludo.dto;

import java.math.BigDecimal;

public class UserDto {

    // ===== ব্যবহারকারী সম্পর্কিত তথ্য =====
    private Long id;
    private String mobile;
    private String password;
    private String gameId;
    private BigDecimal balance;
    private String role;
    private String avatarUrl;
    private String displayName;
    private BigDecimal lifetimeEarnings;

    // ===== রেফারেল সিস্টেম সম্পর্কিত তথ্য =====
    private String referralCode;
    private String referredBy;

    // ===== সাইনআপ বোনাস ট্র্যাকিং =====
    private boolean signupBonusClaimed;

    // ===== Constructors =====
    public UserDto() {
        this.balance = BigDecimal.ZERO;
        this.lifetimeEarnings = BigDecimal.ZERO;
        this.signupBonusClaimed = false;
    }

    public UserDto(Long id, String mobile, String password, String gameId, BigDecimal balance, String role,
                   String avatarUrl, String displayName, BigDecimal lifetimeEarnings,
                   String referralCode, String referredBy, boolean signupBonusClaimed) {
        this.id = id;
        this.mobile = mobile;
        this.password = password;
        this.gameId = gameId;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.role = role;
        this.avatarUrl = avatarUrl;
        this.displayName = displayName;
        this.lifetimeEarnings = lifetimeEarnings != null ? lifetimeEarnings : BigDecimal.ZERO;
        this.referralCode = referralCode;
        this.referredBy = referredBy;
        this.signupBonusClaimed = signupBonusClaimed;
    }

    // ===== Getters & Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance != null ? balance : BigDecimal.ZERO; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public BigDecimal getLifetimeEarnings() { return lifetimeEarnings; }
    public void setLifetimeEarnings(BigDecimal lifetimeEarnings) {
        this.lifetimeEarnings = lifetimeEarnings != null ? lifetimeEarnings : BigDecimal.ZERO;
    }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public String getReferredBy() { return referredBy; }
    public void setReferredBy(String referredBy) { this.referredBy = referredBy; }

    public boolean isSignupBonusClaimed() { return signupBonusClaimed; }
    public void setSignupBonusClaimed(boolean signupBonusClaimed) { this.signupBonusClaimed = signupBonusClaimed; }

    // ===== Builder Pattern =====
    public static class Builder {
        private Long id;
        private String mobile;
        private String password;
        private String gameId;
        private BigDecimal balance = BigDecimal.ZERO;
        private String role;
        private String avatarUrl;
        private String displayName;
        private BigDecimal lifetimeEarnings = BigDecimal.ZERO;
        private String referralCode;
        private String referredBy;
        private boolean signupBonusClaimed = false;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder mobile(String mobile) { this.mobile = mobile; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder gameId(String gameId) { this.gameId = gameId; return this; }
        public Builder balance(BigDecimal balance) { this.balance = balance != null ? balance : BigDecimal.ZERO; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder avatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder lifetimeEarnings(BigDecimal lifetimeEarnings) { this.lifetimeEarnings = lifetimeEarnings != null ? lifetimeEarnings : BigDecimal.ZERO; return this; }
        public Builder referralCode(String referralCode) { this.referralCode = referralCode; return this; }
        public Builder referredBy(String referredBy) { this.referredBy = referredBy; return this; }
        public Builder signupBonusClaimed(boolean signupBonusClaimed) { this.signupBonusClaimed = signupBonusClaimed; return this; }

        public UserDto build() {
            return new UserDto(id, mobile, password, gameId, balance, role, avatarUrl, displayName,
                    lifetimeEarnings, referralCode, referredBy, signupBonusClaimed);
        }
    }

    // ===== toString =====
    @Override
    public String toString() {
        return "UserDto{" +
                "id=" + id +
                ", mobile='" + mobile + '\'' +
                ", gameId='" + gameId + '\'' +
                ", balance=" + balance +
                ", role='" + role + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", displayName='" + displayName + '\'' +
                ", lifetimeEarnings=" + lifetimeEarnings +
                ", referralCode='" + referralCode + '\'' +
                ", referredBy='" + referredBy + '\'' +
                ", signupBonusClaimed=" + signupBonusClaimed +
                '}';
    }
}
