package com.yourcompany.ludo.security;

import com.yourcompany.ludo.repository.UserRepository;
import com.yourcompany.ludo.service.impl.UserDetailsServiceImpl;
import com.yourcompany.ludo.util.JwtFilter;
import com.yourcompany.ludo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserRepository userRepository;

    public SecurityConfig(JwtFilter jwtFilter,
                          JwtUtil jwtUtil,
                          @Lazy UserDetailsServiceImpl userDetailsService,
                          UserRepository userRepository) {
        this.jwtFilter = jwtFilter;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Value("#{'${app.cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public QueryParamJwtAuthenticationFilter queryParamJwtAuthenticationFilter() {
        return new QueryParamJwtAuthenticationFilter(jwtUtil, userRepository);
    }

    // Spring Boot যেন ফিল্টার দুটো আলাদাভাবে সার্ভলেট চেইনে না বসায়
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<QueryParamJwtAuthenticationFilter> queryJwtFilterRegistration() {
        FilterRegistrationBean<QueryParamJwtAuthenticationFilter> reg =
                new FilterRegistrationBean<>(queryParamJwtAuthenticationFilter());
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // --- CORS Config ---
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowCredentials(true);
                if (allowedOrigins.contains("*")) {
                    config.addAllowedOriginPattern("*");
                } else {
                    config.setAllowedOrigins(allowedOrigins);
                }
                config.addAllowedHeader("*");
                config.addAllowedMethod("*");
                return config;
            }))
            // --- Disable CSRF ---
            .csrf(csrf -> csrf.disable())
            // --- Authorization Rules ---
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ===== Admin only (public তালিকার আগে থাকতে হবে) =====
                .requestMatchers(
                    "/api/payment-config/admin/**",
                    "/api/admin/payment-accounts/**",
                    "/api/payment-sms/**",
                    "/api/withdraw/approve/**",
                    "/api/withdraw/reject/**",
                    "/api/withdraw/pending/**",
                    "/api/withdraw/complete/**"
                ).hasRole("ADMIN")

                // ===== Admin + Support (STAFF) : ডিপোজিট ম্যানেজমেন্ট =====
                .requestMatchers("/api/deposit/admin/**").hasAnyRole("ADMIN", "STAFF")

                // ===== Public Endpoints =====
                .requestMatchers(
                    "/",
                    "/error",
                    "/auth/**",
                    "/api/otp/**",
                    "/avatars/**",
                    "/api/payment-config/**",
                    "/api/payment/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/swagger-ui.html",
                    "/actuator/health",
                    "/ludo-ws/**"
                ).permitAll()

                // ===== Authenticated =====
                .requestMatchers("/api/user/profile/**").authenticated()
                .requestMatchers("/api/deposit/**").authenticated()
                // গেম: রোল, চাল, স্টেট, লিভ ও চ্যাট (/api/game/chat/**) সবই লগইন লাগবে
                .requestMatchers("/api/game/**").authenticated()

                // বাকি সব লগইন লাগবে
                .anyRequest().authenticated()
            )
            // --- Stateless Session ---
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // --- Filters ---
        http.addFilterBefore(queryParamJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
