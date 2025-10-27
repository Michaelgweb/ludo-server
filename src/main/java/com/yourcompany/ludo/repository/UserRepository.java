package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMobile(String mobile);
    Optional<User> findByGameId(String gameId);
    Optional<User> findByReferralCode(String referralCode);
    Optional<User> findByMobileOrGameId(String mobile, String gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.gameId = :gameId")
    Optional<User> findByGameIdForUpdate(@Param("gameId") String gameId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.referralCode = :referralCode")
    Optional<User> findByReferralCodeForUpdate(@Param("referralCode") String referralCode);

    // ---------------- Balance & Earnings Updates by gameId ----------------
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.depositBalance = :depositBalance WHERE u.gameId = :gameId")
    int updateDepositBalanceByGameId(@Param("gameId") String gameId, @Param("depositBalance") BigDecimal depositBalance);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.withdrawBalance = :withdrawBalance WHERE u.gameId = :gameId")
    int updateWithdrawBalanceByGameId(@Param("gameId") String gameId, @Param("withdrawBalance") BigDecimal withdrawBalance);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lifetimeEarnings = :amount WHERE u.gameId = :gameId")
    int updateLifetimeEarningsByGameId(@Param("gameId") String gameId, @Param("amount") BigDecimal amount);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lifetimeWithdraw = :amount WHERE u.gameId = :gameId")
    int updateLifetimeWithdrawByGameId(@Param("gameId") String gameId, @Param("amount") BigDecimal amount);

    // ---------------- Profile Updates by gameId ----------------
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.avatarUrl = :avatarUrl WHERE u.gameId = :gameId")
    int updateAvatarByGameId(@Param("gameId") String gameId, @Param("avatarUrl") String avatarUrl);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.displayName = :displayName WHERE u.gameId = :gameId")
    int updateDisplayNameByGameId(@Param("gameId") String gameId, @Param("displayName") String displayName);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.displayName = :displayName, u.avatarUrl = :avatarUrl WHERE u.gameId = :gameId")
    int updateProfileInfoByGameId(@Param("gameId") String gameId,
                                  @Param("displayName") String displayName,
                                  @Param("avatarUrl") String avatarUrl);

    // ---------------- Bonus & Referral Updates by gameId ----------------
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.firstDepositBonusGiven = :status WHERE u.gameId = :gameId")
    int updateFirstDepositStatusByGameId(@Param("gameId") String gameId, @Param("status") boolean status);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.referralCode = :referralCode WHERE u.gameId = :gameId")
    int updateReferralCodeByGameId(@Param("gameId") String gameId, @Param("referralCode") String referralCode);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.referredBy = :referredBy WHERE u.gameId = :gameId")
    int updateReferredByByGameId(@Param("gameId") String gameId, @Param("referredBy") String referredBy);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.signupBonusClaimed = :status WHERE u.gameId = :gameId")
    int updateSignupBonusByGameId(@Param("gameId") String gameId, @Param("status") boolean status);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.referralBonusClaimed = :status WHERE u.gameId = :gameId")
    int updateReferralBonusByGameId(@Param("gameId") String gameId, @Param("status") boolean status);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.referrerBonusGiven = :status WHERE u.gameId = :gameId")
    int updateReferrerBonusByGameId(@Param("gameId") String gameId, @Param("status") boolean status);
}
