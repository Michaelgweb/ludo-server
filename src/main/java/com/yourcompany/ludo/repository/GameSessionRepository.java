package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    List<GameSession> findByPlayer1OrPlayer2(User player1, User player2);

    @Query("SELECT g.winner.id, COUNT(g) FROM GameSession g WHERE g.winner IS NOT NULL GROUP BY g.winner.id ORDER BY COUNT(g) DESC")
    List<Object[]> countWinsByUser();

    // ---------------- Locking ----------------
    /** টাকার লেনদেন/স্টেট বদলের আগে সেশন রো লক */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GameSession g WHERE g.id = :id")
    Optional<GameSession> findByIdForUpdate(@Param("id") Long id);

    // ---------------- Active session ----------------
    /** একজন খেলোয়াড়ের চলমান সেশন (statuses = MATCH_FOUND, ONGOING) */
    @Query("SELECT g FROM GameSession g " +
           "WHERE g.status IN :statuses " +
           "AND (g.player1.gameId = :gameId OR g.player2.gameId = :gameId)")
    List<GameSession> findActiveSessionsByPlayerGameId(@Param("gameId") String gameId,
                                                       @Param("statuses") Collection<GameStatus> statuses);

    default List<GameSession> findActiveSessionsByPlayerGameId(String gameId) {
        return findActiveSessionsByPlayerGameId(gameId, List.of(GameStatus.MATCH_FOUND, GameStatus.ONGOING));
    }

    // ---------------- Cleanup task queries (ID শুধু, পরে লক করে প্রসেস) ----------------
    /** কাউন্টডাউন শেষ, ONGOING করার সময় হয়েছে */
    @Query("SELECT g.id FROM GameSession g WHERE g.status = :status " +
           "AND g.matchStartTimestamp IS NOT NULL AND g.matchStartTimestamp <= :nowMillis")
    List<Long> findIdsReadyToStart(@Param("status") GameStatus status, @Param("nowMillis") long nowMillis);

    /** ফি কাটা হয়েছে, কিন্তু অপর জন নির্দিষ্ট সময়ে প্রথম রোল করেনি */
    @Query("SELECT g.id FROM GameSession g WHERE g.status = :status " +
           "AND g.feeDeducted = true AND g.firstRollAt < :cutoff " +
           "AND (g.player1DiceCount = 0 OR g.player2DiceCount = 0)")
    List<Long> findIdsFirstRollTimedOut(@Param("status") GameStatus status, @Param("cutoff") LocalDateTime cutoff);

    /** কেউই রোল করেনি (ফি কাটা হয়নি), অনেকক্ষণ পার */
    @Query("SELECT g.id FROM GameSession g WHERE g.status IN :statuses " +
           "AND g.feeDeducted = false AND g.startTime < :cutoff")
    List<Long> findIdsIdleWithoutFee(@Param("statuses") Collection<GameStatus> statuses,
                                     @Param("cutoff") LocalDateTime cutoff);
}
