package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.BonusHistory;
import com.yourcompany.ludo.model.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UserService {

    // ================== Registration ==================
    User register(String mobile, String password);
    User registerWithReferral(String mobile, String password, String referralCode);

    // ================== Login ==================
    UserDto login(String mobile, String password);

    // ================== Find Users ==================
    Optional<User> findByMobile(String mobile);
    Optional<User> findByGameId(String gameId);
    Optional<User> findByReferralCode(String referralCode);
    Optional<String> findGameIdByReferralCode(String referralCode);

    // ================== Save / Update ==================
    User save(User user);
    void updateProfile(String gameId, String displayName, String avatarUrl);
    void updateAvatar(String gameId, String avatarUrl);

    // ================== Balance & Earnings ==================
    BigDecimal getBalance(String gameId);
    void setBalance(String gameId, BigDecimal newBalance);
    void addBalance(String gameId, BigDecimal amount);
    void deductBalance(String gameId, BigDecimal amount);
    BigDecimal getLifetimeEarnings(String gameId);
    void addToLifetimeEarnings(String gameId, BigDecimal amount);

    // ================== Credit Balance (for MatchService) ==================
    void creditBalance(User user, BigDecimal amount);

    // ================== Notifications ==================
    void notifyUserUpdate(String gameId);
    void notifyBalanceUpdate(String gameId, BigDecimal newBalance);

    // ================== Referral / Signup / Bonuses ==================
    String generateUniqueReferralCode();
    void giveReferralBonus(String newUserGameId, String referralCode);
    void giveSignupBonus(User user);

    // ================== Bonus History ==================
    List<BonusHistory> getUserBonusHistory(String gameId);
}
