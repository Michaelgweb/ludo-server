package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.repository.GameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * গেমের ভেতরের চ্যাট (মেসেজ + ইমোজি)।
 * কিছুই ডাটাবেসে বা ফাইলে সেভ হয় না; শুধু যাচাই করে প্রতিপক্ষকে পাঠানো হয়।
 * মুছে ফেলার কাজ (১ মিনিট / নতুন মেসেজ / গেম শেষ) ক্লায়েন্টে হয়।
 */
@Service
public class GameChatService {

    public static final int MAX_LEN = 60;          // অক্ষর (ইমোজি ১টা = ১)
    private static final long MIN_GAP_MS = 800;    // স্প্যাম আটকাতে

    private final GameSessionRepository sessions;

    /** শুধু মেমরিতে: কে সর্বশেষ কখন পাঠিয়েছে (মেসেজের লেখা রাখা হয় না) */
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public GameChatService(GameSessionRepository sessions) {
        this.sessions = sessions;
    }

    public record ChatMsg(int from, String text, long id) {}

    @Transactional(readOnly = true)
    public ChatMsg validate(Long sid, String gameId, String raw) {
        GameSession s = sessions.findById(sid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (s.getStatus() != GameStatus.ONGOING) {
            throw new IllegalStateException("Game not active");
        }

        int slot = s.slotOf(gameId);                   // ম্যাচের খেলোয়াড় না হলে IllegalArgumentException

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
 
