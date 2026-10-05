package com.yourcompany.ludo.service;

import com.yourcompany.ludo.repository.GameSessionRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * গেমের প্রবাহ: রোল, টাইমার, বের হওয়া। চলমান স্টেট Redis-এ, টাকার কাজ GameMoneyService-এ (DB)।
 * প্রতিটি মেথড গেম-লকের ভেতরে চলে, তাই একই গেমে দুটো কাজ একসাথে হয় না।
 */
@Service
public class GameFlowService {

    public static final long TURN_MS = 15_000;
    public static final int MAX_MISSES = 3;
    /** ফি কাটার পর অপর জন এর মধ্যে প্রথম রোল না করলে বাতিল + রিফান্ড */
    public static final long FIRST_ROLL_TIMEOUT_MS = 60_000;

    private final GameStateStore store;
    private final GameLock lock;
    private final GameMoneyService money;
    private final SecureRandom rnd = new SecureRandom();

    public GameFlowService(GameStateStore store, GameLock lock, GameMoneyService money,
                           GameSessionRepository unused) {
        this.store = store;
        this.lock = lock;
        this.money = money;
    }

    public record RollResult(int dice, boolean cancelled, String message,
                             int player, int nextPlayer, boolean canMove) {}

    public enum Kind { NONE, ROLLED, NEED_MOVE, FORFEITED }

    public record Expired(Kind kind, int actor, String gameId, int token, RollResult roll) {
        static Expired none() { return new Expired(Kind.NONE, 0, null, -1, null); }
    }

    // =====================================================================
    // 1) MATCH_FOUND -> ONGOING (GameCleanupTask কল করে)
    // =====================================================================
    public void startIfReady(Long sid) {
        lock.with(sid, () -> {
            String[] ids = money.markOngoing(sid);
            if (ids == null) return null;
            GameState s = new GameState();
            s.id = sid;
            s.g1 = ids[0];
            s.g2 = ids[1];
            s.deadline = System.currentTimeMillis() + TURN_MS;
            store.save(s);
            return null;
        });
    }

    // =====================================================================
    // 2) ডাইস রোল। প্রথম রোলে দুজনের ফি কাটে
    // =====================================================================
    public RollResult rollDice(Long sid, String gameId) {
        return lock.with(sid, () -> {
            GameState s = store.load(sid);
            if (s == null) throw new IllegalStateException("Game not active");
            int me = s.slotOf(gameId);
            if (s.currentPlayer != me) throw new IllegalStateException("Not your turn");
            if (s.pendingMove) throw new IllegalStateException("Previous move pending");
            s.misses[me] = 0;
            RollResult r = doRoll(s, me);
            if (!r.cancelled()) store.save(s);
            return r;
        });
    }

    /** রোলের মূল লজিক (ম্যানুয়াল ও অটো দুটোতেই একই)। লকের ভেতরে ডাকতে হবে */
    private RollResult doRoll(GameState s, int me) {
        if (!s.feeDeducted) {
            if (!money.chargeEntryFee(s.id)) {              // DB: ব্যালেন্স কম, বাতিল হয়েছে
                store.delete(s.id);
                return new RollResult(0, true, "Insufficient balance", me, s.currentPlayer, false);
            }
            s.feeDeducted = true;
            s.firstRollAt = System.currentTimeMillis();
        }

        int[] mine = s.tokens(me);
        boolean allYardBefore = DicePicker.allInYard(mine);
        boolean allowSix = s.sixCount < 2;                   // পরপর তিনটি ৬ নয়

        int dice = DicePicker.pick(rnd, me, mine, s.noSix[me], allowSix);

        s.sixCount = dice == 6 ? s.sixCount + 1 : 0;
        s.noSix[me] = allYardBefore ? (dice == 6 ? 0 : s.noSix[me] + 1) : 0;
        s.diceCount[me]++;
        s.lastDice = dice;
        s.diceOwner = me;

        boolean canMove = LudoRules.hasLegalMove(mine, dice);
        s.pendingMove = canMove;

        int other = me == 1 ? 2 : 1;
        int next;
        if (!canMove && dice == 6) {
            next = other;                                    // ৬ বাতিল, বোনাস রোল নেই
            s.sixCount = 0;
        } else {
            next = dice == 6 ? me : other;
        }
        s.currentPlayer = next;
        s.deadline = System.currentTimeMillis() + TURN_MS;

        return new RollResult(dice, false, "OK", me, next, canMove);
    }

    // =====================================================================
    // 3) ১৫ সেকেন্ডের টাইমার (TurnTicker কল করে)
    // =====================================================================
    public Expired expireTurn(Long sid) {
        return lock.with(sid, () -> {
            GameState s = store.load(sid);
            if (s == null) return Expired.none();

            long now = System.currentTimeMillis();
            if (s.deadline == 0 || s.deadline > now) {       // কেউ ইতিমধ্যে খেলেছে
                store.save(s);                               // ticker মুছে ফেলেছিল, টাইমার আবার বসাও
                return Expired.none();
            }

            // দুজন রোল না করা পর্যন্ত অটো বন্ধ
            if (!s.bothRolled()) {
                if (s.feeDeducted && now - s.firstRollAt > FIRST_ROLL_TIMEOUT_MS) {
                    money.refundAndCancel(sid, "প্রতিপক্ষ রোল না করায় ম্যাচ বাতিল, ফি ফেরত");
                    store.delete(sid);
                    return Expired.none();
                }
                s.deadline = now + TURN_MS;
                store.save(s);
                return Expired.none();
            }

            int actor = s.pendingMove ? s.diceOwner : s.currentPlayer;
            String gid = actor == 1 ? s.g1 : s.g2;

            s.misses[actor]++;
            if (s.misses[actor] >= MAX_MISSES) {
                quit(s, actor, "৩ বার চাল মিস, প্রতিপক্ষ জিতেছে");
                return new Expired(Kind.FORFEITED, actor, gid, -1, null);
            }
            s.auto[actor]++;

            if (s.pendingMove) {
                s.deadline = now + TURN_MS;                  // চাল ব্যর্থ হলেও লুপ নয়
                int tok = DicePicker.autoToken(actor, s.tokens(actor), s.lastDice);
                if (tok < 0 || LudoRules.target(s.tokens(actor)[tok], s.lastDice) == -1) {
                    s.pendingMove = false;                   // নিরাপত্তা: চালার মতো গুটি নেই
                    s.currentPlayer = actor == 1 ? 2 : 1;
                    store.save(s);
                    return Expired.none();
                }
                store.save(s);
                return new Expired(Kind.NEED_MOVE, actor, gid, tok, null);
            }

            RollResult r = doRoll(s, actor);
            if (!r.cancelled()) store.save(s);
            return new Expired(Kind.ROLLED, actor, gid, -1, r);
        });
    }

    // =====================================================================
    // 4) বাক / ডিসকানেক্ট
    // =====================================================================
    public void leave(Long sid, String gameId) {
        lock.with(sid, () -> {
            GameState s = store.load(sid);
            if (s == null) {
                // গেম এখনো শুরু হয়নি (MATCH_FOUND) বা শেষ। ফি না কাটা থাকলে শুধু বাতিল
                money.cancelIfIdle(sid, gameId, "ম্যাচ বাতিল");
                return null;
            }
            quit(s, s.slotOf(gameId), "প্রতিপক্ষ বের হয়ে গেছে");
            return null;
        });
    }

    /** leaver হারে। কেউ রোল না করলে বাতিল, একজন করলে রিফান্ড, দুজন করলে প্রতিপক্ষ জেতে */
    private void quit(GameState s, int leaver, String loseMsg) {
        if (!s.feeDeducted) {
            money.refundAndCancel(s.id, "ম্যাচ বাতিল");
        } else if (!s.bothRolled()) {
            money.refundAndCancel(s.id, "ম্যাচ বাতিল, ফি ফেরত দেওয়া হয়েছে");
        } else {
            money.payout(s.id, leaver == 1 ? 2 : 1, loseMsg);
        }
        store.delete(s.id);
    }

    // =====================================================================
    // 5) কেউ রোল করেনি, ফি কাটা হয়নি, শুধু বাতিল (GameCleanupTask ও InactiveMatchScheduler কল করে)
    // =====================================================================
    public void cancelIdle(Long sid) {
        lock.with(sid, () -> {
            if (money.cancelIfIdle(sid, null, "কেউ খেলা শুরু না করায় ম্যাচ বাতিল")) {
                store.delete(sid);
            }
            return null;
        });
    }
}
