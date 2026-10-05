package com.yourcompany.ludo.tasks;

import com.yourcompany.ludo.service.GameFlowService;
import com.yourcompany.ludo.service.GameMoveService;
import com.yourcompany.ludo.service.GamePayloads;
import com.yourcompany.ludo.service.GameStateStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** প্রতি ১ সেকেন্ডে Redis থেকে মেয়াদ-শেষ গেম নিয়ে সমান্তরালে প্রসেস করে। DB কোয়েরি নেই */
@Component
public class TurnTicker {

    private static final Logger log = LoggerFactory.getLogger(TurnTicker.class);

    private final GameStateStore store;
    private final GameFlowService flow;
    private final GameMoveService moves;
    private final SimpMessagingTemplate ws;
    private final ExecutorService pool = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "turn-ticker");
        t.setDaemon(true);
        return t;
    });

    public TurnTicker(GameStateStore store, GameFlowService flow,
                      GameMoveService moves, SimpMessagingTemplate ws) {
        this.store = store;
        this.flow = flow;
        this.moves = moves;
        this.ws = ws;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        try {
            for (long id : store.due(System.currentTimeMillis(), 500)) {
                if (!store.claim(id)) continue;              // অন্য সার্ভার ধরে ফেলেছে
                pool.submit(() -> handle(id));
            }
        } catch (Exception e) {
            log.error("tick failed", e);
        }
    }

    private void handle(long id) {
        try {
            var e = flow.expireTurn(id);
            switch (e.kind()) {
                case ROLLED -> ws.convertAndSend("/topic/game/" + id, GamePayloads.roll(id, e.roll()));
                case NEED_MOVE -> {
                    var r = moves.move(id, e.gameId(), e.token(), false);   // অটো: মিস রিসেট নয়
                    ws.convertAndSend("/topic/game/" + id, GamePayloads.move(id, r));
                }
                default -> { }   // NONE বা FORFEITED (close() নিজেই নোটিফাই করে)
            }
        } catch (Exception ex) {
            log.error("turn expire failed for {}", id, ex);
            store.retryLater(id, 5000);                      // টাইমার হারিয়ে না যায়
        }
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
