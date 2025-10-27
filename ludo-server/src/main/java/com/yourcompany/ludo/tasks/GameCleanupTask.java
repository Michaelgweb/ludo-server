package com.yourcompany.ludo.tasks;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.repository.GameSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GameCleanupTask {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // প্রতি ১ মিনিটে চেক করো
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupInactiveGames() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);
        List<GameSession> sessions = gameSessionRepository.findAll();

        for (GameSession session : sessions) {
            if (session.getStatus().equals("ONGOING")
                    && !session.isFeeDeducted()
                    && session.getStartTime().isBefore(cutoff)) {

                // ম্যাচ ক্যানসেল
                session.setStatus("CANCELLED");
                gameSessionRepository.save(session);

                // WebSocket payload তৈরি
                Map<String, Object> payload = new HashMap<>();
                payload.put("gameId", session.getId());
                payload.put("status", "CANCELLED");
                payload.put("message", "ম্যাচ ২ মিনিটে শুরু না হওয়ায় ক্যানসেল হয়েছে");

                // দুই প্লেয়ারকে নোটিফাই
                messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer1().getGameId(), payload);
                messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer2().getGameId(), payload);
            }
        }
    }
}
