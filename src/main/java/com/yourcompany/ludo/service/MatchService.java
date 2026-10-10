package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.model.MatchRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.repository.MatchRequestRepository;
import com.yourcompany.ludo.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ম্যাচমেকিং। এখানে শুধু ব্যালেন্স চেক হয়, টাকা কাটা হয় না।
 * (ফি কাটা হয় GameMoneyService.chargeEntryFee এ, প্রথম রোলের সময়, user.deduct() দিয়ে:
 *  ব্যালেন্স কমে + টার্নওভার ১০০% কমে, আসলে কত কমল তা GameSession এ সেভ হয়)
 *
 * ব্যালেন্স এখন একটাই (ডিপোজিট + জেতা টাকা মিলিয়ে), তাই চেক হয় getBalance() দিয়ে।
 */
@Service
public class MatchService {

    private static final int COUNTDOWN_SECONDS = 10;
    private static final int WAIT_SECONDS = 60;          // এর বেশি অপেক্ষমাণ রিকোয়েস্ট মুছে যাবে
    private static final long LOCK_KEY_BASE = 7_000_000L;

    /** ক্লায়েন্ট যেকোনো সংখ্যা পাঠাতে পারে, তাই শুধু এই ফি গুলো অনুমোদিত */
    public static final List<BigDecimal> ALLOWED_FEES = List.of(
            BigDecimal.valueOf(11), BigDecimal.valueOf(23), BigDecimal.valueOf(45),
            BigDecimal.valueOf(113), BigDecimal.valueOf(217));

    private final MatchRequestRepository requests;
    private final GameSessionRepository sessions;
    private final UserRepository users;

    @PersistenceContext
    private EntityManager em;

    public MatchService(MatchRequestRepository requests,
                        GameSessionRepository sessions,
                        UserRepository users) {
        this.requests = requests;
        this.sessions = sessions;
        this.users = users;
    }

    /**
     * @return ম্যাচ পেলে GameSession, না পেলে null (অপেক্ষমাণ তালিকায় ঢুকেছে)
     */
    @Transactional
    public GameSession tryMatch(User user, BigDecimal entryFee) {
        if (ALLOWED_FEES.stream().noneMatch(f -> f.compareTo(entryFee) == 0)) {
            throw new IllegalArgumentException("অবৈধ এন্ট্রি ফি");
        }

        // একই ফি-টিয়ারের ম্যাচমেকিং সিরিয়াল করা: দুজন একসাথে এসে কেউ কাউকে না পাওয়ার সমস্যা বন্ধ
        lockTier(entryFee);

        if (hasActiveSession(user)) {
            throw new IllegalStateException("আপনার একটি সক্রিয় ম্যাচ আছে। আগে শেষ করুন।");
        }

        User me = users.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("ব্যবহারকারী পাওয়া যায়নি"));
        if (me.getBalance().compareTo(entryFee) < 0) {
            throw new IllegalStateException("ব্যালেন্স অপর্যাপ্ত");
        }

        requests.deleteOldUnmatchedRequests(LocalDateTime.now().minusSeconds(WAIT_SECONDS));
        requests.deleteWaitingByUser(me);               // একই ইউজারের ডুপ্লিকেট অপেক্ষা নয়

        for (int i = 0; i < 5; i++) {
            Optional<MatchRequest> opt =
                    requests.findFirstByEntryFeeAndMatchedFalseAndUserNotOrderByRequestTimeAsc(entryFee, me);
            if (opt.isEmpty()) break;

            MatchRequest req = opt.get();
            requests.delete(req);                       // রিকোয়েস্ট ব্যবহার হয়ে গেছে

            // অপেক্ষার সময়ে প্রতিপক্ষের ব্যালেন্স/অবস্থা বদলে থাকতে পারে, তাই নতুন করে আনি
            User opponent = users.findById(req.getUser().getId()).orElse(null);
            if (opponent == null) continue;
            if (opponent.getBalance().compareTo(entryFee) < 0) continue;
            if (hasActiveSession(opponent)) continue;

            return createSession(opponent, me, entryFee);
        }

        MatchRequest waiting = new MatchRequest();
        waiting.setUser(me);
        waiting.setEntryFee(entryFee);
        waiting.setMatched(false);
        waiting.setRequestTime(LocalDateTime.now());
        requests.save(waiting);
        return null;
    }

    /** ইউজার সার্চ স্ক্রিন থেকে বেরিয়ে গেলে */
    @Transactional
    public void cancelWaiting(User user) {
        requests.deleteWaitingByUser(user);
    }

    // ---------------------------------------------------------------

    private GameSession createSession(User first, User second, BigDecimal entryFee) {
        GameSession s = new GameSession();
        s.setPlayer1(first);                            // যে আগে অপেক্ষায় ছিল, সে-ই প্রথম চাল দেবে
        s.setPlayer2(second);
        s.setEntryFee(entryFee);
        s.setPrizeByEntryFee();
        s.setStatus(GameStatus.MATCH_FOUND);
        s.setStartTime(LocalDateTime.now());
        s.setMatchStartTimestamp(System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L);
        s.setFeeDeducted(false);
        return sessions.save(s);
    }

    private boolean hasActiveSession(User u) {
        return !sessions.findActiveSessionsByPlayerGameId(u.getGameId()).isEmpty();
    }

    /** PostgreSQL advisory lock, ট্রানজেকশন শেষে নিজে থেকে ছেড়ে দেয় */
    private void lockTier(BigDecimal entryFee) {
        em.createNativeQuery("select count(*) from (select pg_advisory_xact_lock(:k)) t")
                .setParameter("k", LOCK_KEY_BASE + entryFee.longValue())
                .getSingleResult();
    }
}
