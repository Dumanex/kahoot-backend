package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.game.AnswerSubmitRequest;
import com.kahoot.kahoot_backend.config.PlayerPrincipal;
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

import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void startGame_notCreator_shouldNotBroadcastErrorToPlayers() {
        when(gameService.startGame(PIN, 2L)).thenThrow(new SecurityException("You are not the creator of this quiz"));

        controller.startGame(PIN, hostAuth(2L));

        verify(gameMessageService, never()).sendErrorToUser(any(), anyString());
    }

    @Test
    void startGame_illegalState_shouldSendErrorOnlyToHost() {
        Authentication host = hostAuth(1L);
        when(gameService.startGame(PIN, 1L)).thenThrow(new IllegalStateException("Game can only be started from WAITING status"));

        controller.startGame(PIN, host);

        verify(gameMessageService).sendErrorToUser(host, "Game can only be started from WAITING status");
    }

    @Test
    void nextQuestion_sessionNotFound_shouldSendErrorOnlyToHost() {
        Authentication host = hostAuth(1L);
        when(gameService.nextQuestion(PIN, 1L)).thenThrow(new ResourceNotFoundException("Game session not found with PIN: " + PIN));

        controller.nextQuestion(PIN, host);

        verify(gameMessageService).sendErrorToUser(host, "Game session not found with PIN: " + PIN);
    }

    @Test
    void submitAnswer_shouldPassPrincipalForPrivateErrors() {
        AnswerSubmitRequest request = AnswerSubmitRequest.builder().playerId(5L).rejoinToken("token-1").questionId(10L).answerId(1L).build();
        PlayerPrincipal player = new PlayerPrincipal(5L);

        controller.submitAnswer(PIN, request, player);

        verify(gameMessageService).handlePlayerAnswer(PIN, request, player);
    }
}
