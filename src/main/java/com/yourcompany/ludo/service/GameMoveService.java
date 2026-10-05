package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameStatus;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * টোকেন চাল যাচাই, কাটা ও জেতা ধরা। ক্লায়েন্ট শুধু টোকেন নম্বর (০-৩) পাঠায়।
 * পুরো কাজ গেম-লকের ভেতরে, Redis স্টেটে।
 */
@Service
public class GameMoveService {

    private final GameStateStore store;
    private final GameLock lock;
    private final GameMoneyService money;

    public GameMoveService(GameStateStore store, GameLock lock, GameMoneyService money) {
        this.store = store;
        this.lock = lock;
        this.money = money;
    }

    public record MoveResult(int player, int token, int from, int to, int captured,
                             boolean finished, String winnerGameId, int nextPlayer,
                             List<Integer> player1Tokens, List<Integer> player2Tokens) {}

    /**
     * @param manual true = খেলোয়াড় নিজে চেপেছে (মিস রিসেট), false = টাইমারের অটো চাল (রিসেট নয়)
     */
    public MoveResult move(Long sid, String gameId, int tokenIndex, boolean manual) {
        return lock.with(sid, () -> {
            GameState s = store.load(sid);
            if (s == null) throw new IllegalStateException("Game not active");

            int me = s.slotOf(gameId);
            int other = me == 1 ? 2 : 1;

            // ডাইস যে ফেলেছে, চালও সেই দেবে (পালা আগেই বদলে যেতে পারে)
            if (!s.pendingMove || s.diceOwner != me) {
                throw new IllegalStateException("No move pending");
            }
            if (tokenIndex < 0 || tokenIndex > 3) {
                throw new IllegalArgumentException("Invalid token");
            }

            int dice = s.lastDice;
            int[] mine = s.tokens(me);
            int[] opp = s.tokens(other);

            int from = mine[tokenIndex];
            int to = LudoRules.target(from, dice);
            if (to == -1) throw new IllegalStateException("Invalid move for this token");
            mine[tokenIndex] = to;

            // কাটা: শেয়ার্ড ট্র্যাকে, সেফ ঘর না হলে প্রতিপক্ষের টোকেন ঘরে ফেরত
            int captured = 0;
            int cell = LudoRules.globalCell(me, to);
            if (cell != -1 && !LudoRules.isSafe(cell)) {
                for (int i = 0; i < 4; i++) {
                    if (LudoRules.globalCell(other, opp[i]) == cell) {
                        opp[i] = LudoRules.YARD;
                        captured++;
                    }
                }
            }

            s.pendingMove = false;
            if (manual) s.misses[me] = 0;
            s.deadline = System.currentTimeMillis() + TURN_MS();

            boolean won = true;
            for (int p : mine) {
                if (p != LudoRules.HOME) { won = false; break; }
            }

            if (won) {
                // DB তে পেমেন্ট আগে, সফল হলে তবেই Redis স্টেট মোছা
                money.payout(sid, me, "ম্যাচ শেষ");
                store.delete(sid);
            } else {
                if (captured > 0 || to == LudoRules.HOME) {
                    s.currentPlayer = me;                    // বোনাস রোল
                }
                store.save(s);
            }

            return new MoveResult(me, tokenIndex, from, to, captured, won,
                    won ? gameId : null, s.currentPlayer, toList(s.p1), toList(s.p2));
        });
    }

    /** রিকানেক্ট / অ্যাপ রিস্টার্টের পর অবস্থা সিঙ্ক। শুধু ওই ম্যাচের খেলোয়াড় দেখতে পায় */
    public Map<String, Object> state(Long sid, String gameId) {
        GameState s = store.load(sid);
        Map<String, Object> m = new HashMap<>();

        if (s != null) {
            int me = s.slotOf(gameId);                       // অন্য কেউ হলে IllegalArgumentException
            m.put("sessionId", sid);
            m.put("status", GameStatus.ONGOING);
            m.put("mySlot", me);
            m.put("currentPlayer", s.currentPlayer);
            m.put("pendingMove", s.pendingMove);
            m.put("diceOwner", s.diceOwner == 0 ? null : s.diceOwner);
            m.put("lastDiceValue", s.lastDice);
            m.put("player1Tokens", toList(s.p1));
            m.put("player2Tokens", toList(s.p2));
            m.put("winnerGameId", null);
            m.put("player1Misses", s.misses[1]);
            m.put("player2Misses", s.misses[2]);
            m.put("player1Auto", s.auto[1]);
            m.put("player2Auto", s.auto[2]);
            m.put("maxMisses", GameFlowService.MAX_MISSES);
            m.put("bothRolled", s.bothRolled());
            m.put("turnDeadline", s.deadline == 0 ? null : s.deadline);
        } else {
            // শুরুর আগে (MATCH_FOUND) বা শেষ হওয়া ম্যাচ: DB থেকে
            GameMoneyService.Snapshot snap = money.snapshot(sid, gameId);
            m.put("sessionId", sid);
            m.put("status", snap.status());
            m.put("mySlot", snap.slot());
            m.put("currentPlayer", 1);
            m.put("pendingMove", false);
            m.put("diceOwner", null);
            m.put("lastDiceValue", 0);
            m.put("player1Tokens", List.of(0, 0, 0, 0));
            m.put("player2Tokens", List.of(0, 0, 0, 0));
            m.put("winnerGameId", snap.winnerGameId());
            m.put("player1Misses", 0);
            m.put("player2Misses", 0);
            m.put("player1Auto", 0);
            m.put("player2Auto", 0);
            m.put("maxMisses", GameFlowService.MAX_MISSES);
            m.put("bothRolled", false);
            m.put("turnDeadline", null);
        }
        m.put("serverTime", System.currentTimeMillis());
        return m;
    }

    private static long TURN_MS() { return GameFlowService.TURN_MS; }

    private static List<Integer> toList(int[] a) {
        List<Integer> l = new ArrayList<>(a.length);
        for (int v : a) l.add(v);
        return l;
    }
}
