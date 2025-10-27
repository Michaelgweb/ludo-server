package com.yourcompany.ludo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String secret;

    private final UserDetailsService userDetailsService;

    public JwtFilter(@Lazy UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        System.out.println("[JwtFilter] Incoming request to path: " + path);

        final String authHeader = request.getHeader("Authorization");
        System.out.println("[JwtFilter] Authorization header: " + authHeader);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(jwt)
                        .getBody();

                String email = claims.getSubject();
                System.out.println("[JwtFilter] JWT valid for email: " + email);

                // Load user details
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                System.out.println("[JwtFilter] Loaded UserDetails for: " + userDetails.getUsername());

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
                System.out.println("[JwtFilter] Authentication set in SecurityContext");
            } catch (Exception e) {
                System.out.println("[JwtFilter] JWT validation failed: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return; // stop filter chain, unauthorized response
            }
        } else {
            System.out.println("[JwtFilter] No Bearer token found in Authorization header");
        }

        filterChain.doFilter(request, response);
    }
}
