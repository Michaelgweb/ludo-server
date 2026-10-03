package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.DepositRequestDto;
import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.DepositService;
import com.yourcompany.ludo.service.PaymentRotationService;
import com.yourcompany.ludo.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deposit")
public class DepositController {

    public record PrepareRequest(String method, BigDecimal amount) {}
    public record SubmitRequest(String userTransactionId) {}
    public record NoteRequest(String note) {}
    public record ReasonRequest(String reason) {}

    private final DepositService depositService;
    private final UserService userService;
    private final PaymentRotationService rotation;

    public DepositController(DepositService depositService, UserService userService,
                             PaymentRotationService rotation) {
        this.depositService = depositService;
        this.userService = userService;
        this.rotation = rotation;
    }

    private User currentUser(Authentication auth) {
        return userService.findByGameId(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    // ---------------- ইউজার ----------------

    /** কোন কোন মেথডে এখন active নম্বর আছে */
    @GetMapping("/options")
    public List<String> options() {
        return rotation.listAll().stream()
                .filter(PaymentRotationService.AccountDto::active)
                .map(PaymentRotationService.AccountDto::method)
                .distinct().toList();
    }

    @PostMapping("/prepare")
    public Map<String, Object> prepare(@RequestBody PrepareRequest req, Authentication auth) {
        DepositRequest d = depositService.prepare(currentUser(auth), req.method(), req.amount());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("depositId", d.getId());
        res.put("transactionId", d.getTransactionId());
        res.put("method", d.getMethod());
        res.put("amount", d.getAmount());
        res.put("paymentNumber", d.getPaymentAccountNumber());
        res.put("status", d.getStatus().name());
        return res;
    }

    @PostMapping("/{id}/submit")
    public DepositRequestDto submit(@PathVariable Long id, @RequestBody SubmitRequest req, Authentication auth) {
        return DepositRequestDto.fromEntity(depositService.submit(currentUser(auth), id, req.userTransactionId()));
    }

    @PostMapping("/{id}/cancel")
    public DepositRequestDto cancel(@PathVariable Long id, Authentication auth) {
        return DepositRequestDto.fromEntity(depositService.cancel(id, currentUser(auth)));
    }

    @GetMapping("/my-history")
    public List<DepositRequestDto> myHistory(Authentication auth) {
        return depositService.myDeposits(currentUser(auth)).stream().map(DepositRequestDto::fromEntity).toList();
    }

    // ---------------- অ্যাডমিন + সাপোর্ট(STAFF) ----------------

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Page<DepositRequestDto> adminList(@RequestParam(required = false) DepositRequest.Status status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return depositService.list(status, page, size).map(DepositRequestDto::fromEntity);
    }

    @PostMapping("/admin/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public DepositRequestDto adminApprove(@PathVariable Long id,
                                          @RequestBody(required = false) NoteRequest req, Authentication auth) {
        return DepositRequestDto.fromEntity(
                depositService.approve(id, currentUser(auth), req == null ? null : req.note()));
    }

    @PostMapping("/admin/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public DepositRequestDto adminReject(@PathVariable Long id,
                                         @RequestBody(required = false) ReasonRequest req, Authentication auth) {
        return DepositRequestDto.fromEntity(
                depositService.reject(id, currentUser(auth), req == null ? null : req.reason()));
    }
}
