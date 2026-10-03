package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.service.DepositService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** মোবাইলের SMS-forwarder admin token দিয়ে এই endpoint-এ POST করবে */
@RestController
@RequestMapping("/api/payment-sms")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentSmsController {

    public record IncomingSms(String sender, String message) {}

    private final DepositService depositService;

    public PaymentSmsController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/incoming")
    public Map<String, Object> incoming(@RequestBody IncomingSms body) {
        return Map.of("result", depositService.ingestSms(body.sender(), body.message()).name());
    }
}
