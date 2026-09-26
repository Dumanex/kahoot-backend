package com.kahoot.kahoot_backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Server -> Client: topic prefix za broadcast (npr. /topic/game/123456/players)
        // /queue: privatne poruke jednom korisniku (klijent se pretplaćuje na /user/queue/...)
        config.enableSimpleBroker("/topic", "/queue");

        // Client -> Server: destination prefix za app endpoint-e (nrp. /app/game/123456/join)
        config.setApplicationDestinationPrefixes("/app");

        // Server -> jedan klijent: /user/queue/errors se za svaku konekciju prevodi na njenu privatnu destinaciju
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint - frontend se konektuje ovde
        // Isti origin-i kao za CORS (app.cors.allowed-origins / CORS_ALLOWED_ORIGINS)
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new))
                .withSockJS(); // Fallback za browsere bez Websocket podrske
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Svaka poruka od klijenta prolazi kroz interceptor (CONNECT frame -> JWT provera)
        registration.interceptors(webSocketAuthInterceptor);
    }
}
