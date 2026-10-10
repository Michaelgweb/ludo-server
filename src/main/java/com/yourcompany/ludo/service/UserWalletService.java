package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.service.UserWalletService;
import com.yourcompany.ludo.service.UserWalletService.WalletDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/wallets")
public class UserWalletController {

    // ভুল পাসওয়ার্ড দিয়ে অনুমান ঠেকাতে: ৫ বার ভুল হলে ১৫ মিনিট নম্বর অ্যাড বন্ধ।
    // মেমোরিতে রাখা হয় (সার্ভার রিস্টার্টে রিসেট হয়)
    private static final int MAX_PASSWORD_ATTEMPTS = 5;
    private static final long PASSWORD_LOCK_MILLIS = 15 * 60 * 1000L;
    private final Map<Long, long[]> passwordAttempts = new ConcurrentHashMap<>(); // [ভুলের সংখ্যা, লক শেষের সময়]

    // একই ইউজারের দুই রিকোয়েস্ট একসাথে এলেও সীমা পার না হওয়ার জন্য
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();

    public record AddWalletRequest(String method, String number, String password) {}

    private final UserWalletService service;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserWalletController(UserWalletService service,
                                UserService userService,
                                PasswordEncoder passwordEncoder) {
        this.service = service;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------- helpers ----------------

    /** লক চললে কত মিনিট বাকি (না থাকলে ০) */
    private long lockMinutesLeft(Long userId) {
        long[] st = passwordAttempts.get(userId);
        if (st == null) return 0;
        long left = st[1] - System.currentTimeMillis();
        return left > 0 ? (left + 59_999) / 60_000 : 0;
    }

    private void recordWrongPassword(Long userId) {
        passwordAttempts.compute(userId, (k, st) -> {
            long now = System.currentTimeMillis();
            if (st == null || (st[1] != 0 && st[1] <= now)) st = new long[]{0, 0};
            st[0]++;
            if (st[0] >= MAX_PASSWORD_ATTEMPTS) {
                st[1] = now + PASSWORD_LOCK_MILLIS;
                st[0] = 0;
            }
            return st;
        });
    }

    /** ডাটাবেস থেকে সর্বশেষ ইউজার এনে পাসওয়ার্ড মেলানো */
    private void verifyPassword(User principal, String password) {
        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "আপনার লগইন পাসওয়ার্ড দিন");
        }

        long lockMinutes = lockMinutesLeft(principal.getId());
        if (lockMinutes > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "অনেকবার ভুল পাসওয়ার্ড দিয়েছেন। " + lockMinutes + " মিনিট পরে চেষ্টা করুন");
        }

        User fresh = userService.findByGameId(principal.getGameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        if (!passwordEncoder.matches(password, fresh.getPassword())) {
            recordWrongPassword(principal.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "পাসওয়ার্ড ভুল");
        }
        passwordAttempts.remove(principal.getId());
    }

    // ---------------- endpoints ----------------

    @GetMapping
    public List<WalletDto> myWallets(@AuthenticationPrincipal User user) {
        return service.list(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletDto add(@AuthenticationPrincipal User user, @RequestBody AddWalletRequest req) {
        // ১. পাসওয়ার্ড যাচাই
        verifyPassword(user, req.password());

        // ২. সর্বোচ্চ ৫টার সীমা UserWalletService.add() এ চেক হয়।
        //    একই ইউজারের দুই রিকোয়েস্ট একসাথে এলে যেন সীমা পার না হয়, তাই ইউজার-ভিত্তিক লক
        synchronized (userLocks.computeIfAbsent(user.getId(), k -> new Object())) {
            return service.add(user.getId(), req.method(), req.number());
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user.getId(), id);
        return Map.of("success", true);
    }
}
