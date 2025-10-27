package com.yourcompany.ludo.security;

import com.yourcompany.ludo.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class QueryParamJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public QueryParamJwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // ✅ সরাসরি query parameter থেকে token নেওয়া
            String token = request.getParameter("token");

            if (token != null && !token.isBlank()) {
                try {
                    String gameId = jwtUtil.getGameIdFromToken(token);
                    if (gameId != null && !gameId.isEmpty()) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(gameId);

                        if (jwtUtil.validateToken(token, gameId)) {
                            UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("❌ QueryParam JWT validation failed: " + e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}