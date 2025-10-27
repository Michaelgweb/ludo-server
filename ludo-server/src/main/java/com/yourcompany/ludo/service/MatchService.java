package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.MatchRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.MatchRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MatchService {

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private UserService userService;

    /**
     * 🎯 ইউজার ম্যাচিং লজিক
     * - ফি শুরুতে কাটবে না
     * - দুইজনই অন্তত ২ বার ডাইস রোল করলে ফি কাটা হবে
     * - ম্যাচ হলে matchStartTimestamp কেবল controller থেকে সেট হবে
     */
    @Transactional
    public GameSession tryMatch(User user, int entryFee) {
        if (user.getBalance() == null || user.getBalance() < entryFee) {
            throw new IllegalArgumentException("ব্যালেন্স পর্যাপ্ত নয়");
        }

        // 🔹 পুরানো unmatched রিকোয়েস্ট ডিলিট করা (2 মিনিট পুরনো)
        matchRequestRepository.deleteOldUnmatchedRequests(LocalDateTime.now().minusMinutes(2));

        // 🔹 প্রতিপক্ষ খোঁজা
        Optional<MatchRequest> opponentRequestOpt =
                matchRequestRepository.findFirstByEntryFeeAndMatchedFalseAndUserNot(entryFee, user);

        if (opponentRequestOpt.isPresent()) {
            // 🔹 ম্যাচ পাওয়া গেছে
            MatchRequest opponentRequest = opponentRequestOpt.get();
            opponentRequest.setMatched(true);
            matchRequestRepository.save(opponentRequest);

            // 🔹 নিজের রিকোয়েস্ট
            MatchRequest userRequest = new MatchRequest();
            userRequest.setUser(user);
            userRequest.setEntryFee(entryFee);
            userRequest.setMatched(true);
            userRequest.setRequestTime(LocalDateTime.now());
            matchRequestRepository.save(userRequest);

            // 🔹 গেম সেশন তৈরি

            // প্রথম রিকোয়েস্ট করা ইউজার হবে প্লেয়ার ১
            User firstPlayer = opponentRequest.getUser();
            User secondPlayer = user;

            GameSession gameSession = new GameSession();
            gameSession.setPlayer1(firstPlayer);
            gameSession.setPlayer2(secondPlayer);
            gameSession.setEntryFee(entryFee);
            gameSession.setTotalPot(entryFee * 2);
            gameSession.setPlatformFee((int) (gameSession.getTotalPot() * 0.10));
            gameSession.setStatus("ONGOING");
            gameSession.setStartTime(LocalDateTime.now());

            // ✅ শুরুতে ডাইস কাউন্ট ও ফি স্ট্যাটাস সেট করা
            gameSession.setPlayer1DiceCount(0);
            gameSession.setPlayer2DiceCount(0);
            gameSession.setFeeDeducted(false);

            // ❌ এখন আর এখানে matchStartTimestamp সেট করা যাবে না
            // gameSession.setMatchStartTimestamp(System.currentTimeMillis() + 10_000);

            gameSessionRepository.save(gameSession);

            return gameSession;
        } else {
            // 🔹 প্রতিপক্ষ না পেলে নতুন রিকোয়েস্ট যোগ করা
            MatchRequest newRequest = new MatchRequest();
            newRequest.setUser(user);
            newRequest.setEntryFee(entryFee);
            newRequest.setMatched(false);
            newRequest.setRequestTime(LocalDateTime.now());
            matchRequestRepository.save(newRequest);

            return null;
        }
    }
}
