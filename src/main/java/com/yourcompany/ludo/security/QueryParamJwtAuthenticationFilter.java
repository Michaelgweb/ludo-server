package com.yourcompany.ludo.security;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class QueryParamJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public QueryParamJwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = request.getParameter("token");

            if (token != null && !token.isBlank()) {
                try {
                    String gameId = jwtUtil.getGameIdFromToken(token);

                    if (gameId != null && !gameId.isEmpty()) {
                        Optional<User> userOptional = userRepository.findByGameId(gameId);

                        if (userOptional.isPresent() && jwtUtil.validateToken(token, gameId)) {
                            User user = userOptional.get();

                            List<SimpleGrantedAuthority> authorities = List.of(
                                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                            );

                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(user, null, authorities);
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
