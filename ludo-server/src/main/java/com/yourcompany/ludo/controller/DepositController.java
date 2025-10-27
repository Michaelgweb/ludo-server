package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.DepositRequestDto;
import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.PaymentConfig;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.DepositService;
import com.yourcompany.ludo.service.PaymentConfigService;
import com.yourcompany.ludo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    // ✅ Create Deposit Request + Send Payment Number
    @PostMapping("/request")
    public ResponseEntity<?> createDepositRequest(@RequestBody DepositRequest depositRequest, Authentication authentication) {
        try {
            String gameId = authentication.getName();
            User user = userService.findByGameId(gameId).orElse(null);
            if (user == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            String method = depositRequest.getMethod();
            PaymentConfig config = paymentConfigService.getConfig(method).orElse(null);
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Payment method not available"));
            }

            depositRequest.setUser(user);

            // ✅ Save deposit request
            DepositRequest savedRequest = depositService.createDepositRequest(depositRequest);

            return ResponseEntity.ok(Map.of(
                    "deposit", DepositRequestDto.fromEntity(savedRequest),
                    "paymentNumber", config.getNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ✅ Admin - See Pending Requests
    @GetMapping("/pending")
    public ResponseEntity<List<DepositRequestDto>> getPendingDeposits() {
        List<DepositRequest> pendingRequests = depositService.getPendingDeposits();
        return ResponseEntity.ok(
                pendingRequests.stream()
                        .map(DepositRequestDto::fromEntity)
                        .collect(Collectors.toList())
        );
    }

    // ✅ User - See Own History
    @GetMapping("/my-history")
    public ResponseEntity<List<DepositRequestDto>> getMyDepositHistory(Authentication authentication) {
        String gameId = authentication.getName();
        User user = userService.findByGameId(gameId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        List<DepositRequest> userDeposits = depositService.getDepositsByUser(user);
        return ResponseEntity.ok(
                userDeposits.stream()
                        .map(DepositRequestDto::fromEntity)
                        .collect(Collectors.toList())
        );
    }

    // ✅ Admin - See All History (Null-Safe)
    @GetMapping("/all-history")
    public ResponseEntity<?> getAllDepositHistory() {
        try {
            List<DepositRequest> allDeposits = depositService.getAllDeposits();

            List<DepositRequestDto> dtos = allDeposits.stream()
                    .map(DepositRequestDto::fromEntity)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to load deposit history",
                    "details", e.getMessage()
            ));
        }
    }

    // ✅ Approve Request
    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approveDeposit(@PathVariable Long id) {
        try {
            DepositRequest approved = depositService.approveDeposit(id);
            return ResponseEntity.ok(DepositRequestDto.fromEntity(approved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ Reject Request
    @PostMapping("/reject/{id}")
    public ResponseEntity<?> rejectDeposit(@PathVariable Long id) {
        try {
            DepositRequest rejected = depositService.rejectDeposit(id);
            return ResponseEntity.ok(DepositRequestDto.fromEntity(rejected));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ Admin - Update Payment Number
    @PostMapping("/payment-config")
    public ResponseEntity<?> updatePaymentConfig(@RequestParam String method, @RequestParam String number) {
        PaymentConfig updated = paymentConfigService.saveOrUpdate(method, number);
        return ResponseEntity.ok(updated);
    }

    // ✅ Public - Get Payment Numbers
    @GetMapping("/payment-config")
    public ResponseEntity<?> getPaymentConfigs() {
        return ResponseEntity.ok(paymentConfigService.getAllConfigs());
    }
}