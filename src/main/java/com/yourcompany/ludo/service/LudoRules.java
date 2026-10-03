package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;

import java.util.List;
import java.util.Set;

/**
 * বোর্ডের নিয়ম (standard)। কোনো ডেটাবেস বা সার্ভিস নির্ভরতা নেই, তাই
 * GameFlowService ও GameMoveService দুজনেই নিরাপদে ব্যবহার করতে পারে।
 *
 * পজিশন: 0 = ঘরে (yard), 1 = নিজের স্টার্ট ঘর, 1..51 = শেয়ার্ড ট্র্যাক,
 *        52..56 = নিজের হোম কলাম (নিরাপদ), 57 = পৌঁছে গেছে।
 */
public final class LudoRules {

    private LudoRules() {}

    public static final int YARD = 0;
    public static final int TRACK_END = 51;
    public static final int HOME = 57;

    // slot 1 ট্র্যাকের 0 নম্বর ঘর থেকে শুরু, slot 2 বিপরীত দিক (26) থেকে
    private static final int[] START_OFFSET = {0, 26};

    // সেফ ঘর: চার রঙের স্টার্ট ঘর + চারটি স্টার ঘর
    private static final Set<Integer> SAFE = Set.of(0, 8, 13, 21, 26, 34, 39, 47);

    /** স্লটের ৪টি টোকেন (পুরনো ডেটায় কম থাকলে ০ দিয়ে ভরে নেয়) */
    public static List<Integer> tokens(GameSession s, int slot) {
        List<Integer> t = slot == 1 ? s.getPlayer1Tokens() : s.getPlayer2Tokens();
        while (t.size() < 4) t.add(YARD);
        return t;
    }

    /** pos থেকে dice চাললে নতুন পজিশন; চাল অবৈধ হলে -1 */
    public static int target(int pos, int dice) {
        if (pos == YARD) return dice == 6 ? 1 : -1;   // বের করতে ৬ লাগে
        if (pos >= HOME) return -1;
        int np = pos + dice;
        return np > HOME ? -1 : np;                   // ঠিক সংখ্যায় পৌঁছাতে হয়
    }

    public static boolean hasLegalMove(GameSession s, int slot, int dice) {
        for (int p : tokens(s, slot)) {
            if (target(p, dice) != -1) return true;
        }
        return false;
    }

    /** শেয়ার্ড ট্র্যাকের ঘর নম্বর (0..51); ট্র্যাকে না থাকলে -1 */
    public static int globalCell(int slot, int pos) {
        if (pos < 1 || pos > TRACK_END) return -1;
        return (pos - 1 + START_OFFSET[slot - 1]) % 52;
    }

    public static boolean isSafe(int cell) {
        return SAFE.contains(cell);
    }
}
