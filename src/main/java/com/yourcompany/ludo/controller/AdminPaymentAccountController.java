package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.service.PaymentRotationService;
import com.yourcompany.ludo.service.PaymentRotationService.AccountDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** অ্যাডমিন শুধু: ডিপোজিটের জন্য পেমেন্ট নম্বর ম্যানেজ */
@RestController
@RequestMapping("/api/admin/payment-accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentAccountController {

    public record AddRequest(String method, String number) {}
    public record ActiveRequest(boolean active) {}

    private final PaymentRotationService service;

    public AdminPaymentAccountController(PaymentRotationService service) {
        this.service = service;
    }

    /** সব নম্বর (method অনুযায়ী সাজানো) */
    @GetMapping
    public List<AccountDto> list() {
        return service.listAll();
    }

    /** নতুন নম্বর যোগ */
    @PostMapping
    public AccountDto add(@RequestBody AddRequest req) {
        return service.add(req.method(), req.number());
    }

    /** চালু / বন্ধ */
    @PatchMapping("/{id}/active")
    public AccountDto setActive(@PathVariable Long id, @RequestBody ActiveRequest req) {
        return service.setActive(id, req.active());
    }

    /** মুছে ফেলা */
    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "মুছে ফেলা হয়েছে");
    }
}
