package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.UserWallet;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.repository.UserWalletRepository;
import com.yourcompany.ludo.util.PaymentRules;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserWalletService {

    public static final int MAX_WALLETS = 5;

    public record WalletDto(Long id, String method, String number) {}

    private final UserWalletRepository walletRepository;
    private final UserRepository userRepository;

    public UserWalletService(UserWalletRepository walletRepository, UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<WalletDto> list(Long userId) {
        return walletRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(w -> new WalletDto(w.getId(), w.getMethod(), w.getNumber()))
                .toList();
    }

    @Transactional
    public WalletDto add(Long userId, String method, String number) {
        String m = PaymentRules.normalizeMethod(method);
        String n = PaymentRules.normalizeNumber(number);

        if (walletRepository.countByUserId(userId) >= MAX_WALLETS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "সর্বোচ্চ " + MAX_WALLETS + "টি ওয়ালেট যোগ করা যাবে");
        }
        if (walletRepository.existsByUserIdAndMethodAndNumber(userId, m, n)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "এই ওয়ালেট আগেই যোগ করা আছে");
        }

        UserWallet w = new UserWallet();
        w.setUser(userRepository.getReferenceById(userId));
        w.setMethod(m);
        w.setNumber(n);
        w = walletRepository.save(w);
        return new WalletDto(w.getId(), w.getMethod(), w.getNumber());
    }

    @Transactional
    public void delete(Long userId, Long walletId) {
        UserWallet w = walletRepository.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
        walletRepository.delete(w);
    }

    /** উইথড্রর সময় রিসিভার নম্বর ইউজারের নিজের ওয়ালেটের কিনা চেক করতে */
    @Transactional(readOnly = true)
    public boolean owns(Long userId, String method, String number) {
        return walletRepository.existsByUserIdAndMethodAndNumber(
                userId, PaymentRules.normalizeMethod(method), PaymentRules.normalizeNumber(number));
    }
}
