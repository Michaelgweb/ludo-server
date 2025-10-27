package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.GameSession;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.GameResult;

public interface GameService {

    // পুরাতন সাপোর্ট
    void finishGame(GameSession session, User winner);

    // নতুন system (controller থেকে কল হবে)
    void finish(GameResult result);

    // ড্র হ্যান্ডেল করার জন্য
    void finishDraw(GameSession session);
}
