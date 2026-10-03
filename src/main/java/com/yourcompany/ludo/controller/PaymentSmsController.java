package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.service.DepositService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** মোবাইলের SMS-forwarder এই endpoint-এ POST করবে */
@RestController
@RequestMapping("/api/payment-sms")
public class PaymentSmsController {

    public record IncomingSms(String sender, String message) {}

    private final DepositService depositService;

    @Value("${app.sms.secret}")
    private String secret;

    public PaymentSmsController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/incoming")
    public Map<String, Object> incoming(@RequestHeader("X-Device-Key") String key,
                                        @RequestBody IncomingSms body) {
        boolean ok = MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device key");
        return Map.of("result", depositService.ingestSms(body.sender(), body.message()).name());
    }
}
