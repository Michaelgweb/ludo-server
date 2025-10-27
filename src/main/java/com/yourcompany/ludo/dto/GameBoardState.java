package com.yourcompany.ludo.dto;

public class GameBoardState {
    private int[][] playerTokens;
    private int currentPlayer;
    private boolean gameOver;
    private Integer lastDiceValue;
    private Integer diceOwner;

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
    public Integer getLastDiceValue() { return lastDiceValue; }
    public void setLastDiceValue(Integer lastDiceValue) { this.lastDiceValue = lastDiceValue; }
    public Integer getDiceOwner() { return diceOwner; }
    public void setDiceOwner(Integer diceOwner) { this.diceOwner = diceOwner; }
}