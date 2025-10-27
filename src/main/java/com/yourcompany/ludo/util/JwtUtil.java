package com.yourcompany.ludo.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${app.jwtSecret}")
    private String secret;

    @Value("${app.jwtExpirationMs:86400000}") // default 24 hours
    private long jwtExpirationMs;

    private Key signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String getSecret() {
        return secret;
    }

    /**
     * Generate JWT token using gameId as subject and role as claim
     */
    public String generateToken(String gameId, String role) {
        return Jwts.builder()
                .setSubject(gameId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract gameId (subject) from token
     */
    public String getGameIdFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (ExpiredJwtException e) {
            logger.warn("Token expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            logger.error("Invalid token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * For backward compatibility
     */
    public String extractUsername(String token) {
        return getGameIdFromToken(token);
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * Validate token with gameId
     */
    public boolean validateToken(String token, String gameId) {
        String tokenGameId = getGameIdFromToken(token);
        return (tokenGameId != null && tokenGameId.equals(gameId) && !isTokenExpired(token));
    }
}
