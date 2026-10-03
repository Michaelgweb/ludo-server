package com.yourcompany.ludo.websocket;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.GameFlowService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket ডিসকানেক্ট হলে সাথে সাথে হারায় না। GRACE_SECONDS এর মধ্যে ফিরে এলে কিছু হয় না,
 * না ফিরলে GameFlowService.leave() কল হয় (নিয়ম অনুযায়ী বাতিল/রিফান্ড অথবা হার)।
 *
 * ইউজার শনাক্ত হয় শুধু handshake এর JWT Principal (principal.getName() = gameId) থেকে।
 * topic নাম থেকে অনুমান করা বাদ দিয়েছি: /topic/game/{sessionId} সাবস্ক্রাইবে
 * সেশন নম্বরকে gameId ধরে ভুল হতো, আর নকলও করা যেত।
 */
@Component
public class GameDisconnectListener {

    private static final Logger log = LoggerFactory.getLogger(GameDisconnectListener.class);
    private static final long GRACE_SECONDS = 60;

    private final GameSessionRepository sessions;
    private final GameFlowService flow;

    private final Map<String, String> wsToGame = new ConcurrentHashMap<>();        // wsSessionId -> gameId
    private final Map<String, Set<String>> gameToWs = new ConcurrentHashMap<>();   // gameId -> wsSessionIds
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "ws-disconnect-grace");
        t.setDaemon(true);
        return t;
    });

    public GameDisconnectListener(GameSessionRepository sessions, GameFlowService flow) {
        this.sessions = sessions;
        this.flow = flow;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user != null && user.getName() != null) {
            register(StompHeaderAccessor.wrap(event.getMessage()).getSessionId(), user.getName());
        }
    }

    /** onConnected এ user না পেলে সাবস্ক্রাইবের সময় Principal থেকে আবার চেষ্টা (ব্যাকআপ) */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        String wsId = acc.getSessionId();
        if (wsId == null || wsToGame.containsKey(wsId)) return;
        Principal user = acc.getUser();
        if (user != null && user.getName() != null) {
            register(wsId, user.getName());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String gameId = unregister(event.getSessionId());
        if (gameId == null) return;
        scheduler.schedule(() -> handleGraceExpired(gameId), GRACE_SECONDS, TimeUnit.SECONDS);
    }

    // ---------------------------------------------------------------

    private void handleGraceExpired(String gameId) {
        try {
            if (isConnected(gameId)) return;                   // রিকানেক্ট করেছে
            List<GameSession> active = sessions.findActiveSessionsByPlayerGameId(gameId);
            for (GameSession s : active) {
                try {
                    flow.leave(s.getId(), gameId);
                    log.info("Player {} left session {} after disconnect", gameId, s.getId());
                } catch (Exception e) {
                    log.error("leave failed for session {} player {}", s.getId(), gameId, e);
                }
            }
        } catch (Exception e) {
            log.error("grace handler failed for {}", gameId, e);
        }
    }

    private void register(String wsId, String gameId) {
        if (wsId == null || gameId == null || gameId.isBlank()) return;
        wsToGame.put(wsId, gameId);
        gameToWs.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(wsId);
    }

    private String unregister(String wsId) {
        String gameId = wsToGame.remove(wsId);
        if (gameId != null) {
            gameToWs.computeIfPresent(gameId, (k, set) -> {
                set.remove(wsId);
                return set.isEmpty() ? null : set;
            });
        }
        return gameId;
    }

    private boolean isConnected(String gameId) {
        Set<String> set = gameToWs.get(gameId);
        return set != null && !set.isEmpty();
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
