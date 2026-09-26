package com.kahoot.kahoot_backend.config;

import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null) {
                accessor.setUser(authenticate(authHeader));
            }
        }

        return message;
    }

    private Authentication authenticate(String authHeader) {
        if (!authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Invalid Authorization header");
        }

        String jwt = authHeader.substring(7);

        try {
            String username = jwtService.extractUsername(jwt);
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null && jwtService.isTokenValid(jwt, username)) {
                UserPrincipal userPrincipal = new UserPrincipal(user);

                return new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
            }
        } catch (JwtException | IllegalArgumentException e) {
            // falls through to the exception below
        }

        throw new MessagingException("Invalid or expired token");
    }
}
