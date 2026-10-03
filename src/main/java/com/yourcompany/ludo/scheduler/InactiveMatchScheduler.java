package com.yourcompany.ludo.scheduler;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.GameFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ৬০ সেকেন্ড ধরে কেউ ডাইস না ঘোরালে ম্যাচ বাতিল করে ও নোটিফাই করে।
 *
 * বাতিল করার কাজ GameFlowService.cancelIdle এ হয়, কারণ সেখানে সেশন রো লক হয়
 * এবং ফি কাটা হয়ে গেলে (কেউ রোল করলে) সেটা বাতিল করে না। সরাসরি স্ট্যাটাস
 * বদলালে রোলের সাথে রেস কন্ডিশনে টাকা কাটা অবস্থায় ম্যাচ বাতিল হয়ে যেতে পারত।
 * এই ক্লাস @Transactional নয়, প্রতিটি সেশন আলাদা ট্রানজেকশনে প্রসেস হয়।
 */
@Component
public class InactiveMatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(InactiveMatchScheduler.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameFlowService flow;
    private final SimpMessagingTemplate messagingTemplate;

    public InactiveMatchScheduler(GameSessionRepository gameSessionRepository,
                                  GameFlowService flow,
                                  SimpMessagingTemplate messagingTemplate) {
        this.gameSessionRepository = gameSessionRepository;
        this.flow = flow;
        this.messagingTemplate = messagingTemplate;
    }

    /** প্রতি ৬০ সেকেন্ডে রান হবে */
    @Scheduled(fixedRate = 60_000)
    public void cancelInactiveMatchesTask() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(60);

        // ONGOING, ফি কাটা হয়নি (মানে কেউ রোল করেনি), ৬০ সেকেন্ডের বেশি পুরনো
        List<Long> ids = gameSessionRepository.findIdsIdleWithoutFee(
                List.of(GameStatus.ONGOING), cutoffTime);

        int cancelled = 0;
        for (Long id : ids) {
            try {
                flow.cancelIdle(id);

                GameSession match = gameSessionRepository.findById(id).orElse(null);
                if (match != null && match.getStatus() == GameStatus.CANCELLED) {
                    cancelled++;

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("gameId", match.getId());
                    payload.put("status", "CANCELLED");
                    payload.put("message", "Match auto-cancelled due to inactivity.");

                    messagingTemplate.convertAndSend(
                            "/topic/match/" + match.getPlayer1().getGameId(), payload);
                    messagingTemplate.convertAndSend(
                            "/topic/match/" + match.getPlayer2().getGameId(), payload);
                }
            } catch (Exception e) {
                log.error("cancelIdle failed for session {}", id, e);
            }
        }

        if (cancelled > 0) {
            log.info("Auto-cancelled inactive matches: {}", cancelled);
        }
    }
}
