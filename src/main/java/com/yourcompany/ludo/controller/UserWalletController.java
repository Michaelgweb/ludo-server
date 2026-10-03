package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.UserWalletService;
import com.yourcompany.ludo.service.UserWalletService.WalletDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallets")
public class UserWalletController {

    public record AddWalletRequest(String method, String number) {}

    private final UserWalletService service;

    public UserWalletController(UserWalletService service) {
        this.service = service;
    }

    @GetMapping
    public List<WalletDto> myWallets(@AuthenticationPrincipal User user) {
        return service.list(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletDto add(@AuthenticationPrincipal User user, @RequestBody AddWalletRequest req) {
        return service.add(user.getId(), req.method(), req.number());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user.getId(), id);
        return Map.of("success", true);
    }
}
