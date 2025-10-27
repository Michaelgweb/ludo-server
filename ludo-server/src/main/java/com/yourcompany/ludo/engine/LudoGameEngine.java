package com.yourcompany.ludo.engine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class LudoGameEngine {

    private final int[][] playerTokens = new int[2][4]; // 2 players, 4 tokens each
    private int currentPlayer = 0;
    private boolean gameOver = false;
    private final int[] missedTurns = new int[2];
    private int winner = -1;

    // Safe spot positions in Ludo
    private static final Set<Integer> SAFE_SPOTS = new HashSet<>(Arrays.asList(
            0, 8, 13, 21, 26, 34, 39, 47
    ));

    public LudoGameEngine() {
        for (int[] tokens : playerTokens) {
            Arrays.fill(tokens, -1); // -1 means in home
        }
    }

    public int rollDice() {
        return new Random().nextInt(6) + 1;
    }

    public boolean moveToken(int playerId, int tokenIndex, int diceValue) {
        if (playerId != currentPlayer || gameOver) return false;

        int pos = playerTokens[playerId][tokenIndex];

        // Rule: 6 needed to take token out of home
        if (pos == -1 && diceValue == 6) {
            playerTokens[playerId][tokenIndex] = 0;
        }
        // Normal move
        else if (pos >= 0 && pos + diceValue <= 57) {
            playerTokens[playerId][tokenIndex] += diceValue;
        } else {
            return false; // Invalid move
        }

        // Check kill rule
        checkForKill(playerId, tokenIndex);

        // Check win condition
        if (playerTokens[playerId][tokenIndex] == 57 && isPlayerWinner(playerId)) {
            gameOver = true;
            winner = playerId;
        }

        missedTurns[playerId] = 0;
        // If not 6, change turn
        if (diceValue != 6) {
            currentPlayer = (currentPlayer + 1) % 2;
        }

        return true;
    }

    private void checkForKill(int playerId, int tokenIndex) {
        int pos = playerTokens[playerId][tokenIndex];
        if (pos <= 0 || pos >= 57 || SAFE_SPOTS.contains(pos)) {
            return; // No kill on safe spots or home
        }
        int opponentId = (playerId + 1) % 2;
        for (int i = 0; i < 4; i++) {
            if (playerTokens[opponentId][i] == pos) {
                playerTokens[opponentId][i] = -1; // send opponent token home
                break;
            }
        }
    }

    public boolean autoMove() {
        if (gameOver) return false;

        int dice = rollDice();
        for (int i = 0; i < 4; i++) {
            if (moveToken(currentPlayer, i, dice)) {
                return true;
            }
        }

        missedTurns[currentPlayer]++;
        if (missedTurns[currentPlayer] >= 3) {
            gameOver = true;
            winner = (currentPlayer + 1) % 2;
        }

        currentPlayer = (currentPlayer + 1) % 2;
        return false;
    }

    public void forceGameOverDueToLeave() {
        if (!gameOver) {
            gameOver = true;
            winner = (currentPlayer + 1) % 2;
        }
    }

    public boolean isPlayerWinner(int playerId) {
        for (int pos : playerTokens[playerId]) {
            if (pos != 57) return false;
        }
        return true;
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public int[][] getPlayerTokens() {
        return playerTokens;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getWinner() {
        return winner;
    }
}
