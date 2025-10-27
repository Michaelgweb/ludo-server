package com.yourcompany.ludo.dto;

public class GameMoveRequest {
    private Long gameId;
    private int playerId;
    private int tokenIndex;
    private int diceValue;

    // getters/setters
    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }

    public int getTokenIndex() { return tokenIndex; }
    public void setTokenIndex(int tokenIndex) { this.tokenIndex = tokenIndex; }

    public int getDiceValue() { return diceValue; }
    public void setDiceValue(int diceValue) { this.diceValue = diceValue; }
}
