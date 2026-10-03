package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.BonusHistory;
import com.yourcompany.ludo.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserService {

    // ================== Registration ==================
    User register(String mobile, String password);
    User registerWithReferral(String mobile, String password, String referralCode);

    // ================== Login ==================
    UserDto login(String mobile, String password);
    void updateLoginInformation(String gameId, String deviceId, String userAgent);
    void increaseFailedLoginAttempt(String gameId);
    void resetFailedLoginAttempt(String gameId);

    // ================== Find Users ==================
    Optional<User> findByMobile(String mobile);
    Optional<User> findByGameId(String gameId);
    Optional<User> findByReferralCode(String referralCode);
    Optional<String> findGameIdByReferralCode(String referralCode);
    Optional<User> findByIdForUpdate(Long id);
    Optional<User> findByGameIdForUpdate(String gameId);

    // ================== Save / Update ==================
    User save(User user);
    void updateProfile(String gameId, String displayName, String avatarUrl);
    void updateAvatar(String gameId, String avatarUrl);
    UserDto getUserProfile(String gameId);
    List<UserDto> getAllUsers();
    long countUsers();

    // ================== Balance & Earnings ==================
    BigDecimal getBalance(String gameId);
    void setBalance(String gameId, BigDecimal newBalance);
    void addBalance(String gameId, BigDecimal amount);
    void deductBalance(String gameId, BigDecimal amount);
    BigDecimal getWithdrawBalance(String gameId);
    void addWithdrawBalance(String gameId, BigDecimal amount);
    BigDecimal getLifetimeEarnings(String gameId);
    void addToLifetimeEarnings(String gameId, BigDecimal amount);
    void addLifetimeWithdraw(String gameId, BigDecimal amount);

    // ================== Credit Balance (for MatchService) ==================
    void creditBalance(User user, BigDecimal amount);

    // ================== Notifications ==================
    void notifyUserUpdate(String gameId);
    void notifyBalanceUpdate(String gameId, BigDecimal newBalance);

    // ================== Referral / Signup / Bonuses ==================
    String generateUniqueReferralCode();
    void giveReferralBonus(String newUserGameId, String referralCode);
    void giveSignupBonus(User user);
    void updateReferralCode(String gameId, String code);
    void updateReferredBy(String gameId, String referredBy);
    void updateSignupBonusStatus(String gameId, boolean status);
    void updateFirstDepositBonusStatus(String gameId, boolean status);
    void updateReferralBonusStatus(String gameId, boolean status);
    void updateReferrerBonusStatus(String gameId, boolean status);

    // ================== Bonus History ==================
    List<BonusHistory> getUserBonusHistory(String gameId);

    // ================== Device Security ==================
    void updateDeviceInformation(String gameId, String deviceId, String deviceFingerprint,
                                 String deviceHash, String deviceName, String deviceModel);
    void updateHardwareInformation(String gameId, String cpu, String ram,
                                   String screenResolution, String timezone, String language);
    void updateOSInformation(String gameId, String osName, String osVersion);
    void updateBrowserInformation(String gameId, String browser, String userAgent);

    // ================== Network / Location ==================
    void updateNetworkInformation(String gameId, String ipAddress, String lastLoginIp,
                                  String isp, String networkType);
    void updateLocationInformation(String gameId, String country, String city,
                                   Double latitude, Double longitude);

    // ================== Fraud / Risk ==================
    void updateRiskInformation(String gameId, Integer riskScore, String riskLevel);
    void updateVpnStatus(String gameId, Boolean detected);
    void updateProxyStatus(String gameId, Boolean detected);
    void updateMultiAccountStatus(String gameId, Boolean detected);

    // ================== Account Security ==================
    void blockUser(String gameId);
    void unblockUser(String gameId);
    boolean isBlocked(String gameId);
    void verifyPhone(String gameId);
    boolean isPhoneVerified(String gameId);

    // ================== Device Management ==================
    Integer getTotalDevices(String gameId);
    void increaseDeviceCount(String gameId);
    void decreaseDeviceCount(String gameId);
    boolean canAddNewDevice(String gameId);
    void setMaxAllowedDevices(String gameId, Integer limit);

    // ================== User Status ==================
    void setOnline(String gameId);
    void setOffline(String gameId);
    void updateLastActive(String gameId, LocalDateTime time);
}
