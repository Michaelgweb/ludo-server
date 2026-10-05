package com.yourcompany.ludo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class GameStateStore {

    private static final String DEADLINES = "game:deadlines";
    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public GameStateStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om = om;
    }

    public GameState load(long id) {
        try {
            String j = redis.opsForValue().get("game:" + id);
            return j == null ? null : om.readValue(j, GameState.class);
        } catch (Exception e) {
            throw new IllegalStateException("state read failed", e);
        }
    }

    /** স্টেট সেভ + টাইমার তালিকায় deadline বসানো (deadline = 0 হলে তালিকা থেকে বাদ) */
    public void save(GameState s) {
        try {
            redis.opsForValue().set("game:" + s.id, om.writeValueAsString(s), Duration.ofHours(3));
            if (s.deadline > 0) redis.opsForZSet().add(DEADLINES, String.valueOf(s.id), s.deadline);
            else redis.opsForZSet().remove(DEADLINES, String.valueOf(s.id));
        } catch (Exception e) {
            throw new IllegalStateException("state write failed", e);
        }
    }

    public void delete(long id) {
        redis.delete("game:" + id);
        redis.opsForZSet().remove(DEADLINES, String.valueOf(id));
    }

    public boolean exists(long id) {
        return Boolean.TRUE.equals(redis.hasKey("game:" + id));
    }

    public List<Long> due(long now, int limit) {
        Set<String> ids = redis.opsForZSet().rangeByScore(DEADLINES, 0, now, 0, limit);
        return ids == null ? List.of() : ids.stream().map(Long::valueOf).toList();
    }

    /** একাধিক সার্ভারে যে সফলভাবে মুছতে পারে, সে-ই গেমটা প্রসেস করবে */
    public boolean claim(long id) {
        Long r = redis.opsForZSet().remove(DEADLINES, String.valueOf(id));
        return r != null && r > 0;
    }

    public void retryLater(long id, long delayMs) {
        redis.opsForZSet().add(DEADLINES, String.valueOf(id), System.currentTimeMillis() + delayMs);
    }
}
