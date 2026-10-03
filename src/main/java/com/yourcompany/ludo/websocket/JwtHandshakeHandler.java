package com.yourcompany.ludo.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/** JwtHandshakeInterceptor যে principal সেট করে সেটাকেই WebSocket সেশনের Principal বানায় */
public class JwtHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object p = attributes.get("principal");
        return p instanceof Principal pr ? pr : null;
    }
}
