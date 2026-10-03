package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.service.PaymentRotationService;
import com.yourcompany.ludo.service.PaymentRotationService.AccountDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/payment-accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentAccountController {

    public record AddAccountRequest(String method, String number) {}
    public record ActiveRequest(boolean active) {}

    private final PaymentRotationService service;

    public AdminPaymentAccountController(PaymentRotationService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountDto> all() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto add(@RequestBody AddAccountRequest req) {
        return service.add(req.method(), req.number());
    }

    @PatchMapping("/{id}/active")
    public AccountDto setActive(@PathVariable Long id, @RequestBody ActiveRequest req) {
        return service.setActive(id, req.active());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("success", true);
    }
}
