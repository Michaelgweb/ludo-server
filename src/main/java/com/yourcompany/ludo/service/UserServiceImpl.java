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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@Primary
public class UserServiceImpl implements UserService, UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final String EVENT_PROFILE_CREATED   = "PROFILE_CREATED";
    private static final String EVENT_PROFILE_UPDATED   = "PROFILE_UPDATED";
    private static final String EVENT_BALANCE_UPDATED   = "BALANCE_UPDATED";
    private static final String EVENT_LIFETIME_UPDATED  = "LIFETIME_EARNINGS_UPDATED";

    private static final BigDecimal SIGNUP_BONUS           = bd("20.00");
    private static final BigDecimal REFERRER_PENDING_BONUS = bd("40.00");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(2, RoundingMode.DOWN);
    }

    @Autowired private UserRepository userRepository;
    @Autowired private BonusHistoryRepository bonusHistoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // ===== Utility =====
    private String normalizeMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty())
            throw new IllegalArgumentException("Mobile cannot be empty");
        mobile = mobile.trim();
        return mobile.startsWith("880") ? mobile : "880" + mobile.replaceFirst("^0+", "");
    }

    private String generateUniqueGameId() {
        Random random = new Random();
        String gameId;
        do {
            gameId = String.format("%012d", Math.abs(random.nextLong()) % 1_000_000_000_000L);
        } while (userRepository.findByGameId(gameId).isPresent());
        return gameId;
    }

    @Override
    public String generateUniqueReferralCode() {
        Random random = new Random();
        String code;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
        } while (userRepository.findByReferralCode(code).isPresent());
        return code;
    }

    private UserDto toDto(User u) {
        return new UserDto.Builder()
                .id(u.getId())
                .mobile(u.getMobile())
                .gameId(u.getGameId())
                .balance(u.getBalance())
                .role(u.getRole().name())
                .avatarUrl(u.getAvatarUrl())
                .displayName(u.getDisplayName())
                .lifetimeEarnings(u.getLifetimeEarnings())
                .referralCode(u.getReferralCode())
                .referredBy(u.getReferredBy())
                .signupBonusClaimed(u.isSignupBonusClaimed())
                .build();
    }

    private void sendProfile(User u) {
        messagingTemplate.convertAndSend("/topic/profile/" + u.getGameId(), toDto(u));
    }

    private void sendEvent(String type, String gameId, Object data) {
        messagingTemplate.convertAndSend("/topic/events/" + gameId, new RealtimeEvent(type, "update", data));
    }

    // user lock with "for update"
    User lockAndGetByGameId(String gameId) {
        return userRepository.findByGameIdForUpdate(gameId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

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

    // ===== Balance Management =====
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void addToDepositBalance(String gameId, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) return;

        User user = lockAndGetByGameId(gameId);
        BigDecimal newBalance = user.getBalance().add(delta).setScale(2, RoundingMode.DOWN);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("Insufficient balance");

        user.setBalance(newBalance);
        userRepository.saveAndFlush(user);

        sendProfile(user);
        sendEvent(EVENT_BALANCE_UPDATED, user.getGameId(), toDto(user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be positive");

        addToDepositBalance(gameId, amount.setScale(2, RoundingMode.DOWN));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be positive");

        addToDepositBalance(gameId, amount.setScale(2, RoundingMode.DOWN).negate());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setBalance(String gameId, BigDecimal newBalance) {
        if (newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Balance cannot be negative");

        User locked = lockAndGetByGameId(gameId);
        locked.setBalance(newBalance.setScale(2, RoundingMode.DOWN));
        userRepository.saveAndFlush(locked);

        sendProfile(locked);
        sendEvent(EVENT_BALANCE_UPDATED, gameId, toDto(locked));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(User::getBalance)
                .orElse(bd("0.00"));
    }

    // ===== Lifetime Earnings =====
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToLifetimeEarnings(String gameId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Earning must be positive");

        User locked = lockAndGetByGameId(gameId);
        BigDecimal currentEarnings = locked.getLifetimeEarnings() != null
                ? locked.getLifetimeEarnings()
                : BigDecimal.ZERO;

        BigDecimal updatedEarnings = currentEarnings.add(amount).setScale(2, RoundingMode.DOWN);
        locked.setLifetimeEarnings(updatedEarnings);

        userRepository.saveAndFlush(locked);

        sendProfile(locked);
        sendEvent(EVENT_LIFETIME_UPDATED, gameId, toDto(locked));
    }

    // ===== Bonuses =====
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void giveSignupBonus(User user) {
        User locked = lockAndGetByGameId(user.getGameId());
        if (!locked.isSignupBonusClaimed()) {
            locked.setSignupBonusClaimed(true);
            userRepository.saveAndFlush(locked);
            addToDepositBalance(locked.getGameId(), SIGNUP_BONUS);
            recordBonus(locked.getGameId(), "SIGNUP", SIGNUP_BONUS, null, "COMPLETED");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void giveReferralBonus(String newUserGameId, String referralCode) {
        userRepository.findByReferralCode(referralCode).ifPresent(referrer -> {
            boolean alreadyPending = bonusHistoryRepository
                    .existsByUserGameIdAndSourceGameIdAndTypeAndStatus(
                            referrer.getGameId(),
                            newUserGameId,
                            "REFERRER_PENDING",
                            "PENDING"
                    );
            if (!alreadyPending) {
                recordBonus(
                        referrer.getGameId(),
                        "REFERRER_PENDING",
                        REFERRER_PENDING_BONUS,
                        newUserGameId,
                        "PENDING"
                );
            }
        });
    }

    // ===== Registration =====
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(String mobile, String password) {
        return registerWithReferral(mobile, password, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User registerWithReferral(String mobile, String password, String referralCode) {
        mobile = normalizeMobile(mobile);

        if (password == null || password.length() < 4)
            throw new IllegalArgumentException("Password too short");
        if (userRepository.findByMobile(mobile).isPresent())
            throw new RuntimeException("User already exists: " + mobile);

        User user = new User();
        user.setMobile(mobile);
        user.setPassword(passwordEncoder.encode(password));
        user.setBalance(bd("0.00"));
        user.setRole(User.Role.USER);
        user.setGameId(generateUniqueGameId());
        user.setLifetimeEarnings(bd("0.00"));
        user.setReferralCode(generateUniqueReferralCode());
        user.setSignupBonusClaimed(false);
        user.setFirstDepositBonusGiven(false);

        User saved = userRepository.saveAndFlush(user);

        giveSignupBonus(saved);

        if (referralCode != null && !referralCode.isBlank()) {
            saved.setReferredBy(userRepository.findByReferralCode(referralCode.trim())
                    .map(User::getGameId).orElse(null));
            userRepository.saveAndFlush(saved);
            giveReferralBonus(saved.getGameId(), referralCode.trim());
        }

        sendProfile(saved);
        sendEvent(EVENT_PROFILE_CREATED, saved.getGameId(), toDto(saved));

        return saved;
    }

    // ===== Login & Security =====
    @Override
    public UserDto login(String mobile, String password) {
        mobile = normalizeMobile(mobile);
        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new BadCredentialsException("Invalid password");

        return toDto(user);
    }

    @Override
    public UserDetails loadUserByUsername(String gameId) throws UsernameNotFoundException {
        // ✅ Lookup user by gameId instead of mobile
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for gameId: " + gameId));

        return new org.springframework.security.core.userdetails.User(
                user.getGameId(),               // principal = gameId
                user.getPassword(),             // password
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }

    // ===== Bonus History =====
    @Override
    @Transactional(readOnly = true)
    public List<BonusHistory> getUserBonusHistory(String gameId) {
        return bonusHistoryRepository.findByUserGameIdOrderByCreatedAtDesc(gameId);
    }

    @Transactional(readOnly = true)
    public List<BonusHistory> getAllBonusHistoryForAdmin() {
        return bonusHistoryRepository.findAllByOrderByCreatedAtDesc();
    }

    // ===== Avatar & Profile =====
    @Override
    public void updateAvatar(String gameId, String avatarUrl) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found: " + gameId));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        notifyUserUpdate(gameId);
    }

    @Override
    public void updateProfile(String gameId, String displayName, String avatarUrl) {
        User user = userRepository.findByGameId(gameId)
                .orElseThrow(() -> new RuntimeException("User not found: " + gameId));

        user.setDisplayName(displayName);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        notifyUserUpdate(gameId);
    }

    // ===== Notifications =====
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyBalanceUpdate(String gameId, BigDecimal amount) {
        addToDepositBalance(gameId, amount.setScale(2, RoundingMode.DOWN));
    }

    @Override
    public void notifyUserUpdate(String gameId) {
        userRepository.findByGameId(gameId).ifPresent(u -> {
            sendProfile(u);
            sendEvent(EVENT_PROFILE_UPDATED, gameId, toDto(u));
        });
    }

    // ================== Credit Balance (for MatchService) ==================
    @Transactional(rollbackFor = Exception.class)
    public void creditBalance(User user, BigDecimal amount) {
        if (user == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        addToDepositBalance(user.getGameId(), amount.setScale(2, RoundingMode.DOWN));
        addToLifetimeEarnings(user.getGameId(), amount.setScale(2, RoundingMode.DOWN));
    }

    // ===== Helpers =====
    @Override
    public Optional<User> findByMobile(String mobile) { return userRepository.findByMobile(mobile); }

    @Override
    public Optional<User> findByGameId(String gameId) { return userRepository.findByGameId(gameId); }

    @Override
    public Optional<User> findByReferralCode(String referralCode) { return userRepository.findByReferralCode(referralCode); }

    @Override
    public Optional<String> findGameIdByReferralCode(String referralCode) { return userRepository.findByReferralCode(referralCode).map(User::getGameId); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User save(User user) { return userRepository.saveAndFlush(user); }

    @Override
    public BigDecimal getLifetimeEarnings(String gameId) {
        return userRepository.findByGameId(gameId)
                .map(User::getLifetimeEarnings)
                .orElse(bd("0.00"));
    }
}
