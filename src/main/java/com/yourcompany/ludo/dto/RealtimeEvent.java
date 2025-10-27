package com.yourcompany.ludo.dto;

import java.time.Instant;
import java.util.Map;

public class RealtimeEvent {
    private String type;        // e.g. PROFILE_UPDATED, BALANCE_UPDATED, DEPOSIT_REQUESTED
    private String gameId;      // target user
    private Object payload;     // any DTO / Map
    private Instant ts = Instant.now();

    public RealtimeEvent() {}

    public RealtimeEvent(String type, String gameId, Object payload) {
        this.type = type;
        this.gameId = gameId;
        this.payload = payload;
    }

    public String getType() { return type; }
    public String getGameId() { return gameId; }
    public Object getPayload() { return payload; }
    public Instant getTs() { return ts; }

    public void setType(String type) { this.type = type; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public void setPayload(Object payload) { this.payload = payload; }
    public void setTs(Instant ts) { this.ts = ts; }
}
