package com.yourcompany.ludo.tasks;

import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.GameFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * প্রতি ১০ সেকেন্ডে চলে। এই ক্লাস @Transactional নয়, প্রতিটি সেশন GameFlowService এর
 * নিজস্ব ট্রানজেকশনে আলাদাভাবে প্রসেস হয়, তাই একটা ফেল করলে বাকিগুলো আটকায় না।
 * একাধিক সার্ভার চললেও নিরাপদ (সেশন লক + স্ট্যাটাস চেক)।
 */
@Component
public class GameCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(GameCleanupTask.class);

    private static final long FIRST_ROLL_TIMEOUT_SECONDS = 60; // ফি কাটার পর অপর জনের রোলের সময়
    private static final long IDLE_NO_ROLL_MINUTES = 2;         // কেউই রোল না করলে

    private final GameSessionRepository sessions;
    private final GameFlowService flow;

    public GameCleanupTask(GameSessionRepository sessions, GameFlowService flow) {
        this.sessions = sessions;
        this.flow = flow;
    }

    @Scheduled(fixedDelay = 10_000)
    public void run() {
        // ১) কাউন্টডাউন শেষ: MATCH_FOUND -> ONGOING
        process(sessions.findIdsReadyToStart(GameStatus.MATCH_FOUND, System.currentTimeMillis()),
                flow::startIfReady, "startIfReady");

        // ২) ফি কাটা হয়েছে কিন্তু অপর জন প্রথম রোল করেনি: বাতিল + দুজনকে রিফান্ড
        LocalDateTime rollCutoff = LocalDateTime.now().minusSeconds(FIRST_ROLL_TIMEOUT_SECONDS);
        process(sessions.findIdsFirstRollTimedOut(GameStatus.ONGOING, rollCutoff),
                flow::timeoutFirstRoll, "timeoutFirstRoll");

        // ৩) কেউই রোল করেনি (ফি কাটা হয়নি): শুধু বাতিল
        LocalDateTime idleCutoff = LocalDateTime.now().minusMinutes(IDLE_NO_ROLL_MINUTES);
        process(sessions.findIdsIdleWithoutFee(List.of(GameStatus.MATCH_FOUND, GameStatus.ONGOING), idleCutoff),
                flow::cancelIdle, "cancelIdle");
    }

    private void process(List<Long> ids, Consumer<Long> action, String name) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                log.error("{} failed for session {}", name, id, e);
            }
        }
    }
}
