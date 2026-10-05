package com.yourcompany.ludo.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ডাইস বাছাইয়ের নিয়ম (সার্ভারে)।
 *  1) সব গুটি ঘরে (pos 0) থাকলে: প্রথম ৩ রোল স্বাভাবিক। ৪র্থ রোলে ৫০% ৬, ৫ম রোলে নিশ্চিত ৬।
 *  2) সেফ ঘর ছাড়া নিজের দুটো গুটি এক ঘরে হয়ে যায় এমন সংখ্যা পড়বে না।
 *     (সব সংখ্যাই ডাবল বানালে তখন বাধ্য হয়ে স্বাভাবিক র‍্যান্ডম)
 */
public final class DicePicker {

    private DicePicker() {}

    public static final int HOME = 57;

    private static final Set<Integer> SAFE_TRACK = Set.of(0, 8, 13, 21, 26, 34, 39, 47);

    /** গুটি p তে dice v দিলে কোথায় যাবে; অবৈধ হলে -1 */
    static int dest(int p, int v) {
        if (p == 0) return v == 6 ? 1 : -1;
        if (p < HOME && p + v <= HOME) return p + v;
        return -1;
    }

    /** ট্র্যাকের সেফ ঘর? (৫২+ হোম কলাম ও ৫৭ হোম সবসময় নিরাপদ) */
    static boolean safeCell(int slot, int pos) {
        if (pos <= 0 || pos >= 52) return true;
        int off = slot == 1 ? 0 : 26;
        return SAFE_TRACK.contains((pos - 1 + off) % 52);
    }

    static boolean makesDouble(int slot, int[] mine, int i, int d) {
        if (d < 0 || safeCell(slot, d)) return false;
        for (int j = 0; j < mine.length; j++) {
            if (j != i && mine[j] == d) return true;
        }
        return false;
    }

    static boolean anyDouble(int slot, int[] mine, int v) {
        for (int i = 0; i < mine.length; i++) {
            int d = dest(mine[i], v);
            if (d >= 0 && makesDouble(slot, mine, i, d)) return true;
        }
        return false;
    }

    public static boolean allInYard(int[] mine) {
        for (int p : mine) if (p != 0) return false;
        return true;
    }

    /**
     * @param noSixStreak সব গুটি ঘরে থাকা অবস্থায় পরপর কতবার ৬ পড়েনি
     * @param allowSix    পরপর ৩ ছক্কা আটকাতে false
     */
    public static int pick(SecureRandom rnd, int slot, int[] mine,
                           int noSixStreak, boolean allowSix) {
        boolean excludeSix = !allowSix;

        if (allInYard(mine) && allowSix) {
            if (noSixStreak >= 4) return 6;
            if (noSixStreak == 3) {
                if (rnd.nextBoolean()) return 6;
                excludeSix = true;
            }
        }

        int max = excludeSix ? 5 : 6;
        List<Integer> all = new ArrayList<>();
        List<Integer> ok = new ArrayList<>();
        for (int v = 1; v <= max; v++) {
            all.add(v);
            if (!anyDouble(slot, mine, v)) ok.add(v);
        }
        List<Integer> pool = ok.isEmpty() ? all : ok;
        return pool.get(rnd.nextInt(pool.size()));
    }

    /** অটো-চালের গুটি: হোমে পৌঁছানো > ডাবল এড়ানো > ঘর থেকে বের > এগিয়ে থাকা */
    public static int autoToken(int slot, int[] mine, int value) {
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < mine.length; i++) {
            int d = dest(mine[i], value);
            if (d < 0) continue;
            int score = d
                    + (d == HOME ? 1000 : 0)
                    + (makesDouble(slot, mine, i, d) ? -500 : 0)
                    + (mine[i] == 0 ? 50 : 0);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }
}
