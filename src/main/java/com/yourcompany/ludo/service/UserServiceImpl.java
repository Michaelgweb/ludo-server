package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.RealtimeEvent;
import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.BonusHistory;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.BonusHistoryRepository;
import com.yourcompany.ludo.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Primary
public class UserServiceImpl implements UserService, UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    // ================= EVENTS =================
    private static final String EVENT_PROFILE_CREATED = "PROFILE_CREATED";
    private static final String EVENT_PROFILE_UPDATED = "PROFILE_UPDATED";
    private static final String EVENT_BALANCE_UPDATED = "BALANCE_UPDATED";
    private static final String EVENT_WIN_UPDATED = "WIN_BALANCE_UPDATED";
    private static final String EVENT_LIFETIME_UPDATED = "LIFETIME_UPDATED";

    // ================= BONUS =================
    private static final BigDecimal SIGNUP_BONUS = bd("20.00");
    private static final BigDecimal REFERRER_PENDING_BONUS = bd("40.00");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.DOWN);
    }

    // ================= AUTOWIRE =================
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BonusHistoryRepository bonusHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ================= MOBILE =================
    private String normalizeMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile empty");
        }

        mobile = mobile.trim();

        if (mobile.startsWith("880")) {
            return mobile;
        }

        return "880" + mobile.replaceFirst("^0+", "");
    }

    // ================= GAME ID =================
    private String generateUniqueGameId() {
        String gameId;

        do {
            gameId = String.format("%012d", SECURE_RANDOM.nextLong(1_000_000_000_000L));
        } while (userRepository.findByGameId(gameId).isPresent());

        return gameId;
    }

    // ================= REFERRAL =================
    @Override
    public String generateUniqueReferralCode() {
        String code;

        do {
            code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        } while (userRepository.findByReferralCode(code).isPresent());

        return code;
    }

    // ================= DTO =================
    private UserDto toDto(User user) {
        return new UserDto.Builder()
                .id(user.getId())
                .mobile(user.getMobile())
                .gameId(user.getGameId())
                .balance(calculateTotal(user))
                .depositBalance(user.getDepositBalance())
                .withdrawBalance(user.getWithdrawBalance())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .displayName(user.getDisplayName())
                .lifetimeEarnings(user.getLifetimeEarnings())
                .referralCode(user.getReferralCode())
                .build();
    }

    // ================= REALTIME =================
    private void sendProfile(User user) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/profile/" + user.getGameId(),
                    toDto(user)
            );
        } catch (Exception e) {
            log.error("Error sending profile update for gameId: {}", user.getGameId(), e);
        }
    }

    private void sendEvent(String type, String gameId, Object data) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/events/" + gameId,
                    new RealtimeEvent(type, "update", data)
            );
        } catch (Exception e) {
            log.error("Error sending event {} for gameId: {}", type, gameId, e);
        }
    }

    // ================= LOCK =================
    public User lockAndGetByGameId(String gameId) {
        return userRepository.findByGameIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ================= TOTAL BALANCE =================
    private BigDecimal calculateTotal(User user) {
        BigDecimal deposit = user.getDepositBalance() == null ? BigDecimal.ZERO : user.getDepositBalance();
        BigDecimal winning = user.getWithdrawBalance() == null ? BigDecimal.ZERO : user.getWithdrawBalance();

        return deposit.add(winning).setScale(2, RoundingMode.DOWN);
    }

    // ================= DEPOSIT BALANCE ADD =================
    @Transactional(rollbackFor = Exception.class)
    public void addDepositBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid deposit amount");
        }

        User user = lockAndGetByGameId(gameId);
        BigDecimal deposit = user.getDepositBalance() == null ? BigDecimal.ZERO : user.getDepositBalance();

        deposit = deposit.add(amount).setScale(2, RoundingMode.DOWN);
        user.setDepositBalance(deposit);

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    // ================= WINNING BALANCE ADD =================
    @Transactional(rollbackFor = Exception.class)
    public void addWinningBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid winning amount");
        }

        User user = lockAndGetByGameId(gameId);
        BigDecimal winning = user.getWithdrawBalance() == null ? BigDecimal.ZERO : user.getWithdrawBalance();

        winning = winning.add(amount).setScale(2, RoundingMode.DOWN);
        user.setWithdrawBalance(winning);

        BigDecimal lifetime = user.getLifetimeEarnings() == null ? BigDecimal.ZERO : user.getLifetimeEarnings();
        user.setLifetimeEarnings(lifetime.add(amount).setScale(2, RoundingMode.DOWN));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_WIN_UPDATED, gameId, toDto(user));
    }

    // ================= GET BALANCE =================
    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return calculateTotal(user);
    }

    // ================= ADD BALANCE =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBalance(String gameId, BigDecimal amount) {
        addDepositBalance(gameId, amount);
    }

    // ================= DEDUCT BALANCE =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        User user = lockAndGetByGameId(gameId);

        BigDecimal deposit = user.getDepositBalance() == null ? BigDecimal.ZERO : user.getDepositBalance();
        BigDecimal winning = user.getWithdrawBalance() == null ? BigDecimal.ZERO : user.getWithdrawBalance();
        BigDecimal total = deposit.add(winning);

        if (total.compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient Balance");
        }

        // CUT DEPOSIT FIRST
        if (deposit.compareTo(amount) >= 0) {
            deposit = deposit.subtract(amount);
        } else {
            BigDecimal remaining = amount.subtract(deposit);
            deposit = BigDecimal.ZERO;
            winning = winning.subtract(remaining);
        }

        user.setDepositBalance(deposit.setScale(2, RoundingMode.DOWN));
        user.setWithdrawBalance(winning.setScale(2, RoundingMode.DOWN));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    // ================= SET DEPOSIT =================
    @Transactional(rollbackFor = Exception.class)
    public void setDepositBalance(String gameId, BigDecimal value) {
        User user = lockAndGetByGameId(gameId);
        user.setDepositBalance(value.setScale(2, RoundingMode.DOWN));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    // ================= SET WINNING =================
    @Transactional(rollbackFor = Exception.class)
    public void setWinningBalance(String gameId, BigDecimal value) {
        User user = lockAndGetByGameId(gameId);
        user.setWithdrawBalance(value.setScale(2, RoundingMode.DOWN));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_WIN_UPDATED, gameId, toDto(user));
    }

    // ================= REFUND =================
    @Transactional(rollbackFor = Exception.class)
    public void refundBalance(String gameId, BigDecimal amount) {
        addWinningBalance(gameId, amount);
    }

    // ================= CREDIT WIN =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void creditBalance(User user, BigDecimal amount) {
        if (user == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        addWinningBalance(user.getGameId(), amount);
    }

    // ================= BONUS HISTORY =================
    private void recordBonus(String userGameId, String type, BigDecimal amount, String sourceGameId, String status) {
        BonusHistory bonus = new BonusHistory();
        bonus.setUserGameId(userGameId);
        bonus.setType(type);
        bonus.setAmount(amount);
        bonus.setSourceGameId(sourceGameId);
        bonus.setCreatedAt(Instant.now());
        bonus.setStatus(status);

        bonusHistoryRepository.save(bonus);
    }

    // ================= SIGNUP BONUS =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void giveSignupBonus(User user) {
        User locked = lockAndGetByGameId(user.getGameId());

        if (!locked.isSignupBonusClaimed()) {
            locked.setSignupBonusClaimed(true);
            userRepository.saveAndFlush(locked);

            addDepositBalance(locked.getGameId(), SIGNUP_BONUS);
            recordBonus(locked.getGameId(), "SIGNUP", SIGNUP_BONUS, null, "COMPLETED");
        }
    }

    // ================= REFERRAL BONUS =================
    /**
     * রেফারারের জন্য PENDING বোনাস রেকর্ড করে (টাকা এখানে যোগ হয় না)।
     *
     * গার্ড:
     *  1) নিজেকে রেফার করা যাবে না
     *  2) নতুন ইউজারের referredBy অবশ্যই এই রেফারার হতে হবে (আসল রেফারেল)
     *  3) রেফারারকে লক করে ডুপ্লিকেট চেক, তাই একসাথে দুই রিকোয়েস্ট এলেও একটাই রেকর্ড
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void giveReferralBonus(String newUserGameId, String referralCode) {
        if (newUserGameId == null || referralCode == null || referralCode.isBlank()) {
            return;
        }

        User newUser = userRepository.findByGameId(newUserGameId).orElse(null);
        User referrerRef = userRepository.findByReferralCode(referralCode.trim()).orElse(null);

        if (newUser == null || referrerRef == null) {
            return;
        }

        if (referrerRef.getGameId().equals(newUser.getGameId())) {
            log.warn("Self referral blocked: gameId={}", newUserGameId);
            return;
        }

        if (!referrerRef.getGameId().equals(newUser.getReferredBy())) {
            log.warn("Referral mismatch blocked: newUser={} referredBy={} referrer={}",
                    newUserGameId, newUser.getReferredBy(), referrerRef.getGameId());
            return;
        }

        User referrer = lockAndGetByGameId(referrerRef.getGameId());

        boolean pending = bonusHistoryRepository.existsByUserGameIdAndSourceGameIdAndTypeAndStatus(
                referrer.getGameId(), newUserGameId, "REFERRER_PENDING", "PENDING");
        boolean completed = bonusHistoryRepository.existsByUserGameIdAndSourceGameIdAndTypeAndStatus(
                referrer.getGameId(), newUserGameId, "REFERRER_PENDING", "COMPLETED");

        if (!pending && !completed) {
            recordBonus(referrer.getGameId(), "REFERRER_PENDING", REFERRER_PENDING_BONUS, newUserGameId, "PENDING");
        }
    }

    // ================= REGISTER =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(String mobile, String password) {
        return registerWithReferral(mobile, password, null);
    }

    // ================= REGISTER WITH REFERRAL =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User registerWithReferral(String mobile, String password, String referralCode) {
        mobile = normalizeMobile(mobile);

        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password too short");
        }

        if (userRepository.findByMobile(mobile).isPresent()) {
            throw new RuntimeException("User already exists");
        }

        User user = new User();
        user.setMobile(mobile);
        user.setPassword(passwordEncoder.encode(password));
        user.setDepositBalance(bd("0.00"));
        user.setWithdrawBalance(bd("0.00"));
        user.setLifetimeEarnings(bd("0.00"));
        user.setRole(User.Role.USER);
        user.setGameId(generateUniqueGameId());
        user.setReferralCode(generateUniqueReferralCode());
        user.setSignupBonusClaimed(false);

        User saved = userRepository.saveAndFlush(user);

        giveSignupBonus(saved);

        if (referralCode != null && !referralCode.isBlank()) {
            saved.setReferredBy(
                    userRepository.findByReferralCode(referralCode.trim())
                            .map(User::getGameId)
                            .orElse(null)
            );

            userRepository.saveAndFlush(saved);
            giveReferralBonus(saved.getGameId(), referralCode.trim());
        }

        sendProfile(saved);
        sendEvent(EVENT_PROFILE_CREATED, saved.getGameId(), toDto(saved));

        return saved;
    }

    // ================= LOGIN =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto login(String mobile, String password) {
        mobile = normalizeMobile(mobile);

        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid password");
        }

        user.setLoginCount(user.getLoginCount() + 1);
        user.setLastLoginTime(LocalDateTime.now());

        userRepository.save(user);

        return toDto(user);
    }

    @Override
    public void updateLoginInformation(String gameId, String deviceId, String userAgent) {
        // Implementation
    }

    @Override
    public void increaseFailedLoginAttempt(String gameId) {
        // Implementation
    }

    @Override
    public void resetFailedLoginAttempt(String gameId) {
        // Implementation
    }

    // ================= SECURITY =================
    @Override
    public UserDetails loadUserByUsername(String gameId) throws UsernameNotFoundException {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new org.springframework.security.core.userdetails.User(
                user.getGameId(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    // ================= FIND USER =================
    @Override
    public Optional<User> findByMobile(String mobile) {
        return userRepository.findByMobile(normalizeMobile(mobile));
    }

    @Override
    public Optional<User> findByGameId(String gameId) {
        return userRepository.findByGameId(gameId);
    }

    @Override
    public Optional<User> findByReferralCode(String referralCode) {
        return userRepository.findByReferralCode(referralCode);
    }

    @Override
    public Optional<String> findGameIdByReferralCode(String referralCode) {
        return userRepository.findByReferralCode(referralCode).map(User::getGameId);
    }

    // ================= LOCK FIND =================
    @Override
    @Transactional
    public Optional<User> findByIdForUpdate(Long id) {
        return userRepository.findByIdForUpdate(id);
    }

    @Override
    @Transactional
    public Optional<User> findByGameIdForUpdate(String gameId) {
        return userRepository.findByGameIdForUpdate(gameId);
    }

    // ================= SAVE =================
    @Override
    public User save(User user) {
        return userRepository.saveAndFlush(user);
    }

    // ================= UPDATE AVATAR =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(String gameId, String avatarUrl) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        notifyUserUpdate(gameId);
    }

    // ================= UPDATE PROFILE =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(String gameId, String displayName, String avatarUrl) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setDisplayName(displayName);
        user.setAvatarUrl(avatarUrl);

        userRepository.save(user);
        notifyUserUpdate(gameId);
    }

    // ================= REALTIME UPDATE =================
    @Override
    public void notifyUserUpdate(String gameId) {
        userRepository.findByGameId(gameId).ifPresent(user -> {
            sendProfile(user);
            sendEvent(EVENT_PROFILE_UPDATED, gameId, toDto(user));
        });
    }

    // ================= BALANCE UPDATE EVENT =================
    // ⚠️ নামে notify হলেও এটা amount যোগ করে। কোথাও কল হচ্ছে কিনা দেখে নিন।
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyBalanceUpdate(String gameId, BigDecimal amount) {
        addDepositBalance(gameId, amount);
    }

    // ================= SET TOTAL BALANCE =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setBalance(String gameId, BigDecimal newBalance) {
        User user = lockAndGetByGameId(gameId);

        BigDecimal winning = user.getWithdrawBalance() == null ? BigDecimal.ZERO : user.getWithdrawBalance();
        BigDecimal deposit = newBalance.subtract(winning);

        if (deposit.compareTo(BigDecimal.ZERO) < 0) {
            deposit = BigDecimal.ZERO;
        }

        user.setDepositBalance(deposit.setScale(2, RoundingMode.DOWN));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    // ================= LIFETIME EARNING =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToLifetimeEarnings(String gameId, BigDecimal amount) {
        User user = lockAndGetByGameId(gameId);

        BigDecimal current = user.getLifetimeEarnings() == null ? BigDecimal.ZERO : user.getLifetimeEarnings();
        user.setLifetimeEarnings(current.add(amount).setScale(2, RoundingMode.DOWN));

        userRepository.saveAndFlush(user);
        sendEvent(EVENT_LIFETIME_UPDATED, gameId, toDto(user));
    }

    @Override
    public BigDecimal getLifetimeEarnings(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(User::getLifetimeEarnings)
                .orElse(bd("0.00"));
    }

    @Override
    public BigDecimal getWithdrawBalance(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(User::getWithdrawBalance)
                .orElse(bd("0.00"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addWithdrawBalance(String gameId, BigDecimal amount) {
        addWinningBalance(gameId, amount);
    }

    // ✅ FIX: আগে এটা lifetimeEarnings বাড়াত; এখন সঠিকভাবে lifetimeWithdraw বাড়ায়
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addLifetimeWithdraw(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        User user = lockAndGetByGameId(gameId);

        BigDecimal current = user.getLifetimeWithdraw() == null ? BigDecimal.ZERO : user.getLifetimeWithdraw();
        user.setLifetimeWithdraw(current.add(amount).setScale(2, RoundingMode.DOWN));

        userRepository.saveAndFlush(user);
        sendEvent(EVENT_LIFETIME_UPDATED, gameId, toDto(user));
    }

    // ================= REFERRAL UPDATES =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReferralCode(String gameId, String code) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setReferralCode(code);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReferredBy(String gameId, String referredBy) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setReferredBy(referredBy);
        userRepository.saveAndFlush(user);
    }

    // ================= BONUS STATUS UPDATES =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSignupBonusStatus(String gameId, boolean status) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setSignupBonusClaimed(status);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFirstDepositBonusStatus(String gameId, boolean status) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFirstDepositBonusGiven(status);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReferralBonusStatus(String gameId, boolean status) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setReferralBonusClaimed(status);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReferrerBonusStatus(String gameId, boolean status) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setReferrerBonusGiven(status);
        userRepository.saveAndFlush(user);
    }

    // ================= BONUS HISTORY =================
    @Override
    @Transactional(readOnly = true)
    public List<BonusHistory> getUserBonusHistory(String gameId) {
        return bonusHistoryRepository.findByUserGameIdOrderByCreatedAtDesc(gameId);
    }

    // ================= DEVICE SECURITY =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceInformation(String gameId, String deviceId, String deviceFingerprint,
                                       String deviceHash, String deviceName, String deviceModel) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setDeviceId(deviceId);
        user.setDeviceName(deviceName);
        user.setDeviceModel(deviceModel);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateHardwareInformation(String gameId, String cpu, String ram,
                                         String screenResolution, String timezone, String language) {
        // Implementation
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOSInformation(String gameId, String osName, String osVersion) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setOsName(osName);
        user.setOsVersion(osVersion);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrowserInformation(String gameId, String browser, String userAgent) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBrowser(browser);
        user.setUserAgent(userAgent);
        userRepository.saveAndFlush(user);
    }

    // ================= NETWORK SECURITY =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNetworkInformation(String gameId, String ipAddress, String lastLoginIp,
                                        String isp, String networkType) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIpAddress(ipAddress);
        user.setLastLoginIp(lastLoginIp);
        userRepository.saveAndFlush(user);
    }

    // ================= LOCATION =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLocationInformation(String gameId, String country, String city,
                                         Double latitude, Double longitude) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setCountry(country);
        user.setCity(city);
        user.setLatitude(latitude);
        user.setLongitude(longitude);
        userRepository.saveAndFlush(user);
    }

    // ================= FRAUD / RISK SECURITY =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRiskInformation(String gameId, Integer riskScore, String riskLevel) {
        // Implementation
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVpnStatus(String gameId, Boolean detected) {
        // Implementation
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProxyStatus(String gameId, Boolean detected) {
        // Implementation
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMultiAccountStatus(String gameId, Boolean detected) {
        // Implementation
    }

    // ================= ACCOUNT SECURITY =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void blockUser(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBlocked(true);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unblockUser(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBlocked(false);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlocked(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(User::isBlocked)
                .orElse(false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyPhone(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPhoneVerified(true);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPhoneVerified(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(u -> u.getPhoneVerified() != null && u.getPhoneVerified())
                .orElse(false);
    }

    // ================= DEVICE MANAGEMENT =================
    @Override
    @Transactional(readOnly = true)
    public Integer getTotalDevices(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(User::getTotalDevices)
                .orElse(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseDeviceCount(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.increaseDeviceCount();
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decreaseDeviceCount(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        int current = user.getTotalDevices() != null ? user.getTotalDevices() : 0;
        user.setTotalDevices(Math.max(0, current - 1));
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAddNewDevice(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(u -> {
                    Integer total = u.getTotalDevices() != null ? u.getTotalDevices() : 0;
                    return total < 5; // Default max 5 devices
                })
                .orElse(false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setMaxAllowedDevices(String gameId, Integer limit) {
        // User model doesn't have maxAllowedDevices field, so implementation skipped
    }

    // ================= USER STATUS =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setOnline(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(User.Status.ONLINE);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setOffline(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(User.Status.OFFLINE);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLastActive(String gameId, LocalDateTime time) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastActive(time != null ? time : LocalDateTime.now());
        userRepository.saveAndFlush(user);
    }

    // ================= COUNT USERS =================
    @Override
    @Transactional(readOnly = true)
    public long countUsers() {
        return userRepository.count();
    }

    // ================= USER PROFILE =================
    @Override
    @Transactional(readOnly = true)
    public UserDto getUserProfile(String gameId) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return toDto(user);
    }

    // ================= GET ALL USERS ✅ FIXED =================
    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
