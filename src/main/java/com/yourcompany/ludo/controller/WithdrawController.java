package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.WithdrawRequestDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.service.WithdrawService;
import com.yourcompany.ludo.service.impl.OtpServiceImpl;
import com.yourcompany.ludo.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

        String txnId = (req != null && req.get("transactionId") != null && !req.get("transactionId").toString().trim().isEmpty())
                ? req.get("transactionId").toString().trim()
                : null;

        try {
            WithdrawRequest approved = withdrawService.approve(withdrawId, txnId);

            // Send Notification
            notificationService.sendNotification(
                    approved.getUser().getId(),
                    "Withdrawal Approved",
                    "Your withdrawal of " + approved.getAmount() + " has been approved."
            );

            // Send WebSocket update
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
            WithdrawRequest rejected = withdrawService.reject(withdrawId, txnId);

            // Refund balance automatically handled in service if needed

            // Send Notification
            notificationService.sendNotification(
                    rejected.getUser().getId(),
                    "Withdrawal Rejected",
                    "Your withdrawal request of " + rejected.getAmount() + " was rejected."
            );

            // Send WebSocket update
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

        BigDecimal amount;
        try {
            amount = new BigDecimal(requestData.get("amount").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid amount format");
        }

        String method = String.valueOf(requestData.get("method"));
        String receiverNumber = String.valueOf(requestData.get("receiverNumber"));
        String otp = String.valueOf(requestData.get("otp"));

        if (user.getBalance().compareTo(amount) < 0) {
            return ResponseEntity.badRequest().body("Insufficient balance");
        }

        if (!otpService.verifyWithdrawOtp(user.getMobile(), otp)) {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }

        // Deduct balance
        user.setBalance(user.getBalance().subtract(amount));
        userService.save(user);

        WithdrawRequest withdrawRequest = new WithdrawRequest();
        withdrawRequest.setUser(user);
        withdrawRequest.setAmount(amount);
        withdrawRequest.setMethod(method);
        withdrawRequest.setReceiverNumber(receiverNumber);
        withdrawRequest.setStatus(WithdrawRequest.Status.PENDING);

        try {
            WithdrawRequest created = withdrawService.createWithdrawRequest(withdrawRequest);

            // Send WebSocket update for new request
            notificationService.sendWebSocketNotification(
                    user.getId(),
                    "withdraw_update",
                    WithdrawRequestDto.fromEntity(created)
            );

            return ResponseEntity.ok(WithdrawRequestDto.fromEntity(created));
        } catch (Exception e) {
            // Rollback balance if failed
            user.setBalance(user.getBalance().add(amount));
            userService.save(user);
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
