package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.MatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    // ✅ entryFee এখন BigDecimal
    Optional<MatchRequest> findFirstByEntryFeeAndMatchedFalseAndUserNot(BigDecimal entryFee, User user);

    // পুরানো, matched=false এমন রিকোয়েস্ট ডিলিট করার জন্য কাস্টম মেথড
    @Modifying
    @Transactional
    @Query("DELETE FROM MatchRequest m WHERE m.matched = false AND m.requestTime < :cutoff")
    void deleteOldUnmatchedRequests(LocalDateTime cutoff);
}
