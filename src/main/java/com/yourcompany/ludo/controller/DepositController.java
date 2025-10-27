package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.DepositRequestDto;
import com.yourcompany.ludo.dto.NotificationDto;
import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.PaymentConfig;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.DepositService;
import com.yourcompany.ludo.service.PaymentConfigService;
import com.yourcompany.ludo.service.UserService;
import com.yourcompany.ludo.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deposit")
public class DepositController {

    @Autowired
    private DepositService depositService;

    @Autowired
    private UserService userService;

    @Autowired
    private PaymentConfigService paymentConfigService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ================= Create Deposit Request =================
    @PostMapping("/request")
    public ResponseEntity<?> createDepositRequest(@RequestBody DepositRequest depositRequest, Authentication authentication) {
        try {
            String gameId = authentication.getName();
            User user = userService.findByGameId(gameId).orElse(null);
            if (user == null) return ResponseEntity.status(401).body("Unauthorized");

            PaymentConfig config = paymentConfigService.getConfig(depositRequest.getMethod()).orElse(null);
            if (config == null) return ResponseEntity.badRequest().body(Map.of("error", "Payment method not available"));

            depositRequest.setUser(user);
            DepositRequest savedRequest = depositService.createDepositRequest(depositRequest);

            return ResponseEntity.ok(Map.of(
                    "deposit", DepositRequestDto.fromEntity(savedRequest),
                    "paymentNumber", config.getNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ================= Pending Deposits =================
    @GetMapping("/pending")
    public ResponseEntity<List<DepositRequestDto>> getPendingDeposits() {
        List<DepositRequest> pendingRequests = depositService.getPendingDeposits();
        return ResponseEntity.ok(
                pendingRequests.stream()
                        .map(DepositRequestDto::fromEntity)
                        .collect(Collectors.toList())
        );
    }

    // ================= My Deposit History =================
    @GetMapping("/my-history")
    public ResponseEntity<List<DepositRequestDto>> getMyDepositHistory(Authentication authentication) {
        String gameId = authentication.getName();
        User user = userService.findByGameId(gameId).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        List<DepositRequest> userDeposits = depositService.getDepositsByUser(user);
        return ResponseEntity.ok(
                userDeposits.stream()
                        .map(DepositRequestDto::fromEntity)
                        .collect(Collectors.toList())
        );
    }

    // ================= All Deposit History =================
    @GetMapping("/all-history")
    public ResponseEntity<List<DepositRequestDto>> getAllDepositHistory() {
        List<DepositRequest> allDeposits = depositService.getAllDeposits();
        return ResponseEntity.ok(
                allDeposits.stream()
                        .map(DepositRequestDto::fromEntity)
                        .collect(Collectors.toList())
        );
    }

    // ================= Approve Deposit =================
    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approveDeposit(@PathVariable Long id) {
        try {
            DepositRequest approved = depositService.approveDeposit(id);

            // WebSocket notification to user
            sendUserNotification(approved.getUser(),
                    "Your deposit request of " + approved.getAmount() + " has been approved!");

            return ResponseEntity.ok(DepositRequestDto.fromEntity(approved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ================= Reject Deposit =================
    @PostMapping("/reject/{id}")
    public ResponseEntity<?> rejectDeposit(@PathVariable Long id) {
        try {
            DepositRequest rejected = depositService.rejectDeposit(id);

            // WebSocket notification to user
            sendUserNotification(rejected.getUser(),
                    "Your deposit request of " + rejected.getAmount() + " has been rejected!");

            return ResponseEntity.ok(DepositRequestDto.fromEntity(rejected));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ================= Update Payment Config =================
    @PostMapping("/payment-config")
    public ResponseEntity<?> updatePaymentConfig(@RequestParam String method, @RequestParam String number) {
        PaymentConfig updated = paymentConfigService.saveOrUpdate(method, number);
        return ResponseEntity.ok(updated);
    }

    // ================= Get All Payment Configs =================
    @GetMapping("/payment-config")
    public ResponseEntity<?> getPaymentConfigs() {
        return ResponseEntity.ok(paymentConfigService.getAllConfigs());
    }

    // ================= WebSocket Notification Helper =================
    private void sendUserNotification(User user, String message) {
        if (user == null) return;
        NotificationDto notification = new NotificationDto(
                "Deposit Update",
                message,
                LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/notifications/" + user.getGameId(), notification);
    }
}
