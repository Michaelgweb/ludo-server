package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.MatchRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.MatchRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MatchService {

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    private static final int COUNTDOWN_SECONDS = 10;

    @Transactional
    public GameSession tryMatch(User user, BigDecimal entryFee) {
        if (user.getBalance() == null || user.getBalance().compareTo(entryFee) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        matchRequestRepository.deleteOldUnmatchedRequests(LocalDateTime.now().minusMinutes(1));

        Optional<MatchRequest> opponentRequestOpt =
                matchRequestRepository.findFirstByEntryFeeAndMatchedFalseAndUserNot(entryFee, user);

        if (opponentRequestOpt.isPresent()) {
            MatchRequest opponentRequest = opponentRequestOpt.get();
            opponentRequest.setMatched(true);
            matchRequestRepository.save(opponentRequest);

            MatchRequest userRequest = new MatchRequest();
            userRequest.setUser(user);
            userRequest.setEntryFee(entryFee);
            userRequest.setMatched(true);
            userRequest.setRequestTime(LocalDateTime.now());
            matchRequestRepository.save(userRequest);

            User firstPlayer = opponentRequest.getUser();
            User secondPlayer = user;

            GameSession gameSession = new GameSession();
            gameSession.setPlayer1(firstPlayer);
            gameSession.setPlayer2(secondPlayer);
            gameSession.setEntryFee(entryFee);
            gameSession.setPrizeByEntryFee();
            gameSession.setStatus("MATCH_FOUND");
            gameSession.setStartTime(LocalDateTime.now());
            gameSession.setMatchStartTimestamp(LocalDateTime.now().plusSeconds(COUNTDOWN_SECONDS));
            gameSession.setPlayer1DiceCount(0);
            gameSession.setPlayer2DiceCount(0);
            gameSession.setFeeDeducted(false);

            if (gameSession.getPlayer1Tokens() == null || gameSession.getPlayer1Tokens().isEmpty()) {
                for (int i = 0; i < 4; i++) gameSession.getPlayer1Tokens().add(0);
            }
            if (gameSession.getPlayer2Tokens() == null || gameSession.getPlayer2Tokens().isEmpty()) {
                for (int i = 0; i < 4; i++) gameSession.getPlayer2Tokens().add(0);
            }

            gameSessionRepository.save(gameSession);
            return gameSession;
        } else {
            MatchRequest newRequest = new MatchRequest();
            newRequest.setUser(user);
            newRequest.setEntryFee(entryFee);
            newRequest.setMatched(false);
            newRequest.setRequestTime(LocalDateTime.now());
            matchRequestRepository.save(newRequest);
            return null;
        }
    }

    @Transactional
    public void finish(GameSession session, User winner, BigDecimal totalPot) {
        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());

        if (winner != null) {
            winner.setBalance(winner.getBalance().add(totalPot));
        }

        gameSessionRepository.save(session);
    }

    @Transactional
    public void finishDraw(GameSession session) {
        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());

        if (session.getPlayer1() != null) session.getPlayer1().setBalance(session.getPlayer1().getBalance().add(session.getEntryFee()));
        if (session.getPlayer2() != null) session.getPlayer2().setBalance(session.getPlayer2().getBalance().add(session.getEntryFee()));

        gameSessionRepository.save(session);
    }
}
