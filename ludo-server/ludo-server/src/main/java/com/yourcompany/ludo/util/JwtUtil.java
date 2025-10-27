package com.yourcompany.ludo.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    // secret key কে application.properties থেকে inject করা হচ্ছে
    @Value("${jwt.secret}")
    private String secret;

    // token validity 1 দিন (মিলিসেকেন্ডে)
    private final long jwtExpirationMs = 24 * 60 * 60 * 1000;

    // Token generate করার মেথড
    public String generateToken(String email) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Token থেকে email (subject) extract করার মেথড
    public String getEmailFromToken(String token) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // Token expiry চেক করার মেথড
    public boolean isTokenExpired(String token) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();

        return expiration.before(new Date());
    }

    // Token validation
    public boolean validateToken(String token, String email) {
        String tokenEmail = getEmailFromToken(token);
        return (tokenEmail.equals(email) && !isTokenExpired(token));
    }
}
