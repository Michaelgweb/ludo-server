package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.PaymentConfig;
import com.yourcompany.ludo.service.PaymentConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment-config")
public class PaymentConfigController {

    @Autowired
    private PaymentConfigService service;

    /**
     * ✅ Public API: Get all payment configs
     * Used for deposit page showing bKash / Nagad numbers
     */
    @GetMapping
    public ResponseEntity<List<PaymentConfig>> getAllConfigs() {
        return ResponseEntity.ok(service.getAllConfigs());
    }

    /**
     * ✅ Public API: Get specific method config
     */
    @GetMapping("/{method}")
    public ResponseEntity<?> getConfig(@PathVariable String method) {
        return service.getConfig(method)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * ✅ Admin API: Update or create payment config
     * Only users with ADMIN role can call this.
     */
    @PostMapping("/admin/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateConfig(
            @RequestParam String method,
            @RequestParam String number
    ) {
        PaymentConfig updated = service.saveOrUpdate(method, number);
        return ResponseEntity.ok(updated);
    }
}