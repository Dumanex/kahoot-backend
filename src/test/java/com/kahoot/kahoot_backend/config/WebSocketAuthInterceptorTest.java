package com.kahoot.kahoot_backend.config;

import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WebSocketAuthInterceptorTest {
    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private Message<byte[]> stompMessage(StompCommand command, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);

        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }

        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal userOf(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).getUser();
    }

    @Test
    void preSend_connectWithoutAuthorizationHeader_shouldPassAsAnonymousPlayer() {
        Message<?> result = interceptor.preSend(stompMessage(StompCommand.CONNECT, null), channel);

        assertThat(userOf(result)).isNull();
        verifyNoInteractions(jwtService, userRepository);
    }

    @Test
    void preSend_connectWithValidToken_shouldSetAuthenticatedUser() {
        User host = User.builder().id(1L).username("host").build();
        when(jwtService.extractUsername("valid-token")).thenReturn("host");
        when(userRepository.findByUsername("host")).thenReturn(Optional.of(host));
        when(jwtService.isTokenValid("valid-token", "host")).thenReturn(true);

        Message<?> result = interceptor.preSend(stompMessage(StompCommand.CONNECT, "Bearer valid-token"), channel);

        Principal user = userOf(result);
        assertThat(user).isInstanceOf(Authentication.class);
        Object principal = ((Authentication) user).getPrincipal();
        assertThat(principal).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) principal).getUser().getId()).isEqualTo(1L);
    }

    @Test
    void preSend_connectWithInvalidToken_shouldThrowMessagingException() {
        when(jwtService.extractUsername("bad-token")).thenThrow(new JwtException("bad signature"));

        assertThatThrownBy(() -> interceptor.preSend(stompMessage(StompCommand.CONNECT, "Bearer bad-token"), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void preSend_connectWithHeaderWithoutBearerPrefix_shouldThrowMessagingException() {
        assertThatThrownBy(() -> interceptor.preSend(stompMessage(StompCommand.CONNECT, "some-token"), channel))
                .isInstanceOf(MessagingException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void preSend_connectWithTokenOfUnknownUser_shouldThrowMessagingException() {
        when(jwtService.extractUsername("valid-token")).thenReturn("ghost");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(stompMessage(StompCommand.CONNECT, "Bearer valid-token"), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void preSend_nonConnectFrame_shouldIgnoreAuthorizationHeader() {
        Message<?> result = interceptor.preSend(stompMessage(StompCommand.SEND, "Bearer valid-token"), channel);

        assertThat(userOf(result)).isNull();
        verifyNoInteractions(jwtService, userRepository);
    }
}
