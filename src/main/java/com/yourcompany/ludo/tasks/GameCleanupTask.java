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

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupInactiveGames() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);
        List<GameSession> sessions = gameSessionRepository.findAll();

        for (GameSession session : sessions) {
            if (session.getStatus().equals("ONGOING")
                    && !session.isFeeDeducted()
                    && session.getStartTime() != null
                    && session.getStartTime().isBefore(cutoff)
                    && (session.getPlayer1DiceCount() < 5 || session.getPlayer2DiceCount() < 5)) {

                session.setStatus("CANCELLED");
                gameSessionRepository.save(session);

                Map<String, Object> payload = new HashMap<>();
                payload.put("gameId", session.getId());
                payload.put("status", "CANCELLED");
                payload.put("message", "২ মিনিটে ডাইস না চালানোয় ম্যাচ বাতিল");

                messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer1().getGameId(), payload);
                messagingTemplate.convertAndSend("/topic/match/" + session.getPlayer2().getGameId(), payload);
            }
        }
    }
}