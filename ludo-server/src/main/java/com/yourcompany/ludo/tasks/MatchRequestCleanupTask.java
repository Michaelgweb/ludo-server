package com.yourcompany.ludo.tasks;

import com.yourcompany.ludo.repository.MatchRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MatchRequestCleanupTask {

    @Autowired
    private MatchRequestRepository repository;

    @Scheduled(fixedRate = 300000) // প্রতি ৫ মিনিটে একবার রান করবে
    public void cleanupOldRequests() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        repository.deleteOldUnmatchedRequests(cutoff);
    }
}
