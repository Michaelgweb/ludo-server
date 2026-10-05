package com.yourcompany.ludo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Redis লক: একই গেমে একসাথে দুটো কাজ চলবে না। এটা reentrant নয়, ভেতরে ভেতরে আবার with() ডাকবেন না */
@Component
public class GameLock {

    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public GameLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public <T> T with(long id, Supplier<T> fn) {
        String key = "lock:game:" + id;
        String token = UUID.randomUUID().toString();
        long giveUp = System.currentTimeMillis() + 3000;

        while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(10)))) {
            if (System.currentTimeMillis() > giveUp) {
                throw new IllegalStateException("Game busy, try again");
            }
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted");
            }
        }
        try {
            return fn.get();
        } finally {
            redis.execute(UNLOCK, List.of(key), token);   // শুধু নিজের লক ছাড়ে
        }
    }
}
