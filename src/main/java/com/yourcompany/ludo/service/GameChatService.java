package com.yourcompany.ludo.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * গেমের ভেতরের চ্যাট (মেসেজ + ইমোজি)। কিছুই সেভ হয় না; শুধু যাচাই করে প্রতিপক্ষকে পাঠানো হয়।
 */
@Service
public class GameChatService {

    public static final int MAX_LEN = 60;
    private static final long MIN_GAP_MS = 800;

    private final GameStateStore store;

    /** শুধু মেমরিতে: কে সর্বশেষ কখন পাঠিয়েছে */
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public GameChatService(GameStateStore store) {
        this.store = store;
    }

    public record ChatMsg(int from, String text, long id) {}

    public ChatMsg validate(Long sid, String gameId, String raw) {
        GameState s = store.load(sid);
        if (s == null) throw new IllegalStateException("Game not active");

        int slot = s.slotOf(gameId);                     // ম্যাচের খেলোয়াড় না হলে IllegalArgumentException

        String text = clean(raw);

        long now = System.currentTimeMillis();
        String key = sid + ":" + slot;
        Long last = lastSent.get(key);
        if (last != null && now - last < MIN_GAP_MS) {
            throw new IllegalStateException("Too fast");
        }
        lastSent.put(key, now);
        if (lastSent.size() > 2000) {
            lastSent.entrySet().removeIf(e -> now - e.getValue() > 600_000);
        }

        return new ChatMsg(slot, text, now);
    }

    private String clean(String raw) {
        if (raw == null) throw new IllegalArgumentException("Empty message");

        String t = raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Empty message");

        if (t.codePointCount(0, t.length()) > MAX_LEN) {
            t = t.substring(0, t.offsetByCodePoints(0, MAX_LEN));
        }
        return t;
    }
}
