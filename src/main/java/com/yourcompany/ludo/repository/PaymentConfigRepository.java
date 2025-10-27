package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.PaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentConfigRepository extends JpaRepository<PaymentConfig, Long> {

    // Case-insensitive search for payment method
    Optional<PaymentConfig> findByMethodIgnoreCase(String method);
}
