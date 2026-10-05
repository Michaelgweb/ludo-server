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
 * startReadyGames প্রতি ১ সেকেন্ডে, cancelIdleGames প্রতি ১০ সেকেন্ডে চলে।
 * ক্লাস @Transactional নয়; প্রতিটি সেশন আলাদাভাবে প্রসেস হয়।
 * একাধিক সার্ভার চললেও নিরাপদ (Redis গেম-লক + DB স্ট্যাটাস চেক)।
 */
@Component
public class GameCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(GameCleanupTask.class);

    private static final long IDLE_NO_ROLL_MINUTES = 2;     // কেউই রোল না করলে

    private final GameSessionRepository sessions;
    private final GameFlowService flow;

    public GameCleanupTask(GameSessionRepository sessions, GameFlowService flow) {
        this.sessions = sessions;
        this.flow = flow;
    }

    // কাউন্টডাউন শেষ: MATCH_FOUND -> ONGOING (দ্রুত চলে, যাতে প্রথম রোলে Game not active না আসে)
    @Scheduled(fixedDelay = 1_000)
    public void startReadyGames() {
        process(sessions.findIdsReadyToStart(GameStatus.MATCH_FOUND, System.currentTimeMillis()),
                flow::startIfReady, "startIfReady");
    }

    // কেউই রোল করেনি (ফি কাটা হয়নি): শুধু বাতিল
    @Scheduled(fixedDelay = 10_000)
    public void cancelIdleGames() {
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
