package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.AdminPaymentAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminPaymentAccountRepository extends JpaRepository<AdminPaymentAccount, Long> {

    boolean existsByMethodAndNumber(String method, String number);

    List<AdminPaymentAccount> findAllByOrderByMethodAscIdAsc();

    /** সবচেয়ে কম দেখানো active নম্বর; লক থাকায় একই সময়ে দুজন একই নম্বর পাবে না */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdminPaymentAccount a where a.method = :method and a.active = true " +
           "order by a.assignCount asc, a.id asc")
    List<AdminPaymentAccount> findNextForUpdate(@Param("method") String method, Pageable pageable);
}
