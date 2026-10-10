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

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.DOWN);
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
                .balance(scale(user.getBalance()))
                .turnoverBalance(scale(user.getTurnoverBalance()))
                .withdrawableBalance(scale(user.getWithdrawableBalance()))
                .lifetimeEarnings(scale(user.getLifetimeEarnings()))
                .lifetimeWithdraw(scale(user.getLifetimeWithdraw()))
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .displayName(user.getDisplayName())
                .referralCode(user.getReferralCode())
                .referredBy(user.getReferredBy())
                .signupBonusClaimed(user.isSignupBonusClaimed())
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

    // ================= DEPOSIT (balance + turnover বাড়ে) =================
    @Transactional(rollbackFor = Exception.class)
    public void addDepositBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid deposit amount");
        }

        User user = lockAndGetByGameId(gameId);
        user.addDeposit(scale(amount));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    // ================= WINNING (শুধু balance + lifetimeEarnings বাড়ে) =================
    @Transactional(rollbackFor = Exception.class)
    public void addWinningBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid winning amount");
        }

        User user = lockAndGetByGameId(gameId);
        BigDecimal a = scale(amount);
        user.addWinnings(a);
        user.addLifetimeEarnings(a);

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

        return scale(user.getBalance());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTurnoverBalance(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(u -> scale(u.getTurnoverBalance()))
                .orElse(bd("0.00"));
    }

    // ================= ADD BALANCE (ডিপোজিট) =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBalance(String gameId, BigDecimal amount) {
        addDepositBalance(gameId, amount);
    }

    // ================= DEDUCT BALANCE (এন্ট্রি ফি) =================
    // ব্যালেন্স কমে + টার্নওভার ১০০% কমে (০ এর নিচে যায় না)।
    // রিটার্ন: টার্নওভার আসলে কত কমল। এটা ম্যাচ অংশগ্রহণকারীর রেকর্ডে সেভ রাখুন।
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal deductBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        User user = lockAndGetByGameId(gameId);
        BigDecimal turnoverReduced = user.deduct(scale(amount)); // Insufficient হলে IllegalStateException

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));

        return scale(turnoverReduced);
    }

    // ================= REFUND ENTRY FEE (ম্যাচ বাতিল) =================
    // পুরো ফি ব্যালেন্সে ফেরত, টার্নওভার শুধু যতটুকু কমেছিল ততটুকু ফেরত
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundEntryFee(String gameId, BigDecimal amount, BigDecimal turnoverRestore) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        User user = lockAndGetByGameId(gameId);
        user.refundEntryFee(scale(amount), scale(turnoverRestore));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    // ================= SET BALANCE (অ্যাডমিন) =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setBalance(String gameId, BigDecimal newBalance) {
        if (newBalance == null || newBalance.signum() < 0) {
            throw new IllegalArgumentException("Invalid balance");
        }

        User user = lockAndGetByGameId(gameId);
        user.setBalance(scale(newBalance));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    // ================= REFUND (পুরনো) =================
    /**
     * @deprecated ম্যাচ বাতিলে এটা ব্যবহার করবেন না: এটা ফি-র পুরোটাই টার্নওভারে যোগ করে,
     * অথচ deduct() টার্নওভার ০ এ আটকে গেলে কম কমে। refundEntryFee() ব্যবহার করুন।
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void refundBalance(String gameId, BigDecimal amount) {
        addDepositBalance(gameId, amount);
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

    // ================= WITHDRAW FLOW =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void holdForWithdraw(String gameId, BigDecimal amount) {
        User user = lockAndGetByGameId(gameId);
        user.holdForWithdraw(scale(amount)); // টার্নওভার বাকি থাকলে IllegalStateException

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundWithdraw(String gameId, BigDecimal amount) {
        User user = lockAndGetByGameId(gameId);
        user.refundWithdraw(scale(amount));

        userRepository.saveAndFlush(user);
        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(user));
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

            // বোনাসও ডিপোজিটের মতো: ব্যালেন্স + টার্নওভার
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
        user.setBalance(bd("0.00"));
        user.setTurnoverBalance(bd("0.00"));
        user.setLifetimeEarnings(bd("0.00"));
        user.setLifetimeWithdraw(bd("0.00"));
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

        // সাইনআপ বোনাস যোগ হওয়ার পরের নতুন মান নিয়ে আসি
        User fresh = userRepository.findByGameId(saved.getGameId()).orElse(saved);

        sendProfile(fresh);
        sendEvent(EVENT_PROFILE_CREATED, fresh.getGameId(), toDto(fresh));

        return fresh;
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
    // ⚠️ নামে notify হলেও এটা amount যোগ করে (ডিপোজিট হিসেবে)। কোথাও কল হচ্ছে কিনা দেখে নিন।
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyBalanceUpdate(String gameId, BigDecimal amount) {
        addDepositBalance(gameId, amount);
    }

    // ================= LIFETIME EARNING =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToLifetimeEarnings(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        User user = lockAndGetByGameId(gameId);
        user.addLifetimeEarnings(scale(amount));

        userRepository.saveAndFlush(user);
        sendEvent(EVENT_LIFETIME_UPDATED, gameId, toDto(user));
    }

    @Override
    public BigDecimal getLifetimeEarnings(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(u -> scale(u.getLifetimeEarnings()))
                .orElse(bd("0.00"));
    }

    @Override
    public BigDecimal getLifetimeWithdraw(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(u -> scale(u.getLifetimeWithdraw()))
                .orElse(bd("0.00"));
    }

    // উত্তোলনযোগ্য টাকা (টার্নওভার বাকি থাকলে ০)
    @Override
    public BigDecimal getWithdrawBalance(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(u -> scale(u.getWithdrawableBalance()))
                .orElse(bd("0.00"));
    }

    // জেতা টাকা যোগ (শুধু ব্যালেন্স + লাইফটাইম আয়)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addWithdrawBalance(String gameId, BigDecimal amount) {
        addWinningBalance(gameId, amount);
    }

    // শুধু উত্তোলন সফল (অ্যাডমিন অ্যাপ্রুভ) হলে কল করুন। ব্যালেন্সে প্রভাব নেই।
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addLifetimeWithdraw(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        User user = lockAndGetByGameId(gameId);
        user.markWithdrawSuccess(scale(amount));

        userRepository.saveAndFlush(user);
        sendProfile(user);
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

    // ================= GET ALL USERS =================
    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
