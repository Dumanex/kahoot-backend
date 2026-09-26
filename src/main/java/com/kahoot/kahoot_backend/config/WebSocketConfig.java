package com.kahoot.kahoot_backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Server -> Client: topic prefix za broadcast (npr. /topic/game/123456/players)
        config.enableSimpleBroker("/topic");

        // Client -> Server: destination prefix za app endpoint-e (nrp. /app/game/123456/join)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint - frontend se konektuje ovde
        // allowedOrigins("*") za development (frontend na drugom portu)
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:63342", "http://localhost:8080", "http://localhost:5173")
                .withSockJS(); // Fallback za browsere bez Websocket podrske
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Svaka poruka od klijenta prolazi kroz interceptor (CONNECT frame -> JWT provera)
        registration.interceptors(webSocketAuthInterceptor);
    }
}
