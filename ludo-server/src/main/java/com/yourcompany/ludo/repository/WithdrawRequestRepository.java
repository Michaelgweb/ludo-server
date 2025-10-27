package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, Long> {

    List<WithdrawRequest> findByUser(User user);

    List<WithdrawRequest> findByStatus(WithdrawRequest.Status status);

    // ✅ Transaction ID duplicate check
    boolean existsByTransactionId(String transactionId);
}
