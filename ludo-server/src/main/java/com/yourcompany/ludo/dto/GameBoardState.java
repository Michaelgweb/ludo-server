package com.yourcompany.ludo.dto;

public class GameBoardState {
    private int[][] playerTokens;
    private int currentPlayer;
    private boolean gameOver;

    public GameBoardState() {}

    public GameBoardState(int[][] playerTokens, int currentPlayer, boolean gameOver) {
        this.playerTokens = playerTokens;
        this.currentPlayer = currentPlayer;
        this.gameOver = gameOver;
    }

    public int[][] getPlayerTokens() { return playerTokens; }
    public void setPlayerTokens(int[][] playerTokens) { this.playerTokens = playerTokens; }

    public int getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(int currentPlayer) { this.currentPlayer = currentPlayer; }

    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
}
