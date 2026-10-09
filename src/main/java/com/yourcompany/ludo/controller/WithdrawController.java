package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.WithdrawRequestDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.service.NotificationService;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.service.WithdrawService;
import com.yourcompany.ludo.service.impl.OtpServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/withdraw")
public class WithdrawController {

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private UserService userService;

    @Autowired
    private OtpServiceImpl otpService;

    @Autowired
    private NotificationService notificationService;

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole().name());
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

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendWithdrawOtp(Authentication auth) {
        User user = userService.findByGameId(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return otpService.sendWithdrawOtp(user.getMobile());
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestWithdraw(@RequestBody Map<String, Object> requestData,
                                             Authentication auth) {
        User user = userService.findByGameId(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        Object a = requestData.get("amount");
        Object m = requestData.get("method");
        Object r = requestData.get("receiverNumber");
        Object o = requestData.get("otp");
        if (a == null || m == null || r == null || o == null) {
            return ResponseEntity.badRequest().body("amount, method, receiverNumber ও otp দিন");
        }

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
        String otp = o.toString().trim();

        if (method.isEmpty() || method.length() > 20) {
            return ResponseEntity.badRequest().body("Invalid method");
        }
        if (!receiver.matches("^\\+?[0-9]{10,15}$")) {
            return ResponseEntity.badRequest().body("Invalid receiver number");
        }

        // সস্তা আগাম চেক (OTP অকারণে খরচ না করতে)। আসল চেক সার্ভিসে লকের ভেতরে হয়
        if (userService.getWithdrawBalance(user.getGameId()).compareTo(amount) < 0) {
            return ResponseEntity.badRequest().body("Turnover incomplete or insufficient balance");
        }

        if (!otpService.verifyWithdrawOtp(user.getMobile(), otp)) {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }

        try {
            // লক + টার্নওভার চেক + ব্যালেন্স কাটা + রিকোয়েস্ট তৈরি, সবই এক ট্রানজেকশনে
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
