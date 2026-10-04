package com.yourcompany.ludo.service;

import com.yourcompany.ludo.service.GameFlowService.RollResult;
import com.yourcompany.ludo.service.GameMoveService.MoveResult;

import java.util.HashMap;
import java.util.Map;

/** WebSocket/REST এর একই payload, কন্ট্রোলার ও অটো-টাইমার দুজনেই ব্যবহার করবে */
public final class GamePayloads {
    private GamePayloads() {}

    public static Map<String, Object> roll(Long sid, RollResult r) {
        Map<String, Object> p = new HashMap<>();
        p.put("sessionId", sid);
        p.put("event", r.cancelled() ? "GAME_CANCELLED" : "DICE_ROLLED");
        p.put("dice", r.dice());
        p.put("rolledBy", r.player());
        p.put("nextPlayer", r.nextPlayer());
        p.put("canMove", r.canMove());
        p.put("message", r.message());
        return p;
    }

    public static Map<String, Object> move(Long sid, MoveResult r) {
        Map<String, Object> p = new HashMap<>();
        p.put("sessionId", sid);
        p.put("event", "TOKEN_MOVED");
        p.put("movedBy", r.player());
        p.put("token", r.token());
        p.put("from", r.from());
        p.put("to", r.to());
        p.put("captured", r.captured());
        p.put("nextPlayer", r.nextPlayer());
        p.put("finished", r.finished());
        p.put("winnerGameId", r.winnerGameId());
        p.put("player1Tokens", r.player1Tokens());
        p.put("player2Tokens", r.player2Tokens());
        return p;
    }
}
