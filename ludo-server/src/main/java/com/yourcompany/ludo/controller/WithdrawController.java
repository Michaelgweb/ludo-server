package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.WithdrawRequestDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.service.WithdrawService;
import com.yourcompany.ludo.service.impl.OtpServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    // ✅ Admin - সব withdraw রিকোয়েস্ট লিস্ট
    @GetMapping("/all")
    public ResponseEntity<?> getAllWithdraws(Authentication auth) {
        String gameId = auth.getName();
        User user = userService.findByGameId(gameId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (!"ADMIN".equalsIgnoreCase(user.getRole().name())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        List<WithdrawRequest> list = withdrawService.getAll();
        return ResponseEntity.ok(
                list.stream().map(WithdrawRequestDto::fromEntity).collect(Collectors.toList())
        );
    }

    // ✅ New - Admin only: All history for withdraw
    @GetMapping("/all-history")
    public ResponseEntity<?> getAllHistory(Authentication auth) {
        String gameId = auth.getName();
        User adminUser = userService.findByGameId(gameId).orElse(null);
        if (adminUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        if (!"ADMIN".equalsIgnoreCase(adminUser.getRole().name())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        List<WithdrawRequest> list = withdrawService.getAll();
        return ResponseEntity.ok(
                list.stream().map(WithdrawRequestDto::fromEntity).collect(Collectors.toList())
        );
    }

    // ✅ Withdraw OTP পাঠানো
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendWithdrawOtp(Authentication auth) {
        String gameId = auth.getName();
        User user = userService.findByGameId(gameId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return otpService.sendWithdrawOtp(user.getMobile());
    }

    // ✅ Withdraw Request with OTP Verification & Balance Deduction
    @PostMapping("/request")
    public ResponseEntity<?> requestWithdraw(@RequestBody Map<String, Object> requestData, Authentication auth) {
        String gameId = auth.getName();
        User user = userService.findByGameId(gameId).orElse(null);
        if (user == null) return ResponseEntity.status(401).body("Unauthorized");

        double amount = Double.parseDouble(requestData.get("amount").toString());
        String method = requestData.get("method").toString();
        String receiverNumber = requestData.get("receiverNumber").toString();
        String otp = requestData.get("otp").toString();

        if (user.getBalance() < amount) {
            return ResponseEntity.badRequest().body("Insufficient balance");
        }

        Boolean otpValid = otpService.verifyWithdrawOtp(user.getMobile(), otp);
        if (!otpValid) {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }

        // ব্যালেন্স কাটো
        user.setBalance(user.getBalance() - amount);
        userService.save(user);

        WithdrawRequest withdrawRequest = new WithdrawRequest();
        withdrawRequest.setUser(user);
        withdrawRequest.setAmount(amount);
        withdrawRequest.setMethod(method);
        withdrawRequest.setReceiverNumber(receiverNumber);
        withdrawRequest.setStatus(WithdrawRequest.Status.PENDING);

        try {
            WithdrawRequest created = withdrawService.createWithdrawRequest(withdrawRequest);
            return ResponseEntity.ok(WithdrawRequestDto.fromEntity(created));
        } catch (Exception e) {
            // কোনো সমস্যা হলে ব্যালেন্স ফেরত দাও
            user.setBalance(user.getBalance() + amount);
            userService.save(user);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ User এর নিজের withdraw history
    @GetMapping("/history")
    public ResponseEntity<List<WithdrawRequestDto>> getMyWithdraws(Authentication auth) {
        String gameId = auth.getName();
        User user = userService.findByGameId(gameId).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        List<WithdrawRequest> list = withdrawService.getByUser(user);
        return ResponseEntity.ok(list.stream().map(WithdrawRequestDto::fromEntity).collect(Collectors.toList()));
    }

    // ✅ Lifetime Withdraw Total
    @GetMapping("/lifetime")
    public ResponseEntity<?> getLifetimeWithdrawTotal(Authentication auth) {
        String gameId = auth.getName();
        User user = userService.findByGameId(gameId).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        double total = withdrawService.getTotalWithdrawn(user);
        return ResponseEntity.ok(Map.of("total", total));
    }

    // ✅ Pending withdraws (Admin view)
    @GetMapping("/pending")
    public ResponseEntity<List<WithdrawRequestDto>> getPending(Authentication auth) {
        String gameId = auth.getName();
        User adminUser = userService.findByGameId(gameId).orElse(null);
        if (adminUser == null || !"ADMIN".equalsIgnoreCase(adminUser.getRole().name())) {
            return ResponseEntity.status(403).body(null);
        }
        List<WithdrawRequest> list = withdrawService.getPending();
        return ResponseEntity.ok(list.stream().map(WithdrawRequestDto::fromEntity).collect(Collectors.toList()));
    }

    // ✅ Approve withdraw with Lifetime Earnings Update
    @PostMapping("/approve/{withdrawId}")
    public ResponseEntity<?> approveWithdraw(@PathVariable Long withdrawId,
                                             @RequestBody(required = false) Map<String, Object> req,
                                             Authentication auth) {
        String gameId = auth.getName();
        User adminUser = userService.findByGameId(gameId).orElse(null);
        if (adminUser == null || !"ADMIN".equalsIgnoreCase(adminUser.getRole().name())) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        String txnId = (req != null && req.get("transactionId") != null && !req.get("transactionId").toString().trim().isEmpty())
                ? req.get("transactionId").toString().trim()
                : null;

        try {
            WithdrawRequest approved = withdrawService.approve(withdrawId, txnId);
            return ResponseEntity.ok(WithdrawRequestDto.fromEntity(approved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ Reject withdraw with refund
    @PostMapping("/reject/{withdrawId}")
    public ResponseEntity<?> rejectWithdraw(@PathVariable Long withdrawId,
                                            @RequestBody(required = false) Map<String, Object> req,
                                            Authentication auth) {
        String gameId = auth.getName();
        User adminUser = userService.findByGameId(gameId).orElse(null);
        if (adminUser == null || !"ADMIN".equalsIgnoreCase(adminUser.getRole().name())) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        String txnId = (req != null && req.get("transactionId") != null) ? req.get("transactionId").toString().trim() : "";

        try {
            WithdrawRequest rejected = withdrawService.reject(withdrawId, txnId);
            return ResponseEntity.ok(WithdrawRequestDto.fromEntity(rejected));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}