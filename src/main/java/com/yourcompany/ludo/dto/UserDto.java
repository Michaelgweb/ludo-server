package com.yourcompany.ludo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserDto {

    // ================= USER INFO =================
    private Long id;
    private String mobile;

    // রিকোয়েস্ট থেকে নেওয়া যায় (signup), কিন্তু রেসপন্সে কখনো যায় না
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String gameId;

    private BigDecimal balance;
    private BigDecimal depositBalance;
    private BigDecimal withdrawBalance;

    private String role;
    private String avatarUrl;
    private String displayName;
    private BigDecimal lifetimeEarnings;

    // ================= REFERRAL =================
    private String referralCode;
    private String referredBy;

    // ================= BONUS =================
    private boolean signupBonusClaimed;

    // ================= DEVICE =================
    private String deviceId;
    private String deviceFingerprint;
    private String deviceHash;
    private String deviceName;
    private String deviceModel;

    // ================= OS / BROWSER =================
    private String osName;
    private String osVersion;
    private String browser;
    private String userAgent;

    // ================= NETWORK =================
    private String ipAddress;
    private String lastLoginIp;
    private String isp;
    private String networkType;

    // ================= LOCATION =================
    private String country;
    private String city;
    private Double latitude;
    private Double longitude;

    // ================= HARDWARE =================
    private String cpu;
    private String ram;
    private String screenResolution;
    private String timezone;
    private String language;

    // ================= SECURITY =================
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean multiAccountDetected;
    private Integer riskScore;
    private String riskLevel;

    // ================= LOGIN SECURITY =================
    private LocalDateTime lastLoginTime;
    private Integer loginCount;
    private Integer failedLoginAttempt;
    private LocalDateTime lastFailedLogin;
    private String lastLoginDeviceId;
    private String lastLoginUserAgent;

    // ================= ACCOUNT STATUS =================
    private Boolean blocked;
    private Boolean phoneVerified;
    private Integer totalDevices;
    private Integer maxAllowedDevices = 3;

    // ==================================================
    // DEFAULT CONSTRUCTOR
    // ==================================================
    public UserDto() {
        this.balance = BigDecimal.ZERO;
        this.depositBalance = BigDecimal.ZERO;
        this.withdrawBalance = BigDecimal.ZERO;
        this.lifetimeEarnings = BigDecimal.ZERO;
        this.signupBonusClaimed = false;
        this.loginCount = 0;
        this.failedLoginAttempt = 0;
        this.blocked = false;
        this.phoneVerified = false;
        this.totalDevices = 0;
        this.riskScore = 0;
        this.riskLevel = "LOW";
        this.vpnDetected = false;
        this.proxyDetected = false;
        this.multiAccountDetected = false;
    }

    // ==================================================
    // FULL CONSTRUCTOR
    // ==================================================
    public UserDto(
            Long id, String mobile, String password, String gameId,
            BigDecimal balance, BigDecimal depositBalance, BigDecimal withdrawBalance,
            String role, String avatarUrl, String displayName, BigDecimal lifetimeEarnings,
            String referralCode, String referredBy, boolean signupBonusClaimed,
            String deviceId, String deviceFingerprint, String deviceHash,
            String deviceName, String deviceModel,
            String osName, String osVersion, String browser, String userAgent,
            String ipAddress, String lastLoginIp, String isp, String networkType,
            String country, String city, Double latitude, Double longitude,
            String cpu, String ram, String screenResolution, String timezone, String language,
            Boolean vpnDetected, Boolean proxyDetected, Boolean multiAccountDetected,
            Integer riskScore, String riskLevel,
            LocalDateTime lastLoginTime, Integer loginCount, Integer failedLoginAttempt,
            LocalDateTime lastFailedLogin, String lastLoginDeviceId, String lastLoginUserAgent,
            Boolean blocked, Boolean phoneVerified, Integer totalDevices, Integer maxAllowedDevices
    ) {
        this.id = id;
        this.mobile = mobile;
        this.password = password;
        this.gameId = gameId;

        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.depositBalance = depositBalance != null ? depositBalance : BigDecimal.ZERO;
        this.withdrawBalance = withdrawBalance != null ? withdrawBalance : BigDecimal.ZERO;

        this.role = role;
        this.avatarUrl = avatarUrl;
        this.displayName = displayName;
        this.lifetimeEarnings = lifetimeEarnings != null ? lifetimeEarnings : BigDecimal.ZERO;

        this.referralCode = referralCode;
        this.referredBy = referredBy;
        this.signupBonusClaimed = signupBonusClaimed;

        this.deviceId = deviceId;
        this.deviceFingerprint = deviceFingerprint;
        this.deviceHash = deviceHash;
        this.deviceName = deviceName;
        this.deviceModel = deviceModel;

        this.osName = osName;
        this.osVersion = osVersion;
        this.browser = browser;
        this.userAgent = userAgent;

        this.ipAddress = ipAddress;
        this.lastLoginIp = lastLoginIp;
        this.isp = isp;
        this.networkType = networkType;

        this.country = country;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;

        this.cpu = cpu;
        this.ram = ram;
        this.screenResolution = screenResolution;
        this.timezone = timezone;
        this.language = language;

        this.vpnDetected = vpnDetected != null ? vpnDetected : false;
        this.proxyDetected = proxyDetected != null ? proxyDetected : false;
        this.multiAccountDetected = multiAccountDetected != null ? multiAccountDetected : false;
        this.riskScore = riskScore != null ? riskScore : 0;
        this.riskLevel = riskLevel != null ? riskLevel : "LOW";

        this.lastLoginTime = lastLoginTime;
        this.loginCount = loginCount != null ? loginCount : 0;
        this.failedLoginAttempt = failedLoginAttempt != null ? failedLoginAttempt : 0;
        this.lastFailedLogin = lastFailedLogin;
        this.lastLoginDeviceId = lastLoginDeviceId;
        this.lastLoginUserAgent = lastLoginUserAgent;

        this.blocked = blocked != null ? blocked : false;
        this.phoneVerified = phoneVerified != null ? phoneVerified : false;
        this.totalDevices = totalDevices != null ? totalDevices : 0;
        this.maxAllowedDevices = maxAllowedDevices != null ? maxAllowedDevices : 3;
    }

    // ==================================================
    // GETTERS & SETTERS
    // ==================================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public BigDecimal getBalance() { return balance != null ? balance : BigDecimal.ZERO; }
    public void setBalance(BigDecimal balance) { this.balance = balance != null ? balance : BigDecimal.ZERO; }

    public BigDecimal getDepositBalance() { return depositBalance != null ? depositBalance : BigDecimal.ZERO; }
    public void setDepositBalance(BigDecimal v) { this.depositBalance = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getWithdrawBalance() { return withdrawBalance != null ? withdrawBalance : BigDecimal.ZERO; }
    public void setWithdrawBalance(BigDecimal v) { this.withdrawBalance = v != null ? v : BigDecimal.ZERO; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public BigDecimal getLifetimeEarnings() { return lifetimeEarnings; }
    public void setLifetimeEarnings(BigDecimal v) { this.lifetimeEarnings = v != null ? v : BigDecimal.ZERO; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public String getReferredBy() { return referredBy; }
    public void setReferredBy(String referredBy) { this.referredBy = referredBy; }

    public boolean isSignupBonusClaimed() { return signupBonusClaimed; }
    public void setSignupBonusClaimed(boolean v) { this.signupBonusClaimed = v; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String v) { this.deviceId = v; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String v) { this.deviceFingerprint = v; }

    public String getDeviceHash() { return deviceHash; }
    public void setDeviceHash(String v) { this.deviceHash = v; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String v) { this.deviceName = v; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String v) { this.deviceModel = v; }

    public String getOsName() { return osName; }
    public void setOsName(String v) { this.osName = v; }

    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String v) { this.osVersion = v; }

    public String getBrowser() { return browser; }
    public void setBrowser(String v) { this.browser = v; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String v) { this.userAgent = v; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String v) { this.ipAddress = v; }

    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String v) { this.lastLoginIp = v; }

    public String getIsp() { return isp; }
    public void setIsp(String v) { this.isp = v; }

    public String getNetworkType() { return networkType; }
    public void setNetworkType(String v) { this.networkType = v; }

    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }

    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double v) { this.latitude = v; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double v) { this.longitude = v; }

    public String getCpu() { return cpu; }
    public void setCpu(String v) { this.cpu = v; }

    public String getRam() { return ram; }
    public void setRam(String v) { this.ram = v; }

    public String getScreenResolution() { return screenResolution; }
    public void setScreenResolution(String v) { this.screenResolution = v; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String v) { this.timezone = v; }

    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }

    public Boolean getVpnDetected() { return vpnDetected; }
    public void setVpnDetected(Boolean v) { this.vpnDetected = v; }

    public Boolean getProxyDetected() { return proxyDetected; }
    public void setProxyDetected(Boolean v) { this.proxyDetected = v; }

    public Boolean getMultiAccountDetected() { return multiAccountDetected; }
    public void setMultiAccountDetected(Boolean v) { this.multiAccountDetected = v; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer v) { this.riskScore = v; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }

    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime v) { this.lastLoginTime = v; }

    public Integer getLoginCount() { return loginCount; }
    public void setLoginCount(Integer v) { this.loginCount = v; }

    public Integer getFailedLoginAttempt() { return failedLoginAttempt; }
    public void setFailedLoginAttempt(Integer v) { this.failedLoginAttempt = v; }

    public LocalDateTime getLastFailedLogin() { return lastFailedLogin; }
    public void setLastFailedLogin(LocalDateTime v) { this.lastFailedLogin = v; }

    public String getLastLoginDeviceId() { return lastLoginDeviceId; }
    public void setLastLoginDeviceId(String v) { this.lastLoginDeviceId = v; }

    public String getLastLoginUserAgent() { return lastLoginUserAgent; }
    public void setLastLoginUserAgent(String v) { this.lastLoginUserAgent = v; }

    public Boolean getBlocked() { return blocked; }
    public void setBlocked(Boolean v) { this.blocked = v; }

    public Boolean getPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(Boolean v) { this.phoneVerified = v; }

    public Integer getTotalDevices() { return totalDevices; }
    public void setTotalDevices(Integer v) { this.totalDevices = v; }

    public Integer getMaxAllowedDevices() { return maxAllowedDevices; }
    public void setMaxAllowedDevices(Integer v) { this.maxAllowedDevices = v; }

    // ==================================================
    // BUILDER
    // ==================================================
    public static class Builder {

        private final UserDto dto = new UserDto();

        public Builder id(Long value) { dto.id = value; return this; }
        public Builder mobile(String value) { dto.mobile = value; return this; }
        public Builder password(String value) { dto.password = value; return this; }
        public Builder gameId(String value) { dto.gameId = value; return this; }

        public Builder balance(BigDecimal value) { dto.balance = value; return this; }
        public Builder depositBalance(BigDecimal value) { dto.depositBalance = value; return this; }
        public Builder withdrawBalance(BigDecimal value) { dto.withdrawBalance = value; return this; }

        public Builder role(String value) { dto.role = value; return this; }
        public Builder avatarUrl(String value) { dto.avatarUrl = value; return this; }
        public Builder displayName(String value) { dto.displayName = value; return this; }
        public Builder lifetimeEarnings(BigDecimal value) { dto.lifetimeEarnings = value; return this; }

        public Builder referralCode(String value) { dto.referralCode = value; return this; }
        public Builder referredBy(String value) { dto.referredBy = value; return this; }
        public Builder signupBonusClaimed(boolean value) { dto.signupBonusClaimed = value; return this; }

        public Builder deviceId(String value) { dto.deviceId = value; return this; }
        public Builder deviceFingerprint(String value) { dto.deviceFingerprint = value; return this; }
        public Builder ipAddress(String value) { dto.ipAddress = value; return this; }
        public Builder country(String value) { dto.country = value; return this; }
        public Builder city(String value) { dto.city = value; return this; }

        public Builder riskScore(Integer value) { dto.riskScore = value; return this; }
        public Builder riskLevel(String value) { dto.riskLevel = value; return this; }

        public Builder lastLoginTime(LocalDateTime value) { dto.lastLoginTime = value; return this; }
        public Builder loginCount(Integer value) { dto.loginCount = value; return this; }
        public Builder blocked(Boolean value) { dto.blocked = value; return this; }
        public Builder phoneVerified(Boolean value) { dto.phoneVerified = value; return this; }
        public Builder totalDevices(Integer value) { dto.totalDevices = value; return this; }

        public UserDto build() { return dto; }
    }

    @Override
    public String toString() {
        return "UserDto{" +
                "id=" + id +
                ", mobile='" + mobile + '\'' +
                ", gameId='" + gameId + '\'' +
                ", balance=" + balance +
                ", depositBalance=" + depositBalance +
                ", withdrawBalance=" + withdrawBalance +
                ", deviceId='" + deviceId + '\'' +
                ", fingerprint='" + deviceFingerprint + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", country='" + country + '\'' +
                ", city='" + city + '\'' +
                ", riskScore=" + riskScore +
                ", riskLevel='" + riskLevel + '\'' +
                ", vpnDetected=" + vpnDetected +
                ", proxyDetected=" + proxyDetected +
                ", blocked=" + blocked +
                '}';
    }
}
