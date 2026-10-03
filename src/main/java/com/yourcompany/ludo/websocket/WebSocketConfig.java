package com.yourcompany.ludo.websocket;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * সার্ভার-সাইড হার্টবিটের জন্য আলাদা scheduler। ইচ্ছা করে @Bean করিনি,
     * নইলে আপনার @Scheduled cleanup task-ও এই pool ব্যবহার করত।
     */
    private final ThreadPoolTaskScheduler heartbeatScheduler;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.heartbeatScheduler = new ThreadPoolTaskScheduler();
        this.heartbeatScheduler.setPoolSize(2);
        this.heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        this.heartbeatScheduler.setDaemon(true);
        this.heartbeatScheduler.initialize();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ludo-ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(new JwtHandshakeHandler())
                .setAllowedOriginPatterns("*");          // SockJS ইচ্ছা করেই নেই
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler);
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdown();
    }
}
