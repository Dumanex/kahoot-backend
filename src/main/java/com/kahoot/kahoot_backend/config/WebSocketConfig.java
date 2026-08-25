package com.kahoot.kahoot_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(WebSocketHandshakeInterceptor handshakeInterceptor) {
        this.handshakeInterceptor = handshakeInterceptor;
    }

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
                .setAllowedOrigins("http://localhost:63342", "http://localhost:8080")
                .addInterceptors(handshakeInterceptor)
                .withSockJS(); // Fallback za browsere bez Websocket podrske
    }

    @Bean
    public CorsFilter corsFilter() {
       CorsConfiguration config = new CorsConfiguration();

       config.setAllowedOrigins(Arrays.asList("http://localhost:63342", "http://localhost:8080"));
       config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
       config.setAllowedHeaders(Arrays.asList("*"));
       config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
