package com.yourcompany.ludo.service;

import com.yourcompany.ludo.repository.GameSessionRepository;
import com.yourcompany.ludo.service.GameFlowService.Expired;
import com.yourcompany.ludo.service.GameMoveService.MoveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** প্রতি সেকেন্ডে চেক: ১৫ সেকেন্ড পার হলে অটো রোল / অটো চাল, ৩ বার মিস করলে হার */
@Service
public class TurnTimerService {

    private static final Logger log = LoggerFactory.getLogger(TurnTimerService.class);

    private final GameSessionRepository sessions;
    private final GameFlowService flow;
    private final GameMoveService moves;
    private final SimpMessagingTemplate ws;

    public TurnTimerService(GameSessionRepository sessions, GameFlowService flow,
                            GameMoveService moves, SimpMessagingTemplate ws) {
        this.sessions = sessions;
        this.flow = flow;
        this.moves = moves;
        this.ws = ws;
    }

    @Scheduled(fixedDelay = 1000)
    public void sweep() {
        for (Long sid : sessions.findExpiredTurnIds(System.currentTimeMillis())) {
            try {
                handle(sid);
            } catch (Exception e) {
                log.warn("turn timer failed for session {}: {}", sid, e.toString());
            }
        }
    }

    private void handle(Long sid) {
        Expired x = flow.expireTurn(sid);                       // লক + ডেডলাইন যাচাই + মিস গোনা
        String topic = "/topic/game/" + sid;

        switch (x.kind()) {
            case ROLLED -> {
                if (!x.roll().cancelled()) {
                    ws.convertAndSend(topic, GamePayloads.roll(sid, x.roll()));
                }
            }
            case NEED_MOVE -> {
                MoveResult r = moves.move(sid, x.gameId(), x.token());
                ws.convertAndSend(topic, GamePayloads.move(sid, r));
                flow.markTurnStart(sid, null);                  // অটো, তাই মিস রিসেট হয় না
            }
            default -> { /* NONE / FORFEITED: GameFlowService নিজেই নোটিফাই করেছে */ }
        }
    }
}
