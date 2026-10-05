package com.yourcompany.ludo.service;

/** চলমান গেমের পুরো স্টেট। Redis-এ JSON হয়ে থাকে। slot ১ ও ২ ব্যবহার হয় (index 0 ফাঁকা) */
public class GameState {
    public long id;
    public String g1, g2;
    public int currentPlayer = 1;
    public int[] p1 = {0, 0, 0, 0};
    public int[] p2 = {0, 0, 0, 0};
    public int lastDice, diceOwner, sixCount;
    public boolean pendingMove, feeDeducted;
    public int[] misses = new int[3];
    public int[] noSix = new int[3];
    public int[] auto = new int[3];
    public int[] diceCount = new int[3];
    public long deadline;        // epoch millis, 0 = টাইমার নেই
    public long firstRollAt;     // ফি কাটার সময়, epoch millis

    public int[] tokens(int slot) { return slot == 1 ? p1 : p2; }

    public boolean bothRolled() { return diceCount[1] >= 1 && diceCount[2] >= 1; }

    public int slotOf(String gameId) {
        if (g1 != null && g1.equals(gameId)) return 1;
        if (g2 != null && g2.equals(gameId)) return 2;
        throw new IllegalArgumentException("You are not a player of this game");
    }
}
