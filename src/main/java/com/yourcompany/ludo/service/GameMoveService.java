package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.repository.GameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * টোকেন চাল যাচাই, কাটা ও জেতা ধরা। ক্লায়েন্ট শুধু টোকেন নম্বর (০-৩) পাঠায়,
 * বাকি সবকিছু সার্ভার ঠিক করে।
 */
@Service
public class GameMoveService {

    private final GameSessionRepository sessions;
    private final GameFlowService flow;

    public GameMoveService(GameSessionRepository sessions, GameFlowService flow) {
        this.sessions = sessions;
        this.flow = flow;
    }

    public record MoveResult(int player, int token, int from, int to, int captured,
                             boolean finished, String winnerGameId, int nextPlayer,
                             List<Integer> player1Tokens, List<Integer> player2Tokens) {}

    @Transactional
    public MoveResult move(Long sid, String gameId, int tokenIndex) {
        GameSession s = sessions.findByIdForUpdate(sid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (s.getStatus() != GameStatus.ONGOING) {
            throw new IllegalStateException("Game not active");
        }
        int me = s.slotOf(gameId);
        int other = me == 1 ? 2 : 1;

        // ডাইস যে ফেলেছে, চালও সেই দেবে (পালা আগেই বদলে যেতে পারে)
        if (!s.isPendingMove() || s.getDiceOwner() == null || s.getDiceOwner() != me) {
            throw new IllegalStateException("No move pending");
        }
        if (tokenIndex < 0 || tokenIndex > 3) {
            throw new IllegalArgumentException("Invalid token");
        }

        int dice = s.getLastDiceValue();
        List<Integer> mine = LudoRules.tokens(s, me);
        List<Integer> opp = LudoRules.tokens(s, other);

        int from = mine.get(tokenIndex);
        int to = LudoRules.target(from, dice);
        if (to == -1) {
            throw new IllegalStateException("Invalid move for this token");
        }
        mine.set(tokenIndex, to);

        // কাটা: শেয়ার্ড ট্র্যাকে, সেফ ঘর না হলে প্রতিপক্ষের টোকেন ঘরে ফেরত
        int captured = 0;
        int cell = LudoRules.globalCell(me, to);
        if (cell != -1 && !LudoRules.isSafe(cell)) {
            for (int i = 0; i < 4; i++) {
                if (LudoRules.globalCell(other, opp.get(i)) == cell) {
                    opp.set(i, LudoRules.YARD);
                    captured++;
                }
            }
        }

        s.setPendingMove(false);

        // চারটি টোকেনই পৌঁছালে সার্ভার নিজেই বিজয়ী ঘোষণা করে (একই ট্রানজেকশনে)
        boolean won = true;
        for (int p : mine) {
            if (p != LudoRules.HOME) { won = false; break; }
        }
        if (won) {
            flow.declareWinner(sid, gameId);
        } else if (captured > 0 || to == LudoRules.HOME) {
            // প্রতিপক্ষের গুটি কাটলে বা গুটি ৫৭ ঘরে উঠলে বোনাস রোল (পালা নিজের কাছেই থাকে)
            s.setCurrentPlayer(me);
        }

        return new MoveResult(me, tokenIndex, from, to, captured, won,
                won ? gameId : null, s.getCurrentPlayer(),
                new ArrayList<>(s.getPlayer1Tokens()), new ArrayList<>(s.getPlayer2Tokens()));
    }

    /**
     * রিকানেক্ট / অ্যাপ রিস্টার্টের পর ক্লায়েন্টের অবস্থা সিঙ্ক করার জন্য।
     * শুধু ওই ম্যাচের খেলোয়াড় দেখতে পাবে (slotOf অন্য কেউ হলে IllegalArgumentException)।
     */
    @Transactional(readOnly = true)
    public Map<String, Object> state(Long sid, String gameId) {
        GameSession s = sessions.findById(sid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        int me = s.slotOf(gameId);

        Map<String, Object> m = new HashMap<>();
        m.put("sessionId", s.getId());
        m.put("status", s.getStatus());
        m.put("mySlot", me);
        m.put("currentPlayer", s.getCurrentPlayer());
        m.put("pendingMove", s.isPendingMove());
        m.put("diceOwner", s.getDiceOwner());
        m.put("lastDiceValue", s.getLastDiceValue());
        m.put("player1Tokens", new ArrayList<>(s.getPlayer1Tokens()));
        m.put("player2Tokens", new ArrayList<>(s.getPlayer2Tokens()));
        m.put("winnerGameId", s.getWinner() != null ? s.getWinner().getGameId() : null);

        // মিস, অটো দান ও ১৫ সেকেন্ডের টাইমার (UI এর জন্য)
        m.put("player1Misses", s.getMisses(1));
        m.put("player2Misses", s.getMisses(2));
        m.put("player1Auto", s.getAutoCount(1));
        m.put("player2Auto", s.getAutoCount(2));
        m.put("maxMisses", GameFlowService.MAX_MISSES);
        m.put("bothRolled", s.isBothRolled());
        m.put("turnDeadline", s.getTurnDeadline());

        m.put("serverTime", System.currentTimeMillis());
        return m;
    }
}
