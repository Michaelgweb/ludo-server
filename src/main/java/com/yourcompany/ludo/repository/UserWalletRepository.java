package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.UserWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {
    List<UserWallet> findByUserIdOrderByCreatedAtAsc(Long userId);
    long countByUserId(Long userId);
    boolean existsByUserIdAndMethodAndNumber(Long userId, String method, String number);
    Optional<UserWallet> findByIdAndUserId(Long id, Long userId);
}
