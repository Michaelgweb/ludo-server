// ==================== PaymentConfigController.java ====================
package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.PaymentConfig;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.PaymentConfigService;
import com.yourcompany.ludo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment-config")
public class PaymentConfigController {

    @Autowired
    private PaymentConfigService service;

    @Autowired
    private UserService userService;

    // ===== Helper method for ADMIN validation =====
    private ResponseEntity<String> validateAdmin(String gameId) {
        Optional<User> userOpt = userService.findByGameId(gameId);
        if (userOpt.isEmpty()) return ResponseEntity.status(403).body("❌ Invalid gameId");

        User user = userOpt.get();
        if (user.getRole() != User.Role.ADMIN) return ResponseEntity.status(403).body("❌ Only ADMIN allowed");
        return null; // means OK
    }

    /** Public API: Get all configs */
    @GetMapping
    public ResponseEntity<List<PaymentConfig>> getAllConfigs() {
        return ResponseEntity.ok(service.getAllConfigs());
    }

    /** Public API: Get config by method */
    @GetMapping("/{method}")
    public ResponseEntity<?> getConfig(@PathVariable String method) {
        return service.getConfig(method)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create or Update config (ADMIN only) */
    @PostMapping("/update-by-gameid")
    public ResponseEntity<?> updateConfigByGameId(
            @RequestParam String gameId,
            @RequestParam String method,
            @RequestParam String number
    ) {
        ResponseEntity<String> validation = validateAdmin(gameId);
        if (validation != null) return validation;

        PaymentConfig updated = service.saveOrUpdate(method, number);
        return ResponseEntity.ok(updated);
    }

    /** Update only number (ADMIN only) */
    @PatchMapping("/update-number")
    public ResponseEntity<?> updateNumberByGameId(
            @RequestParam String gameId,
            @RequestParam String method,
            @RequestParam String number
    ) {
        ResponseEntity<String> validation = validateAdmin(gameId);
        if (validation != null) return validation;

        try {
            PaymentConfig updated = service.updateNumber(method, number);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /** Delete config (ADMIN only) */
    @DeleteMapping("/{method}")
    public ResponseEntity<?> deleteConfig(
            @PathVariable String method,
            @RequestParam String gameId
    ) {
        ResponseEntity<String> validation = validateAdmin(gameId);
        if (validation != null) return validation;

        service.deleteConfig(method);
        return ResponseEntity.ok("✅ Payment method deleted");
    }
}
