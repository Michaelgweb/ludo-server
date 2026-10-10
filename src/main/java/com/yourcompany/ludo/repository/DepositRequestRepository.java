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

    /** অ্যাডমিন সার্চ: gameId, মোবাইল, সার্ভার ID (DEP...), ইউজারের TrxID */
    @EntityGraph(attributePaths = "user")
    @Query(value = """
            select d from DepositRequest d join d.user u
            where (:status is null or d.status = :status)
              and (lower(u.gameId) like lower(concat('%', :q, '%'))
                or u.mobile like concat('%', :q, '%')
                or upper(d.transactionId) like upper(concat('%', :q, '%'))
                or upper(d.userTransactionId) like upper(concat('%', :q, '%')))
            """,
            countQuery = """
            select count(d) from DepositRequest d join d.user u
            where (:status is null or d.status = :status)
              and (lower(u.gameId) like lower(concat('%', :q, '%'))
                or u.mobile like concat('%', :q, '%')
                or upper(d.transactionId) like upper(concat('%', :q, '%'))
                or upper(d.userTransactionId) like upper(concat('%', :q, '%')))
            """)
    Page<DepositRequest> search(@Param("q") String q,
                                @Param("status") Status status,
                                Pageable pageable);
}
