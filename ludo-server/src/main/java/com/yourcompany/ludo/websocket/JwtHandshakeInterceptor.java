package com.yourcompany.ludo.websocket;

import com.yourcompany.ludo.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            System.out.println("❌ Not an HTTP servlet request");
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        // প্রথমে query param থেকে token নেওয়া
        String token = httpRequest.getParameter("token");

        // যদি query param এ token না থাকে, তাহলে Authorization header থেকে নেওয়া
        if (token == null || token.trim().isEmpty()) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token == null || token.trim().isEmpty()) {
            System.out.println("❌ No token provided in query parameter or Authorization header.");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        try {
            String gameId = jwtUtil.getGameIdFromToken(token);
            if (gameId == null || gameId.isEmpty()) {
                System.out.println("❌ Game ID extracted from token is null or empty");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(gameId);
            if (userDetails == null) {
                System.out.println("❌ UserDetails not found for gameId: " + gameId);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            if (jwtUtil.validateToken(token, gameId)) {
                attributes.put("user", userDetails);
                attributes.put("gameId", gameId);
                System.out.println("✅ WebSocket Auth Success for gameId: " + gameId);
                return true;
            } else {
                System.out.println("❌ JWT validation failed for gameId: " + gameId);
            }
        } catch (Exception e) {
            System.out.println("❌ Exception during WebSocket token validation: " + e.getMessage());
            e.printStackTrace();
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No action needed after handshake
    }
}
