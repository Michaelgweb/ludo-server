package com.yourcompany.ludo.config;

import com.yourcompany.ludo.websocket.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ludo-ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*") // ✅ Production-এ নির্দিষ্ট ডোমেইন দেবে
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // ✅ User-specific messaging
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user"); // ✅ Private messages support
    }
}
