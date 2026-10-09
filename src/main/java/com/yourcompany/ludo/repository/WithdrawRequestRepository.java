package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, Long> {

    List<WithdrawRequest> findByUser(User user);

    List<WithdrawRequest> findByStatus(WithdrawRequest.Status status);

    boolean existsByTransactionId(String transactionId);

    /** অ্যাপ্রুভ/রিজেক্টে রিকোয়েস্ট রো লক করে, যাতে একই রিকোয়েস্ট দুবার প্রসেস না হয় */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WithdrawRequest w WHERE w.id = :id")
    Optional<WithdrawRequest> findByIdForUpdate(@Param("id") Long id);

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
