package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.LoginRequest;
import com.yourcompany.ludo.dto.UserDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // ================== Login ==================
    // UserServiceImpl.toDto() এ balance, turnoverBalance, withdrawableBalance,
    // lifetimeEarnings, lifetimeWithdraw সব সেট করা আছে।
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        UserDto user = userService.login(req.getMobile(), req.getPassword());
        String token = jwtUtil.generateToken(user.getGameId(), user.getRole());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("token", token);
        res.put("user", user);
        return res;
    }

    // ================== Signup ==================
    // সাইনআপ বোনাস (ব্যালেন্স + টার্নওভার) ও রেফারাল রেকর্ড registerWithReferral-এর ভেতরেই হয়।
    // এখানে আর কোনো টাকা যোগ হবে না।
    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody UserDto req) {
        // req.getReferredBy() এখানে আসলে রেফারাল কোড
        User u = userService.registerWithReferral(
                req.getMobile(), req.getPassword(), req.getReferredBy());

        String token = jwtUtil.generateToken(u.getGameId(), u.getRole().name());

        UserDto responseUser = new UserDto.Builder()
                .id(u.getId())
                .mobile(u.getMobile())
                .gameId(u.getGameId())
                .balance(u.getBalance())
                .turnoverBalance(u.getTurnoverBalance())
                .withdrawableBalance(u.getWithdrawableBalance())
                .lifetimeEarnings(u.getLifetimeEarnings())
                .lifetimeWithdraw(u.getLifetimeWithdraw())
                .role(u.getRole().name())
                .avatarUrl(u.getAvatarUrl())
                .displayName(u.getDisplayName())
                .referralCode(u.getReferralCode())
                .referredBy(u.getReferredBy())
                .signupBonusClaimed(u.isSignupBonusClaimed())
                .build();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Signup successful");
        res.put("token", token);
        res.put("user", responseUser);
        return res;
    }

    // /first-deposit সরানো হয়েছে: ডিপোজিট এখন শুধু DepositController (/api/deposit/**)
    // দিয়ে, অ্যাডমিন অ্যাপ্রুভের পর।
}
