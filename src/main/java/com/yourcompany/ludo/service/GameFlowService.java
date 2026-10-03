package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.WalletTransaction;
import com.yourcompany.ludo.model.WalletTransaction.TxType;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.WalletTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * গেমের সব টাকা-সংক্রান্ত কাজ একই জায়গায়। প্রতিটি মেথড আগে সেশন রো লক করে,
 * তাই একই ম্যাচের উপর দুটো কাজ একসাথে চলতে পারে না।
 */
@Service
public class GameFlowService {

    private static final Logger log = LoggerFactory.getLogger(GameFlowService.class);

    private final GameSessionRepository sessions;
    private final WalletTransactionRepository wallet;
    private final SimpMessagingTemplate ws;
    private final SecureRandom rnd = new SecureRandom();

    @PersistenceContext
    private EntityManager em;

    public GameFlowService(GameSessionRepository sessions,
                           WalletTransactionRepository wallet,
                           SimpMessagingTemplate ws) {
        this.sessions = sessions;
        this.wallet = wallet;
        this.ws = ws;
    }

    /** player = যে রোল করেছে (১/২), nextPlayer = এরপর কার পালা */
    public record RollResult(int dice, boolean cancelled, String message, int player, int nextPlayer) {}

    // =====================================================================
    // 1) MATCH_FOUND -> ONGOING (Cleanup task কল করবে)
    // =====================================================================
    @Transactional
    public void startIfReady(Long sid) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.MATCH_FOUND) return;
        if (s.getMatchStartTimestamp() == null
                || s.getMatchStartTimestamp() > System.currentTimeMillis()) return;

        s.setStatus(GameStatus.ONGOING);
        if (s.getStartTime() == null) s.setStartTime(LocalDateTime.now());

        Map<String, Object> p = new HashMap<>();
        p.put("sessionId", s.getId());
        p.put("event", "GAME_STARTED");
        p.put("status", GameStatus.ONGOING);
        p.put("startingPlayerGameId", s.getPlayer1().getGameId());
        notifyBoth(s, p);
    }

    // =====================================================================
    // 2) ডাইস রোল। প্রথম রোলেই দুজনের ফি কাটে
    // =====================================================================
    @Transactional
    public RollResult rollDice(Long sid, String gameId) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.ONGOING) {
            throw new IllegalStateException("Game not active");
        }
        int me = s.slotOf(gameId);
        if (s.getCurrentPlayer() != me) {
            throw new IllegalStateException("Not your turn");
        }

        if (!s.isFeeDeducted()) {
            User[] ps = lockPlayers(s);                       // id অনুযায়ী লক, ডেডলক নেই
            BigDecimal fee = s.getEntryFee();
            if (ps[0].getBalance().compareTo(fee) < 0 || ps[1].getBalance().compareTo(fee) < 0) {
                close(s, GameStatus.CANCELLED, "ব্যালেন্স অপর্যাপ্ত, ম্যাচ বাতিল");
                return new RollResult(0, true, "Insufficient balance", me, s.getCurrentPlayer());
            }
            for (User u : ps) {
                u.deduct(fee);
                logTx(u, s, TxType.ENTRY_FEE, fee.negate());
            }
            s.setFeeDeducted(true);
            s.setFirstRollAt(LocalDateTime.now());
        }

        int dice = rnd.nextInt(6) + 1;                         // সার্ভারে র‍্যান্ডম

        // পরপর তিনটি ৬ হলে তৃতীয়টি ১-৫ এ বদলে যায় (আপনার আগের নিয়ম)
        if (dice == 6) {
            s.setConsecutiveSixCount(s.getConsecutiveSixCount() + 1);
            if (s.getConsecutiveSixCount() >= 3) {
                dice = rnd.nextInt(5) + 1;
                s.setConsecutiveSixCount(0);
            }
        } else {
            s.setConsecutiveSixCount(0);
        }

        if (me == 1) s.setPlayer1DiceCount(s.getPlayer1DiceCount() + 1);
        else s.setPlayer2DiceCount(s.getPlayer2DiceCount() + 1);
        s.setLastDiceValue(dice);
        s.setDiceOwner(me);

        // ৬ না হলে পালা বদল
        int next = (dice == 6) ? me : (me == 1 ? 2 : 1);
        s.setCurrentPlayer(next);

        return new RollResult(dice, false, "OK", me, next);
    }

    // =====================================================================
    // 3) স্বাভাবিকভাবে কেউ জিতলে (টোকেন লজিক থেকে কল করুন)
    // =====================================================================
    @Transactional
    public void declareWinner(Long sid, String winnerGameId) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.ONGOING) return;       // ডাবল পেমেন্ট বন্ধ
        if (!s.isFeeDeducted()) throw new IllegalStateException("Fee not deducted");
        User winner = s.slotOf(winnerGameId) == 1 ? s.getPlayer1() : s.getPlayer2();
        payWinner(s, winner);
        close(s, GameStatus.FINISHED, "ম্যাচ শেষ");
    }

    // =====================================================================
    // 4) বাক / ডিসকানেক্ট (রিকানেক্ট সময় শেষে কল করুন)
    // =====================================================================
    @Transactional
    public void leave(Long sid, String gameId) {
        GameSession s = lock(sid);
        if (s.getStatus() == GameStatus.FINISHED || s.getStatus() == GameStatus.CANCELLED) return;

        int leaver = s.slotOf(gameId);

        if (!s.isFeeDeducted()) {                              // কেউ রোল করেনি, ফি কাটা হয়নি
            close(s, GameStatus.CANCELLED, "ম্যাচ বাতিল");
            return;
        }
        if (!s.isBothRolled()) {                               // একজন রোল করেছে, অপর জন করেনি
            refundBoth(s);
            close(s, GameStatus.CANCELLED, "ম্যাচ বাতিল, ফি ফেরত দেওয়া হয়েছে");
            return;
        }
        User winner = leaver == 1 ? s.getPlayer2() : s.getPlayer1();   // খেলার মাঝে বের হলে হার
        payWinner(s, winner);
        close(s, GameStatus.FINISHED, "প্রতিপক্ষ বের হয়ে গেছে");
    }

    // =====================================================================
    // 5) টাইমআউট (Cleanup task কল করবে)
    // =====================================================================
    /** ফি কাটা হয়েছে, অপর জন সময়মতো প্রথম রোল করেনি, দুজনকে ফেরত */
    @Transactional
    public void timeoutFirstRoll(Long sid) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.ONGOING || !s.isFeeDeducted() || s.isBothRolled()) return;
        refundBoth(s);
        close(s, GameStatus.CANCELLED, "প্রতিপক্ষ রোল না করায় ম্যাচ বাতিল, ফি ফেরত");
    }

    /** কেউ রোল করেনি, ফি কাটা হয়নি, শুধু বাতিল */
    @Transactional
    public void cancelIdle(Long sid) {
        GameSession s = lock(sid);
        if (s.isFeeDeducted()) return;
        if (s.getStatus() != GameStatus.MATCH_FOUND && s.getStatus() != GameStatus.ONGOING) return;
        close(s, GameStatus.CANCELLED, "কেউ খেলা শুরু না করায় ম্যাচ বাতিল");
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================
    private GameSession lock(Long sid) {
        return sessions.findByIdForUpdate(sid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    /** দুজনকে id অনুযায়ী সাজিয়ে লক + DB থেকে রিফ্রেশ (stale ব্যালেন্স এড়াতে) */
    private User[] lockPlayers(GameSession s) {
        List<User> list = new java.util.ArrayList<>(List.of(s.getPlayer1(), s.getPlayer2()));
        list.sort(Comparator.comparing(User::getId));
        for (User u : list) em.refresh(u, LockModeType.PESSIMISTIC_WRITE);
        return new User[]{s.getPlayer1(), s.getPlayer2()};
    }

    private void refundBoth(GameSession s) {
        for (User u : lockPlayers(s)) {
            u.addToDepositBalance(s.getEntryFee());            // ধরে নিয়েছি রিফান্ড deposit এ যাবে
            logTx(u, s, TxType.REFUND, s.getEntryFee());
        }
    }

    private void payWinner(GameSession s, User winner) {
        em.refresh(winner, LockModeType.PESSIMISTIC_WRITE);
        BigDecimal pot = s.getTotalPot();
        winner.addToWithdrawBalance(pot);
        winner.addLifetimeEarnings(pot.subtract(s.getEntryFee()));   // নিট লাভ
        s.setWinner(winner);
        logTx(winner, s, TxType.WIN, pot);
        wallet.save(new WalletTransaction(null, s.getId(), TxType.COMMISSION, s.getCommission(), null));
    }

    private void logTx(User u, GameSession s, TxType type, BigDecimal amount) {
        wallet.save(new WalletTransaction(u.getId(), s.getId(), type, amount, u.getBalance()));
    }

    private void close(GameSession s, GameStatus st, String msg) {
        s.setStatus(st);
        s.setEndTime(LocalDateTime.now());
        Map<String, Object> p = new HashMap<>();
        p.put("sessionId", s.getId());
        p.put("status", st);
        p.put("message", msg);
        p.put("winnerGameId", s.getWinner() != null ? s.getWinner().getGameId() : null);
        notifyBoth(s, p);
    }

    /** কমিট হওয়ার পরেই WebSocket মেসেজ, নইলে রোলব্যাকের পরেও ক্লায়েন্ট ভুল খবর পাবে */
    private void notifyBoth(GameSession s, Map<String, Object> payload) {
        String g1 = s.getPlayer1().getGameId();
        String g2 = s.getPlayer2().getGameId();
        Runnable send = () -> {
            ws.convertAndSend("/topic/game/" + g1, payload);
            ws.convertAndSend("/topic/game/" + g2, payload);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
