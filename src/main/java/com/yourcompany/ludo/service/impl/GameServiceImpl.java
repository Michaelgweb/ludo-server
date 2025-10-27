package com.yourcompany.ludo.service.impl;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.GameResult;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.service.GameService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class GameServiceImpl implements GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameServiceImpl.class);

    private final GameSessionRepository gameRepo;
    private final UserRepository userRepo;

    public GameServiceImpl(GameSessionRepository gameRepo, UserRepository userRepo) {
        this.gameRepo = gameRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public void finishGame(GameSession session, User winner) {
        if (session == null || winner == null) {
            logger.warn("[GameService] finishGame called with null params.");
            return;
        }

        BigDecimal totalPot = session.getTotalPot() != null ? session.getTotalPot() : BigDecimal.ZERO;

        BigDecimal currentBalance = winner.getBalance() != null ? winner.getBalance() : BigDecimal.ZERO;
        winner.setBalance(currentBalance.add(totalPot));
        userRepo.save(winner);

        session.setWinner(winner);
        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());
        session.setFeeDeducted(true);
        gameRepo.save(session);

        logger.info("[GameService] Game {} finished. Winner (mobile: {}) credited with prize {}",
                session.getId(), winner.getMobile(), totalPot);
    }

    @Override
    @Transactional
    public void finish(GameResult result) {
        if (result == null || result.getGameSession() == null) {
            logger.warn("[GameService] finish called with null result/session.");
            return;
        }

        GameSession session = result.getGameSession();
        User winner = result.getWinner();
        User loser = result.getLoser();

        BigDecimal totalPot = session.getTotalPot() != null ? session.getTotalPot() : BigDecimal.ZERO;

        if (winner != null) {
            BigDecimal winnerBalance = winner.getBalance() != null ? winner.getBalance() : BigDecimal.ZERO;
            winner.setBalance(winnerBalance.add(totalPot));
            userRepo.save(winner);
        }

        if (loser != null) {
            userRepo.save(loser); // future update এর জন্য রাখা হলো
        }

        session.setWinner(winner);
        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());
        session.setFeeDeducted(true);
        gameRepo.save(session);

        logger.info("[GameService] Game {} finished via GameResult. Winner: {} Prize: {}",
                session.getId(),
                winner != null ? winner.getMobile() : "N/A",
                totalPot);
    }

    @Override
    @Transactional
    public void finishDraw(GameSession session) {
        if (session == null) return;

        BigDecimal totalPot = session.getTotalPot() != null ? session.getTotalPot() : BigDecimal.ZERO;
        BigDecimal refundEach = totalPot.divide(BigDecimal.valueOf(2), BigDecimal.ROUND_HALF_UP);

        if (session.getPlayer1() != null) {
            User p1 = session.getPlayer1();
            BigDecimal bal = p1.getBalance() != null ? p1.getBalance() : BigDecimal.ZERO;
            p1.setBalance(bal.add(refundEach));
            userRepo.save(p1);
        }
        if (session.getPlayer2() != null) {
            User p2 = session.getPlayer2();
            BigDecimal bal = p2.getBalance() != null ? p2.getBalance() : BigDecimal.ZERO;
            p2.setBalance(bal.add(refundEach));
            userRepo.save(p2);
        }

        session.setWinner(null);
        session.setStatus("DRAW");
        session.setEndTime(LocalDateTime.now());
        session.setFeeDeducted(true);
        gameRepo.save(session);

        logger.info("[GameService] Game {} finished as DRAW. Refund {} to each player.",
                session.getId(), refundEach);
    }
}
