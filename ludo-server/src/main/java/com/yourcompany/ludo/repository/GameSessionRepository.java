package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    List<GameSession> findByPlayer1OrPlayer2(User player1, User player2);

    @Query("SELECT g.winner.id, COUNT(g) FROM GameSession g WHERE g.winner IS NOT NULL GROUP BY g.winner.id ORDER BY COUNT(g) DESC")
    List<Object[]> countWinsByUser();

    // এখানে Optional<GameSession> এর পরিবর্তে List<GameSession> ব্যবহার করলাম
    @Query("SELECT g FROM GameSession g " +
           "WHERE g.status = 'ONGOING' " +
           "AND (g.player1.gameId = :gameId OR g.player2.gameId = :gameId)")
    List<GameSession> findActiveSessionsByPlayerGameId(@Param("gameId") String gameId);

    @Transactional
    @Modifying
    @Query("UPDATE GameSession g SET g.status = 'CANCELLED' " +
           "WHERE g.status = 'ONGOING' " +
           "AND g.startTime IS NOT NULL " +
           "AND g.startTime < :cutoffTime " +
           "AND g.player1DiceCount = 0 " +
           "AND g.player2DiceCount = 0")
    int cancelInactiveMatches(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Transactional
    @Modifying
    @Query("UPDATE GameSession g SET g.feeDeducted = true WHERE g.id = :sessionId")
    void markFeeDeducted(@Param("sessionId") Long sessionId);
}
