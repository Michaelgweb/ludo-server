package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.PaymentConfig;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.PaymentConfigService;
import com.yourcompany.ludo.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment-config")
public class PaymentConfigController {

    @Autowired
    private PaymentConfigService service;

    @Autowired
    private UserService userService;

    // =====================================
    // ADMIN CHECK FROM JWT
    // =====================================
    private User getAdmin(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Unauthorized");
        }

        User user = userService
                .findByGameId(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("Only ADMIN allowed");
        }

        return user;
    }

    // =====================================
    // PUBLIC - GET ALL PAYMENT CONFIG
    // =====================================
    @GetMapping
    public ResponseEntity<List<PaymentConfig>> getAllConfigs() {
        return ResponseEntity.ok(service.getAllConfigs());
    }

    // =====================================
    // PUBLIC - GET BY METHOD
    // =====================================
    @GetMapping("/{method}")
    public ResponseEntity<?> getConfig(@PathVariable String method) {
        Optional<PaymentConfig> config = service.getConfig(method);

        if (config.isPresent()) {
            return ResponseEntity.ok(config.get());
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", "Payment method not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // =====================================
    // CREATE OR UPDATE PAYMENT CONFIG
    // ADMIN ONLY
    // =====================================
    @PostMapping("/update")
    public ResponseEntity<?> updateConfig(
            @RequestParam String method,
            @RequestParam String number,
            Authentication authentication) {
        try {
            getAdmin(authentication);

            PaymentConfig updated = service.saveOrUpdate(method, number);
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
    }

    // =====================================
    // UPDATE ONLY NUMBER
    // ADMIN ONLY
    // =====================================
    @PatchMapping("/update-number")
    public ResponseEntity<?> updateNumber(
            @RequestParam String method,
            @RequestParam String number,
            Authentication authentication) {
        try {
            getAdmin(authentication);

            PaymentConfig updated = service.updateNumber(method, number);
            return ResponseEntity.ok(updated);

        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    // =====================================
    // DELETE PAYMENT CONFIG
    // ADMIN ONLY
    // =====================================
    @DeleteMapping("/{method}")
    public ResponseEntity<?> deleteConfig(
            @PathVariable String method,
            Authentication authentication) {
        try {
            getAdmin(authentication);

            service.deleteConfig(method);

            Map<String, String> success = new HashMap<>();
            success.put("message", "Payment method deleted successfully");
            return ResponseEntity.ok(success);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
    }
}
