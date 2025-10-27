package com.yourcompany.ludo.websocket;

import com.yourcompany.ludo.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

/**
 * WebSocket Handshake Interceptor that authenticates users using JWT tokens.
 * Supports token via query parameter (?token=) or Authorization header.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    @Lazy
    private UserDetailsService userDetailsService; // ✅ Lazy inject to break circular dependency

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            logError("Not an HTTP servlet request", HttpStatus.FORBIDDEN, response);
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        // Step 1: Get token from query parameter (?token=...)
        String token = httpRequest.getParameter("token");

        // Step 2: If not in query, try Authorization header
        if (token == null || token.isBlank()) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        // Step 3: Reject if no token found
        if (token == null || token.isBlank()) {
            logError("No token provided in query parameter or Authorization header", HttpStatus.FORBIDDEN, response);
            return false;
        }

        try {
            // Step 4: Extract gameId from token
            String gameId = jwtUtil.getGameIdFromToken(token);
            if (gameId == null || gameId.isBlank()) {
                logError("Game ID extracted from token is null or empty", HttpStatus.UNAUTHORIZED, response);
                return false;
            }

            // Step 5: Load user details from DB
            UserDetails userDetails = userDetailsService.loadUserByUsername(gameId);
            if (userDetails == null) {
                logError("UserDetails not found for gameId: " + gameId, HttpStatus.UNAUTHORIZED, response);
                return false;
            }

            // Step 6: Validate token
            if (jwtUtil.validateToken(token, gameId)) {
                // ✅ Set attributes and Principal for private messaging
                WebSocketPrincipal principal = new WebSocketPrincipal(gameId);
                attributes.put("principal", principal); // Spring will use this as Principal
                attributes.put("user", userDetails);
                attributes.put("gameId", gameId);

                System.out.println("✅ WebSocket Auth Success for gameId: " + gameId);
                return true;
            } else {
                logError("JWT validation failed for gameId: " + gameId, HttpStatus.UNAUTHORIZED, response);
            }

        } catch (Exception e) {
            logError("Exception during WebSocket token validation: " + e.getMessage(),
                     HttpStatus.UNAUTHORIZED, response);
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No post-handshake actions needed
    }

    private void logError(String message, HttpStatus status, ServerHttpResponse response) {
        System.out.println("❌ " + message);
        response.setStatusCode(status);
    }
}
