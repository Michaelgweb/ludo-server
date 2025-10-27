package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.BonusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonusHistoryRepository extends JpaRepository<BonusHistory, Long> {

    // ================= User Specific =================
    // ইউজারের সব বোনাস
    List<BonusHistory> findByUserGameIdOrderByCreatedAtDesc(String userGameId);

    // ইউজারের বোনাস টাইপ ফিল্টার করে
    List<BonusHistory> findByUserGameIdAndTypeInOrderByCreatedAtDesc(String userGameId, List<String> types);

    // ইউজারের নির্দিষ্ট টাইপ + স্ট্যাটাস বোনাস
    List<BonusHistory> findByUserGameIdAndTypeAndStatusOrderByCreatedAtDesc(
            String userGameId,
            String type,
            String status
    );

    // নতুন ইউজারের সোর্স হিসেবে বোনাস (যাতে referrer খুঁজে পাওয়া যায়)
    List<BonusHistory> findBySourceGameIdOrderByCreatedAtDesc(String sourceGameId);

    // সোর্স হিসেবে বোনাস + টাইপ + স্ট্যাটাস
    List<BonusHistory> findBySourceGameIdAndTypeAndStatusOrderByCreatedAtDesc(
            String sourceGameId,
            String type,
            String status
    );

    // Duplicate prevention (রেফারারের gameId = userGameId, নতুন ইউজারের gameId = sourceGameId)
    boolean existsByUserGameIdAndSourceGameIdAndTypeAndStatus(
            String userGameId,     // Referrer
            String sourceGameId,   // New User
            String type,
            String status
    );

    // ================= Admin View =================
    List<BonusHistory> findAllByOrderByCreatedAtDesc();

    // একাধিক টাইপের জন্য ফিল্টার
    List<BonusHistory> findByTypeInOrderByCreatedAtDesc(List<String> types);
}
