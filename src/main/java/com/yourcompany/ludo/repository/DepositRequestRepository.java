package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.DepositRequest.Status;
import com.yourcompany.ludo.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepositRequestRepository extends JpaRepository<DepositRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DepositRequest d where d.id = :id")
    Optional<DepositRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DepositRequest> findFirstByMethodAndUserTransactionIdAndStatus(
            String method, String userTransactionId, Status status);

    boolean existsByTransactionId(String transactionId);

    boolean existsByMethodAndUserTransactionId(String method, String userTransactionId);

    List<DepositRequest> findByUserOrderByIdDesc(User user);

    @EntityGraph(attributePaths = "user")
    Page<DepositRequest> findByStatus(Status status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<DepositRequest> findAll(Pageable pageable);
}
