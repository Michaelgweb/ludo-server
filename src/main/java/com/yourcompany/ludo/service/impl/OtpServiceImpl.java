package com.yourcompany.ludo.service.impl;

import com.yourcompany.ludo.dto.OtpVerifyRequest;
import com.yourcompany.ludo.dto.OtpPasswordResetRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.service.OtpService;
import com.yourcompany.ludo.service.UserServiceImpl;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class OtpServiceImpl implements OtpService {

    // SMS Bangladesh credentials
    private static final String API_USER = "mdbasarullislam917@gmail.com";
    private static final String API_PASS = "zks$YfYD4R3zy4g8";

    private static final long WITHDRAW_COOLDOWN_SECONDS = 600; // 10 minute cooldown

    private final Map<String, String> otpStore = new HashMap<>();
    private final Map<String, LocalDateTime> otpCooldownMap = new HashMap<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserServiceImpl userService; // UserService call for registration + bonuses

    // ===== Registration OTP =====
    @Override
    public ResponseEntity<?> sendOtpToPhone(String mobile) {
        return sendOtpInternal(normalizeMobile(mobile), "registration");
    }

    // ===== Withdraw OTP with cooldown =====
    public ResponseEntity<?> sendWithdrawOtp(String mobile) {
        mobile = normalizeMobile(mobile);

        if (otpCooldownMap.containsKey(mobile)) {
            LocalDateTime lastSent = otpCooldownMap.get(mobile);
            LocalDateTime nextAllowed = lastSent.plusSeconds(WITHDRAW_COOLDOWN_SECONDS);
            if (nextAllowed.isAfter(LocalDateTime.now())) {
                long remainingSeconds = Duration.between(LocalDateTime.now(), nextAllowed).getSeconds();
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error", "Please wait before requesting a new OTP.",
                                "remainingSeconds", remainingSeconds
                        ));
            }
        }

        return sendOtpInternal(mobile, "withdraw");
    }

    // ===== Internal OTP Send =====
    private ResponseEntity<?> sendOtpInternal(String mobile, String type) {
        String otp = generateOtp();
        otpStore.put(mobile, otp);
        otpCooldownMap.put(mobile, LocalDateTime.now()); // start cooldown

        // Keep colon (:) unencoded in SMS
        String rawMessage = "your onetime password code: " + otp;
        String encodedMessage = URLEncoder.encode(rawMessage, StandardCharsets.UTF_8)
                .replace("%3A", ":");

        String url = "https://panel.smsbangladesh.com/otp"
                + "?user=" + API_USER
                + "&password=" + API_PASS
                + "&to=" + mobile
                + "&text=" + encodedMessage;

        try {
            String response = new RestTemplate().getForObject(url, String.class);
            return ResponseEntity.ok(Map.of(
                    "message", "OTP sent successfully",
                    "type", type,
                    "apiResponse", response,
                    "remainingSeconds", WITHDRAW_COOLDOWN_SECONDS
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to send OTP", "details", e.getMessage()));
        }
    }

    // ===== Verify OTP for Withdraw =====
    public boolean verifyWithdrawOtp(String mobile, String otp) {
        mobile = normalizeMobile(mobile);
        String savedOtp = otpStore.get(mobile);
        if (savedOtp != null && savedOtp.equals(otp)) {
            otpStore.remove(mobile);
            return true;
        }
        return false;
    }

    // ===== Verify OTP & Register User with Referral =====
    @Override
    public ResponseEntity<?> verifyOtpAndRegister(OtpVerifyRequest request) {
        String mobile = normalizeMobile(request.getMobile());
        String otp = otpStore.get(mobile);

        if (otp == null || !otp.equals(request.getOtp())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or expired OTP"));
        }

        if (userRepository.findByMobile(mobile).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User already exists"));
        }

        // ===== Register user via UserServiceImpl =====
        User newUser = userService.registerWithReferral(
                mobile,
                request.getPassword(),
                request.getReferralCode() // Referral code sent from client
        );

        otpStore.remove(mobile);

        // Generate JWT token
        String token = jwtUtil.generateToken(newUser.getGameId(), newUser.getRole().name());

        return ResponseEntity.ok(Map.of(
                "message", "User registered successfully",
                "token", token,
                "gameId", newUser.getGameId()
        ));
    }

    // ===== Reset Password via OTP =====
    @Override
    public ResponseEntity<?> resetPasswordWithOtp(OtpPasswordResetRequest request) {
        String mobile = normalizeMobile(request.getMobile());
        String otp = otpStore.get(mobile);

        if (otp == null || !otp.equals(request.getOtp())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or expired OTP"));
        }

        Optional<User> optionalUser = userRepository.findByMobile(mobile);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User not found"));
        }

        User user = optionalUser.get();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        otpStore.remove(mobile);

        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    // ===== Helper Methods =====
    private String generateOtp() {
        return String.valueOf(new Random().nextInt(900000) + 100000);
    }

    private String generateUniqueGameId() {
        Random random = new Random();
        String id;
        do {
            id = String.format("%012d", Math.abs(random.nextLong()) % 1_000_000_000_000L);
        } while (userRepository.findByGameId(id).isPresent());
        return id;
    }

    private String normalizeMobile(String mobile) {
        if (!mobile.startsWith("880")) {
            return "880" + mobile.replaceFirst("^0+", "");
        }
        return mobile;
    }
}