package com.yourcompany.ludo.tasks;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.GameStatus;
import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.GameFlowService;
import com.yourcompany.ludo.service.GameMoneyService;
import com.yourcompany.ludo.service.GameState;
import com.yourcompany.ludo.service.GameStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * চালুর সময় একবার: DB তে ONGOING কিন্তু Redis এ স্টেট নেই এমন গেম ঠিক করে।
 *  - ফি কাটা হয়নি: বোর্ড অক্ষত (সবাই ঘরে), নতুন স্টেট বানাও
 *  - ফি কাটা হয়েছে: বোর্ড হারিয়েছে, দুজনকে ফেরত দিয়ে বাতিল
 */
@Component
public class GameRecovery {

    private static final Logger log = LoggerFactory.getLogger(GameRecovery.class);

    private final GameSessionRepository sessions;
    private final GameStateStore store;
    private final GameMoneyService money;

    public GameRecovery(GameSessionRepository sessions, GameStateStore store, GameMoneyService money) {
        this.sessions = sessions;
        this.store = store;
        this.money = money;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (GameSession g : sessions.findByStatus(GameStatus.ONGOING)) {
            try {
                if (store.exists(g.getId())) continue;

                if (!g.isFeeDeducted()) {
                    GameState s = new GameState();
                    s.id = g.getId();
                    s.g1 = g.getPlayer1().getGameId();
                    s.g2 = g.getPlayer2().getGameId();
                    s.deadline = System.currentTimeMillis() + GameFlowService.TURN_MS;
                    store.save(s);
                    log.info("Recovered fresh game {}", g.getId());
                } else {
                    money.refundAndCancel(g.getId(), "সার্ভার সমস্যায় ম্যাচ বাতিল, ফি ফেরত");
                    log.warn("Cancelled + refunded game {} (Redis state lost)", g.getId());
                }
            } catch (Exception e) {
                log.error("recover failed for {}", g.getId(), e);
            }
        }
    }
}
