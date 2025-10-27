package com.yourcompany.ludo.engine;

import com.yourcompany.ludo.controller.GameSocketController;
import com.yourcompany.ludo.dto.GameBoardState;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.TimerTask;

public class GameAutoMoveTask extends TimerTask {

    private final Long gameId;
    private final LudoGameEngine engine;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameSocketController controller;

    public GameAutoMoveTask(Long gameId, LudoGameEngine engine,
                             SimpMessagingTemplate messagingTemplate, GameSocketController controller) {
        this.gameId = gameId;
        this.engine = engine;
        this.messagingTemplate = messagingTemplate;
        this.controller = controller;
    }

    @Override
    public void run() {
        if (engine.isGameOver()) return;

        System.out.println("[AutoMove] No move in 30s, auto-moving...");

        engine.autoMove();
        controller.checkGameOverAndUpdateDB(gameId, engine);

        GameBoardState state = new GameBoardState(
                engine.getPlayerTokens(),
                engine.getCurrentPlayer(),
                engine.isGameOver()
        );

        messagingTemplate.convertAndSend("/topic/game/" + gameId, state);
    }
}
