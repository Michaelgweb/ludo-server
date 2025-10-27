package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.LoginRequest;
import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final BigDecimal SIGNUP_BONUS = new BigDecimal("20.00");
    private static final BigDecimal REFERRAL_BONUS = new BigDecimal("30.00");

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    // ================== Login ==================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest loginRequest) {
        UserDto user = userService.login(loginRequest.getMobile(), loginRequest.getPassword());

        String role = user.getRole();
        String token = jwtUtil.generateToken(user.getGameId(), role);

        // Generate referral code if empty
        if (user.getReferralCode() == null || user.getReferralCode().isEmpty()) {
            String newCode = userService.generateUniqueReferralCode();
            userService.findByGameId(user.getGameId()).ifPresent(u -> {
                u.setReferralCode(newCode);
                userService.save(u);
            });
            user.setReferralCode(newCode);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", user);
        return response;
    }

    // ================== Signup ==================
    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody UserDto newUserDto) {
        Map<String, Object> response = new HashMap<>();

        // Register user with referral if any
        User newUser = userService.registerWithReferral(
                newUserDto.getMobile(),
                newUserDto.getPassword(),
                newUserDto.getReferredBy()
        );

        // Apply signup bonus
        if (!newUser.isSignupBonusClaimed()) {
            userService.addBalance(newUser.getGameId(), SIGNUP_BONUS);
            newUser.setSignupBonusClaimed(true);
        }

        // Apply referral bonus to referrer
        if (newUser.getReferredBy() != null && !newUser.isReferralBonusClaimed()) {
            userService.findByReferralCode(newUser.getReferredBy()).ifPresent(referrer -> {
                if (!referrer.isReferrerBonusGiven()) {
                    userService.addBalance(referrer.getGameId(), REFERRAL_BONUS);
                    referrer.setReferrerBonusGiven(true);
                    userService.save(referrer);
                }
            });
            newUser.setReferralBonusClaimed(true);
        }

        userService.save(newUser);

        String token = jwtUtil.generateToken(newUser.getGameId(), newUser.getRole().name());

        UserDto responseUser = new UserDto.Builder()
                .id(newUser.getId())
                .mobile(newUser.getMobile())
                .password(null) // never return password
                .gameId(newUser.getGameId())
                .balance(newUser.getBalance())
                .role(newUser.getRole().name())
                .displayName(newUser.getDisplayName())
                .referralCode(newUser.getReferralCode())
                .referredBy(newUser.getReferredBy())
                .signupBonusClaimed(newUser.isSignupBonusClaimed())
                .build();

        response.put("message", "Signup successful, bonus credited");
        response.put("token", token);
        response.put("user", responseUser);

        return response;
    }

    // ================== First Deposit ==================
    @PostMapping("/first-deposit/{gameId}")
    public Map<String, Object> firstDeposit(@PathVariable String gameId, @RequestParam BigDecimal amount) {
        Map<String, Object> response = new HashMap<>();

        Optional<User> userOpt = userService.findByGameId(gameId);
        if (userOpt.isEmpty()) {
            response.put("error", "User not found");
            return response;
        }

        User user = userOpt.get();

        if (!user.isFirstDepositBonusGiven()) {
            userService.addBalance(gameId, amount);
            user.setFirstDepositBonusGiven(true);

            // Apply referral bonus to referrer if applicable
            if (user.getReferredBy() != null && !user.isReferralBonusClaimed()) {
                userService.findByReferralCode(user.getReferredBy()).ifPresent(referrer -> {
                    if (!referrer.isReferrerBonusGiven()) {
                        userService.addBalance(referrer.getGameId(), REFERRAL_BONUS);
                        referrer.setReferrerBonusGiven(true);
                        userService.save(referrer);
                    }
                });
                user.setReferralBonusClaimed(true);
            }

            userService.save(user);
            response.put("message", "First deposit successful");
        } else {
            userService.addBalance(gameId, amount);
            response.put("message", "Deposit successful");
        }

        response.put("balance", userService.findByGameId(gameId).get().getBalance());
        return response;
    }
}
