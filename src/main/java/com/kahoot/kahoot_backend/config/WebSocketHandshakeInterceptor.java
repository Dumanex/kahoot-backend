package com.kahoot.kahoot_backend.config;

import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Extract token from query parameter: ws://host/ws?token=xxx
        String query = request.getURI().getQuery();
        String token = extractTokenFromQuery(query);

        if (token == null) {
            // Allow anonymous connections for player join (pinCode based)
            // But mark as unauthenticated
            attributes.put("authenticated", false);
            return true;
        }

        try {
            String username = jwtService.extractUsername(token);
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null && jwtService.isTokenValid(token, username)) {
                UserPrincipal principal = new UserPrincipal(user);
                attributes.put("userPrincipal", principal);
                attributes.put("authenticated", true);
                return true;
            }
        } catch (Exception e) {
            // Invalid token - allow but mark as unauthenticated
        }

        attributes.put("authenticated", false);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }

    private String extractTokenFromQuery(String query) {
        if (query == null) return null;
        // Parse query string for token parameter
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}