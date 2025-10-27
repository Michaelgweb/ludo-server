package com.yourcompany.ludo.service.impl;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.service.GameService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        int prize = session.getTotalPot() - session.getPlatformFee();
        winner.setBalance(winner.getBalance() + prize);
        userRepo.save(winner);

        session.setWinner(winner);
        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());
        gameRepo.save(session);

        logger.info("[GameService] Game {} finished. Winner (mobile: {}) credited with prize {}",
                session.getId(), winner.getMobile(), prize); // getMobile() ব্যবহার
    }
}
