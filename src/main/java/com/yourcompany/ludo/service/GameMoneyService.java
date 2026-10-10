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
 *  - ফি কাটা: ব্যালেন্স কমে + টার্নওভার ১০০% কমে (User.deduct), আসলে কত কমল তা GameSession এ সেভ হয়
 *  - জেতা টাকা: শুধু ব্যালেন্স বাড়ে, টার্নওভার বাড়ে না (User.addWinnings)
 *  - বাতিল/রিফান্ড: ব্যালেন্স পুরো ফেরত, টার্নওভার শুধু যতটুকু কমেছিল ততটুকু ফেরত (User.refundEntryFee)
 *
 * লাইভ আপডেট: টাকা বদলালে কমিটের পরে দুজনের প্রোফাইল (ব্যালেন্স + টার্নওভার) WebSocket এ যায়।
 * টেলিগ্রাম: টাকার মেসেজ এখানেই যায় (কমিটের পরে, async)। ফেল করলেও টাকার কাজে প্রভাব নেই।
 */
@Service
public class GameMoneyService {

    private static final Logger log = LoggerFactory.getLogger(GameMoneyService.class);

    private final GameSessionRepository sessions;
    private final WalletTransactionRepository wallet;
    private final SimpMessagingTemplate ws;
    private final TelegramService telegram;
    private final UserService userService;

    @PersistenceContext
    private EntityManager em;

    public GameMoneyService(GameSessionRepository sessions,
                            WalletTransactionRepository wallet,
                            SimpMessagingTemplate ws,
                            TelegramService telegram,
                            UserService userService) {
        this.sessions = sessions;
        this.wallet = wallet;
        this.ws = ws;
        this.telegram = telegram;
        this.userService = userService;
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
            telegramAfterCommit("❌ ম্যাচ বাতিল (ব্যালেন্স অপর্যাপ্ত)\n"
                    + "Session: " + s.getId() + "\n"
                    + "Player 1: " + ps[0].getGameId() + "\n"
                    + "Player 2: " + ps[1].getGameId() + "\n"
                    + "Entry Fee: " + fee + " টাকা\n"
                    + "Status: CANCELLED");
            return false;
        }
        for (int i = 0; i < 2; i++) {
            BigDecimal cut = ps[i].deduct(fee);          // ব্যালেন্স কমে, টার্নওভার আসলে কত কমল তা ফেরত
            s.setTurnoverCut(i + 1, cut);                // রিফান্ডে ঠিক এতটাই ফেরত যাবে
            logTx(ps[i], s, TxType.ENTRY_FEE, fee.negate());
        }
        s.setFeeDeducted(true);
        s.setFirstRollAt(LocalDateTime.now());

        refreshProfilesAfterCommit(ps[0], ps[1]);        // টার্নওভার/ব্যালেন্স লাইভ কমবে

        telegramAfterCommit("💰 এন্ট্রি ফি কাটা হয়েছে\n"
                + "Session: " + s.getId() + "\n"
                + "Player 1: " + ps[0].getGameId() + "\n"
                + "Player 2: " + ps[1].getGameId() + "\n"
                + "প্রতি জন: " + fee + " টাকা\n"
                + "Total Pot: " + s.getTotalPot() + " টাকা\n"
                + "Status: ONGOING");
        return true;
    }

    /** জেতা: winnerSlot ১ বা ২। ডাবল পেমেন্ট বন্ধ (ONGOING না হলে কিছু করে না) */
    @Transactional
    public void payout(Long sid, int winnerSlot, String msg) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.ONGOING) return;
        if (!s.isFeeDeducted()) throw new IllegalStateException("Fee not deducted");
        User winner = winnerSlot == 1 ? s.getPlayer1() : s.getPlayer2();
        User loser = winnerSlot == 1 ? s.getPlayer2() : s.getPlayer1();
        payWinner(s, winner);
        close(s, GameStatus.FINISHED, msg);

        refreshProfilesAfterCommit(winner, loser);

        telegramAfterCommit("🏆 ম্যাচ শেষ\n"
                + "Session: " + s.getId() + "\n"
                + "বিজয়ী: " + winner.getGameId() + "\n"
                + "পরাজিত: " + loser.getGameId() + "\n"
                + "Entry Fee: " + s.getEntryFee() + " টাকা\n"
                + "Total Pot: " + s.getTotalPot() + " টাকা\n"
                + "বিজয়ী পেয়েছে: " + s.getTotalPot() + " টাকা\n"
                + "কমিশন: " + s.getCommission() + " টাকা\n"
                + "কারণ: " + msg + "\n"
                + "Status: FINISHED");
    }

    /** ফি কাটা থাকলে দুজনকে ফেরত, তারপর বাতিল */
    @Transactional
    public void refundAndCancel(Long sid, String msg) {
        GameSession s = lock(sid);
        if (s.getStatus() != GameStatus.ONGOING) return;
        boolean refunded = s.isFeeDeducted();
        if (refunded) {
            refundBoth(s);
            refreshProfilesAfterCommit(s.getPlayer1(), s.getPlayer2());
        }
        close(s, GameStatus.CANCELLED, msg);

        telegramAfterCommit((refunded ? "🔄 ম্যাচ বাতিল (ফি ফেরত)" : "🚫 ম্যাচ বাতিল") + "\n"
                + "Session: " + s.getId() + "\n"
                + "Player 1: " + s.getPlayer1().getGameId() + "\n"
                + "Player 2: " + s.getPlayer2().getGameId() + "\n"
                + (refunded ? "ফেরত: প্রতি জনকে " + s.getEntryFee() + " টাকা\n" : "")
                + "কারণ: " + msg + "\n"
                + "Status: CANCELLED");
    }

    /**
     * ফি কাটা না হয়ে থাকলে (MATCH_FOUND বা ONGOING) শুধু বাতিল। বাতিল হলে true।
     * onlyPlayerGameId দিলে ওই খেলোয়াড় ম্যাচের সদস্য কিনা যাচাই হয়।
     * (এর টেলিগ্রাম মেসেজ GameFlowService-এ আছে, এখানে নেই)
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

    /** বাতিল ম্যাচে ফি ফেরত: ব্যালেন্স পুরো, টার্নওভার শুধু যতটুকু কমেছিল ততটুকু */
    private void refundBoth(GameSession s) {
        User[] ps = lockPlayers(s);
        for (int i = 0; i < 2; i++) {
            ps[i].refundEntryFee(s.getEntryFee(), s.getTurnoverCut(i + 1));
            logTx(ps[i], s, TxType.REFUND, s.getEntryFee());
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
        afterCommit(() -> {
            ws.convertAndSend("/topic/game/" + g1, payload);
            ws.convertAndSend("/topic/game/" + g2, payload);
        });
    }

    /** কমিটের পরে দুজনের প্রোফাইল পুশ (ব্যালেন্স + টার্নওভার), ফেল করলেও টাকার কাজে প্রভাব নেই */
    private void refreshProfilesAfterCommit(User a, User b) {
        String g1 = a.getGameId();
        String g2 = b.getGameId();
        afterCommit(() -> {
            try {
                userService.notifyUserUpdate(g1);
                userService.notifyUserUpdate(g2);
            } catch (Exception e) {
                log.warn("Profile refresh failed: {}", e.getMessage());
            }
        });
    }

    /** টেলিগ্রাম: কমিটের পরে, ফেল করলেও টাকার কাজে প্রভাব নেই */
    private void telegramAfterCommit(String text) {
        afterCommit(() -> {
            try {
                telegram.send(text);
            } catch (Exception e) {
                log.warn("Telegram notify failed: {}", e.getMessage());
            }
        });
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }
}
