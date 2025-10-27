package com.yourcompany.ludo.util;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    @Value("${app.jwtSecret}")
    private String secret;

    private final UserRepository userRepository;

    public JwtFilter(@Lazy UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/swagger-ui", "/v3/api-docs", "/swagger-resources", "/webjars",
            "/auth",
            "/api/otp",
            "/avatars",
            "/error",
            "/actuator/health",
            "/ludo-ws"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();

        // Skip public endpoints
        if (PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.trim().startsWith("Bearer ")) {
            String jwt = authHeader.trim().substring(7);
            try {
                Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(jwt)
                        .getBody();

                String gameId = claims.getSubject();

                Optional<User> userOptional = userRepository.findByGameId(gameId);
                if (userOptional.isPresent()) {
                    User user = userOptional.get();

                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                    );

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    logger.error("Token validation failed: User not found for gameId={}", gameId);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                    return;
                }

            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                logger.warn("Token expired: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
                return;
            } catch (Exception e) {
                logger.error("Token validation failed: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        } else {
            logger.warn("No Authorization header found for protected endpoint: {}", path);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Authorization header");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
