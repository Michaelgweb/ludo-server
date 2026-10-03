package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.PaymentSms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PaymentSmsRepository extends JpaRepository<PaymentSms, Long> {

    boolean existsByMethodAndTrxId(String method, String trxId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentSms> findFirstByMethodAndTrxIdAndStatus(String method, String trxId, PaymentSms.Status status);
}
