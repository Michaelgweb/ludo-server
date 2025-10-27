package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, Long> {

    List<WithdrawRequest> findByUser(User user);

    List<WithdrawRequest> findByStatus(WithdrawRequest.Status status);

    boolean existsByTransactionId(String transactionId);

    @Modifying
    @Transactional
    @Query("UPDATE WithdrawRequest w SET w.status = :status WHERE w.id = :id")
    int updateWithdrawStatus(Long id, WithdrawRequest.Status status);

    @Modifying
    @Transactional
    @Query("UPDATE WithdrawRequest w SET w.adminNote = :note WHERE w.id = :id")
    int updateAdminNote(Long id, String note);

    @Modifying
    @Transactional
    @Query("UPDATE WithdrawRequest w SET w.approvedAt = :approvedAt WHERE w.id = :id")
    int updateApprovedAt(Long id, LocalDateTime approvedAt);

    @Modifying
    @Transactional
    @Query("UPDATE WithdrawRequest w SET w.rejectedAt = :rejectedAt WHERE w.id = :id")
    int updateRejectedAt(Long id, LocalDateTime rejectedAt);
}
