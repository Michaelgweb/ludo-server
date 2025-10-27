package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepositRequestRepository extends JpaRepository<DepositRequest, Long> {

    // User এর deposits
    List<DepositRequest> findByUser(User user);

    // Status অনুযায়ী deposits
    List<DepositRequest> findByStatus(DepositRequest.Status status);

    // Transaction ID check
    Optional<DepositRequest> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
}
