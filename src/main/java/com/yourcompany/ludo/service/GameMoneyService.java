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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * গেমের সব টাকা-সংক্রান্ত DB কাজ। প্রতিটি মেথড আগে সেশন রো লক করে।
 *
 * ব্যালেন্স লজিক:
 *  - ফি কাটা: ব্যালেন্স কমে + টার্নওভার ১০০% কমে (User.deduct)
 *  - জেতা টাকা: শুধু ব্যালেন্স বাড়ে, টার্নওভার বাড়ে না (User.addWinnings)
 *  - বাতিল/রিফান্ড: ব্যালেন্স ও টার্নওভার দুটোই ফেরত (User.refundEntryFee)
 */
@Service
public class GameMoneyService {

    private final GameSessionRepository sessions;
    private final WalletTransactionRepository wallet;
    private final SimpMessagingTemplate ws;

    @PersistenceContext
    private EntityManager em;

    public GameMoneyService(GameSessionRepository sessions,
                            WalletTransactionRepository wallet,
                            SimpMessagingTemplate ws) {
        this.sessions = sessions;
        this.wallet = wallet;
        this.ws = ws;
    }

    public record Snapshot(GameStatus status, int slot, String winnerGameId) {}

    /** MATCH_FOUND -> ONGOING। সফল হলে {g1, g2} ফেরত, নইলে null */
    @Transactional
    public String[] markOngoing(Long sid) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.MATCH_FOUND) return null;
        if (s.getMatchStartTimestamp() == null
                || s.getMatchStartTimestamp() > System.currentTimeMillis()) return null;

        s.setStatus(GameStatus.ONGOING);
        if (s.getStartTime() == null) s.setStartTime(LocalDateTime.now());

        Map<String, Object> p = new HashMap<>();
        p.put("sessionId", s.getId());
        p.put("event", "GAME_STARTED");
        p.put("status", GameStatus.ONGOING);
        p.put("startingPlayerGameId", s.getPlayer1().getGameId());
        notifyBoth(s, p);
        return new String[]{s.getPlayer1().getGameId(), s.getPlayer2().getGameId()};
    }

    /** প্রথম রোলে দুজনের ফি কাটা। আগেই কাটা থাকলে true। ব্যালেন্স কম হলে বাতিল করে false */
    @Transactional
    public boolean chargeEntryFee(Long sid) {
        GameSession s = lock(sid);
        if (s.isFeeDeducted()) return true;
        if (s.getStatus() != GameStatus.ONGOING) return false;

        User[] ps = lockPlayers(s);
        BigDecimal fee = s.getEntryFee();
        if (ps[0].getBalance().compareTo(fee) < 0 || ps[1].getBalance().compareTo(fee) < 0) {
            close(s, GameStatus.CANCELLED, "ব্যালেন্স অপর্যাপ্ত, ম্যাচ বাতিল");
            return false;
        }
        for (User u : ps) {
            u.deduct(fee);                      // ব্যালেন্স কমে + টার্নওভার ১০০% কমে
            logTx(u, s, TxType.ENTRY_FEE, fee.negate());
        }
        s.setFeeDeducted(true);
        s.setFirstRollAt(LocalDateTime.now());
        return true;
    }

    /** জেতা: winnerSlot ১ বা ২। ডাবল পেমেন্ট বন্ধ (ONGOING না হলে কিছু করে না) */
    @Transactional
    public void payout(Long sid, int winnerSlot, String msg) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.ONGOING) return;
        if (!s.isFeeDeducted()) throw new IllegalStateException("Fee not deducted");
        User winner = winnerSlot == 1 ? s.getPlayer1() : s.getPlayer2();
        payWinner(s, winner);
        close(s, GameStatus.FINISHED, msg);
    }

    /** ফি কাটা থাকলে দুজনকে ফেরত, তারপর বাতিল */
    @Transactional
    public void refundAndCancel(Long sid, String msg) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.ONGOING) return;
        if (s.isFeeDeducted()) refundBoth(s);
        close(s, GameStatus.CANCELLED, msg);
    }

    /**
     * ফি কাটা না হয়ে থাকলে (MATCH_FOUND বা ONGOING) শুধু বাতিল। বাতিল হলে true।
     * onlyPlayerGameId দিলে ওই খেলোয়াড় ম্যাচের সদস্য কিনা যাচাই হয়।
     */
    @Transactional
    public boolean cancelIfIdle(Long sid, String onlyPlayerGameId, String msg) {
        GameSession s = lock(sid);
        if (onlyPlayerGameId != null) s.slotOf(onlyPlayerGameId);
        if (s.isFeeDeducted()) return false;
        if (s.getStatus() != GameStatus.MATCH_FOUND && s.getStatus() != GameStatus.ONGOING) return false;
        close(s, GameStatus.CANCELLED, msg);
        return true;
    }

    /** Redis স্টেট না থাকলে (শুরুর আগে বা শেষ) state() এখান থেকে উত্তর দেয় */
    @Transactional(readOnly = true)
    public Snapshot snapshot(Long sid, String gameId) {
        GameSession s = sessions.findById(sid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        int slot = s.slotOf(gameId);
        return new Snapshot(s.getStatus(), slot,
                s.getWinner() != null ? s.getWinner().getGameId() : null);
    }

    // ---------------------------------------------------------------
    private GameSession lock(Long sid) {
        return sessions.findByIdForUpdate(sid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    /** দুজনকে id অনুযায়ী সাজিয়ে লক + DB থেকে রিফ্রেশ (ডেডলক ও stale ব্যালেন্স এড়াতে) */
    private User[] lockPlayers(GameSession s) {
        List<User> list = new ArrayList<>(List.of(s.getPlayer1(), s.getPlayer2()));
        list.sort(Comparator.comparing(User::getId));
        for (User u : list) em.refresh(u, LockModeType.PESSIMISTIC_WRITE);
        return new User[]{s.getPlayer1(), s.getPlayer2()};
    }

    /** বাতিল ম্যাচে ফি ফেরত: ব্যালেন্স + টার্নওভার দুটোই আগের অবস্থায় */
    private void refundBoth(GameSession s) {
        for (User u : lockPlayers(s)) {
            u.refundEntryFee(s.getEntryFee());
            logTx(u, s, TxType.REFUND, s.getEntryFee());
        }
    }

    /** জেতা টাকা: শুধু ব্যালেন্স + লাইফটাইম আয় (টার্নওভার বাড়ে না) */
    private void payWinner(GameSession s, User winner) {
        em.refresh(winner, LockModeType.PESSIMISTIC_WRITE);
        BigDecimal pot = s.getTotalPot();
        winner.addWinnings(pot);
        winner.addLifetimeEarnings(pot.subtract(s.getEntryFee()));
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

    /** কমিটের পরেই WebSocket মেসেজ, নইলে রোলব্যাকের পরেও ক্লায়েন্ট ভুল খবর পাবে */
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
