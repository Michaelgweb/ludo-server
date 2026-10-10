package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.WithdrawRequestDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.service.NotificationService;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.service.WithdrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/withdraw")
public class WithdrawController {

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ভুল লগইন পাসওয়ার্ড দিয়ে অনুমান করা ঠেকাতে: ৫ বার ভুল হলে ১৫ মিনিট উইথড্র বন্ধ।
    // মেমোরিতে রাখা হয় (সার্ভার রিস্টার্টে রিসেট হয়, একাধিক সার্ভার চললে প্রতিটির নিজস্ব গণনা)
    private static final int MAX_PASSWORD_ATTEMPTS = 5;
    private static final long PASSWORD_LOCK_MILLIS = 15 * 60 * 1000L;
    private final Map<String, long[]> passwordAttempts = new ConcurrentHashMap<>(); // [ভুলের সংখ্যা, লক শেষের সময়]

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole().name());
    }

    /** লক চললে কত মিনিট বাকি (না থাকলে ০) */
    private long passwordLockMinutesLeft(String gameId) {
        long[] st = passwordAttempts.get(gameId);
        if (st == null) return 0;
        long left = st[1] - System.currentTimeMillis();
        return left > 0 ? (left + 59_999) / 60_000 : 0;
    }

    private void recordWrongPassword(String gameId) {
        passwordAttempts.compute(gameId, (k, st) -> {
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

    /** ================== ADMIN ENDPOINTS ================== **/

    @GetMapping("/all")
    public ResponseEntity<?> getAllWithdraws(Authentication auth) {
        User user = userService.findByGameId(auth.getName()).orElse(null);
        if (!isAdmin(user)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        List<WithdrawRequest> list = withdrawService.getAll();
        return ResponseEntity.ok(list.stream()
                .map(WithdrawRequestDto::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/all-history")
    public ResponseEntity<?> getAllHistory(Authentication auth) {
        return getAllWithdraws(auth);
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPending(Authentication auth) {
        User adminUser = userService.findByGameId(auth.getName()).orElse(null);
        if (!isAdmin(adminUser)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));

        List<WithdrawRequest> list = withdrawService.getPending();
        return ResponseEntity.ok(list.stream()
                .map(WithdrawRequestDto::fromEntity)
                .collect(Collectors.toList()));
    }

    @PostMapping("/approve/{withdrawId}")
    public ResponseEntity<?> approveWithdraw(@PathVariable Long withdrawId,
                                             @RequestBody(required = false) Map<String, Object> req,
                                             Authentication auth) {
        User adminUser = userService.findByGameId(auth.getName()).orElse(null);
        if (!isAdmin(adminUser)) return ResponseEntity.status(403).body("Forbidden");

        String txnId = (req != null && req.get("transactionId") != null
                && !req.get("transactionId").toString().trim().isEmpty())
                ? req.get("transactionId").toString().trim()
                : null;

        try {
            WithdrawRequest approved = withdrawService.approve(withdrawId, txnId);

            notificationService.sendNotification(
                    approved.getUser().getId(),
                    "Withdrawal Approved",
                    "Your withdrawal of " + approved.getAmount() + " has been approved."
            );

            notificationService.sendWebSocketNotification(
                    approved.getUser().getId(),
                    "withdraw_update",
                    WithdrawRequestDto.fromEntity(approved)
            );

            return ResponseEntity.ok(WithdrawRequestDto.fromEntity(approved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/reject/{withdrawId}")
    public ResponseEntity<?> rejectWithdraw(@PathVariable Long withdrawId,
                                            @RequestBody(required = false) Map<String, Object> req,
                                            Authentication auth) {
        User adminUser = userService.findByGameId(auth.getName()).orElse(null);
        if (!isAdmin(adminUser)) return ResponseEntity.status(403).body("Forbidden");

        String txnId = (req != null && req.get("transactionId") != null)
                ? req.get("transactionId").toString().trim()
                : "";

        try {
            // ব্যালেন্স ফেরত সার্ভিসের ভেতরেই হয়
            WithdrawRequest rejected = withdrawService.reject(withdrawId, txnId);

            notificationService.sendNotification(
                    rejected.getUser().getId(),
                    "Withdrawal Rejected",
                    "Your withdrawal request of " + rejected.getAmount() + " was rejected."
            );

            notificationService.sendWebSocketNotification(
                    rejected.getUser().getId(),
                    "withdraw_update",
                    WithdrawRequestDto.fromEntity(rejected)
            );

            return ResponseEntity.ok(WithdrawRequestDto.fromEntity(rejected));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** ================== USER ENDPOINTS ================== **/

    @PostMapping("/request")
    public ResponseEntity<?> requestWithdraw(@RequestBody Map<String, Object> requestData,
                                             Authentication auth) {
        User user = userService.findByGameId(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        Object a = requestData.get("amount");
        Object m = requestData.get("method");
        Object r = requestData.get("receiverNumber");
        Object p = requestData.get("password");
        if (a == null || m == null || r == null) {
            return ResponseEntity.badRequest().body("amount, method ও receiverNumber দিন");
        }
        if (p == null || p.toString().isEmpty()) {
            return ResponseEntity.badRequest().body("আপনার লগইন পাসওয়ার্ড দিন");
        }
        String password = p.toString();   // trim করা হয় না, পাসওয়ার্ডে স্পেস থাকতে পারে

        BigDecimal amount;
        try {
            amount = new BigDecimal(a.toString().trim()).setScale(2, RoundingMode.DOWN);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid amount format");
        }
        if (amount.signum() <= 0) {
            return ResponseEntity.badRequest().body("Amount must be greater than 0");
        }

        String method = m.toString().trim();
        String receiver = r.toString().trim();

        if (method.isEmpty() || method.length() > 20) {
            return ResponseEntity.badRequest().body("Invalid method");
        }
        if (!receiver.matches("^\\+?[0-9]{10,15}$")) {
            return ResponseEntity.badRequest().body("Invalid receiver number");
        }

        // সস্তা আগাম চেক। আসল চেক সার্ভিসে লকের ভেতরে হয়
        BigDecimal turnoverLeft = userService.getTurnoverBalance(user.getGameId());
        if (turnoverLeft.signum() > 0) {
            return ResponseEntity.badRequest()
                    .body("উইথড্র করতে আরও " + turnoverLeft.toPlainString() + " টাকার টার্নওভার বাকি");
        }
        if (userService.getBalance(user.getGameId()).compareTo(amount) < 0) {
            return ResponseEntity.badRequest().body("ব্যালেন্স অপর্যাপ্ত");
        }

        // লগইন পাসওয়ার্ড যাচাই (সাবমিটের একমাত্র নিরাপত্তা ধাপ)
        long lockMinutes = passwordLockMinutesLeft(user.getGameId());
        if (lockMinutes > 0) {
            return ResponseEntity.status(429)
                    .body("অনেকবার ভুল পাসওয়ার্ড দিয়েছেন। " + lockMinutes + " মিনিট পরে চেষ্টা করুন");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordWrongPassword(user.getGameId());
            return ResponseEntity.badRequest().body("পাসওয়ার্ড ভুল");
        }
        passwordAttempts.remove(user.getGameId());

        try {
            // লক + টার্নওভার চেক + ব্যালেন্স কাটা + রিকোয়েস্ট তৈরি + টেলিগ্রাম, সবই সার্ভিসে
            WithdrawRequest created =
                    withdrawService.requestWithdraw(user.getGameId(), amount, method, receiver);

            notificationService.sendWebSocketNotification(
                    user.getId(),
                    "withdraw_update",
                    WithdrawRequestDto.fromEntity(created)
            );

            return ResponseEntity.ok(WithdrawRequestDto.fromEntity(created));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<WithdrawRequestDto>> getMyWithdraws(Authentication auth) {
        User user = userService.findByGameId(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        List<WithdrawRequest> list = withdrawService.getByUser(user);
        return ResponseEntity.ok(list.stream()
                .map(WithdrawRequestDto::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/lifetime")
    public ResponseEntity<?> getLifetimeWithdrawTotal(Authentication auth) {
        User user = userService.findByGameId(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        BigDecimal total = withdrawService.getTotalWithdrawn(user);
        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        return ResponseEntity.ok(response);
    }
}
