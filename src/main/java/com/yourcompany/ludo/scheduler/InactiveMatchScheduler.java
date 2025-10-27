package com.yourcompany.ludo.scheduler;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.repository.GameSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class InactiveMatchScheduler {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * প্রতি ৬০ সেকেন্ডে রান হবে
     * ৬০ সেকেন্ড ধরে কেউ ডাইস না ঘোরালে ম্যাচ ক্যানসেল করবে এবং নোটিফাই করবে
     */
    @Transactional
    @Scheduled(fixedRate = 60000) // 60,000 মিলিসেকেন্ড = 60 সেকেন্ড
    public void cancelInactiveMatchesTask() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(60);

        // ইনঅ্যাকটিভ ম্যাচগুলো খুঁজে আনা
        List<GameSession> inactiveMatches = gameSessionRepository.findAll().stream()
                .filter(g -> "ONGOING".equals(g.getStatus())
                        && g.getStartTime() != null
                        && g.getStartTime().isBefore(cutoffTime)
                        && g.getPlayer1DiceCount() == 0
                        && g.getPlayer2DiceCount() == 0)
                .toList();

        for (GameSession match : inactiveMatches) {
            match.setStatus("CANCELLED");
            gameSessionRepository.save(match);

            var payload = new java.util.HashMap<String, Object>();
            payload.put("gameId", match.getId());
            payload.put("status", "CANCELLED");
            payload.put("message", "Match auto-cancelled due to inactivity.");

            // রিয়েল-টাইম নোটিফিকেশন
            messagingTemplate.convertAndSend("/topic/match/" + match.getPlayer1().getGameId(), payload);
            messagingTemplate.convertAndSend("/topic/match/" + match.getPlayer2().getGameId(), payload);
        }

        if (!inactiveMatches.isEmpty()) {
            System.out.println("⏳ Auto-cancelled inactive matches: " + inactiveMatches.size());
        }
    }
}
