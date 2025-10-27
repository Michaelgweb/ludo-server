package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;

public interface GameService {
    void finishGame(GameSession session, User winner);
}

