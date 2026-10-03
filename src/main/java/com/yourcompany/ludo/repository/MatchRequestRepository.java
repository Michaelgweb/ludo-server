package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.MatchRequest;
import com.yourcompany.ludo.model.User;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    /**
     * সবচেয়ে পুরনো অপেক্ষমাণ প্রতিপক্ষ, রো লক করে।
     * SKIP LOCKED (-2): অন্য ট্রানজেকশন লক করা রো এড়িয়ে যায়, তাই দুজন একই প্রতিপক্ষ পাবে না।
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    Optional<MatchRequest> findFirstByEntryFeeAndMatchedFalseAndUserNotOrderByRequestTimeAsc(
            BigDecimal entryFee, User user);

    /** একই ইউজারের পুরনো অপেক্ষমাণ রিকোয়েস্ট মুছে (ডুপ্লিকেট ঠেকাতে) */
    @Modifying
    @Transactional
    @Query("DELETE FROM MatchRequest m WHERE m.user = :user AND m.matched = false")
    void deleteWaitingByUser(@Param("user") User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM MatchRequest m WHERE m.matched = false AND m.requestTime < :cutoff")
    void deleteOldUnmatchedRequests(@Param("cutoff") LocalDateTime cutoff);
}
