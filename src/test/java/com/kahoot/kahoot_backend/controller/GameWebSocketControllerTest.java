package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.config.UserPrincipal;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.service.GameMessageService;
import com.kahoot.kahoot_backend.service.GameService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameWebSocketControllerTest {
    @Mock
    private GameService gameService;

    @Mock
    private GameMessageService gameMessageService;

    @InjectMocks
    private GameWebSocketController controller;

    private static final String PIN = "123456";

    private Authentication hostAuth(Long userId) {
        User user = User.builder().id(userId).username("host").build();
        UserPrincipal principal = new UserPrincipal(user);

        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // Authenticated host
    @Test
    void startGame_authenticatedHost_shouldCallGameServiceWithUserId() {
        controller.startGame(PIN, hostAuth(1L));

        verify(gameService).startGame(PIN, 1L);
    }

    @Test
    void nextQuestion_authenticatedHost_shouldCallGameServiceWithUserId() {
        controller.nextQuestion(PIN, hostAuth(1L));

        verify(gameService).nextQuestion(PIN, 1L);
    }

    @Test
    void endGame_authenticatedHost_shouldCallGameServiceWithUserId() {
        controller.endGame(PIN, hostAuth(1L));

        verify(gameService).endGame(PIN, 1L);
    }

    @Test
    void finalizeUnansweredPlayers_authenticatedHost_shouldCallGameServiceWithUserId() {
        controller.finalizeUnansweredPlayers(PIN, hostAuth(1L));

        verify(gameService).finalizeUnanswered(PIN, 1L);
    }

    // Unauthenticated caller
    @Test
    void startGame_noPrincipal_shouldRejectWithoutCallingServices() {
        controller.startGame(PIN, null);

        verifyNoInteractions(gameService);
        verifyNoInteractions(gameMessageService);
    }

    @Test
    void endGame_principalIsNotAuthentication_shouldRejectWithoutCallingServices() {
        Principal anonymous = () -> "anonymous";

        controller.endGame(PIN, anonymous);

        verifyNoInteractions(gameService);
        verifyNoInteractions(gameMessageService);
    }

    // Authenticated but not the creator of the quiz
    @Test
    void startGame_notCreator_shouldNotBroadcastErrorToPlayers() {
        when(gameService.startGame(PIN, 2L)).thenThrow(new SecurityException("You are not the creator of this quiz"));

        controller.startGame(PIN, hostAuth(2L));

        verify(gameMessageService, never()).sendError(anyString(), anyString());
    }

    // Errors visible to the host
    @Test
    void startGame_illegalState_shouldSendErrorToGameTopic() {
        when(gameService.startGame(PIN, 1L)).thenThrow(new IllegalStateException("Game can only be started from WAITING status"));

        controller.startGame(PIN, hostAuth(1L));

        verify(gameMessageService).sendError(PIN, "Game can only be started from WAITING status");
    }

    @Test
    void nextQuestion_sessionNotFound_shouldSendErrorToGameTopic() {
        when(gameService.nextQuestion(PIN, 1L)).thenThrow(new ResourceNotFoundException("Game session not found with PIN: " + PIN));

        controller.nextQuestion(PIN, hostAuth(1L));

        verify(gameMessageService).sendError(PIN, "Game session not found with PIN: " + PIN);
    }
}
