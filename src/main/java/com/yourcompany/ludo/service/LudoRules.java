package com.yourcompany.ludo.service;

import java.util.Set;

/**
 * বোর্ডের নিয়ম। কোনো DB/Redis নির্ভরতা নেই।
 * পজিশন: 0 = ঘরে (yard), 1..51 = শেয়ার্ড ট্র্যাক, 52..56 = নিজের হোম কলাম, 57 = পৌঁছে গেছে।
 */
public final class LudoRules {

    private LudoRules() {}

    public static final int YARD = 0;
    public static final int TRACK_END = 51;
    public static final int HOME = 57;

    private static final int[] START_OFFSET = {0, 26};
    private static final Set<Integer> SAFE = Set.of(0, 8, 13, 21, 26, 34, 39, 47);

    /** pos থেকে dice চাললে নতুন পজিশন; চাল অবৈধ হলে -1 */
    public static int target(int pos, int dice) {
        if (pos == YARD) return dice == 6 ? 1 : -1;
        if (pos >= HOME) return -1;
        int np = pos + dice;
        return np > HOME ? -1 : np;
    }

    public static boolean hasLegalMove(int[] mine, int dice) {
        for (int p : mine) {
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
