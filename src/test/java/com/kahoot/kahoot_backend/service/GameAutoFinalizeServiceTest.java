package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameAutoFinalizeServiceTest {
    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private GameService gameService;

    @InjectMocks
    private GameAutoFinalizeService gameAutoFinalizeService;

    @Test
    void finalizeExpiredQuestions_shouldCheckEveryOpenQuestion() {
        GameSession first = GameSession.builder().pinCode("111111").build();
        GameSession second = GameSession.builder().pinCode("222222").build();
        when(gameSessionRepository.findByStatusAndQuestionFinalizedFalse(GameSessionStatus.IN_PROGRESS)).thenReturn(List.of(first, second));

        gameAutoFinalizeService.finalizeExpiredQuestions();

        verify(gameService).autoFinalizeIfExpired("111111");
        verify(gameService).autoFinalizeIfExpired("222222");
    }

    @Test
    void finalizeExpiredQuestions_oneGameFails_shouldStillCheckTheOthers() {
        GameSession first = GameSession.builder().pinCode("111111").build();
        GameSession second = GameSession.builder().pinCode("222222").build();
        when(gameSessionRepository.findByStatusAndQuestionFinalizedFalse(GameSessionStatus.IN_PROGRESS)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("boom")).when(gameService).autoFinalizeIfExpired("111111");

        gameAutoFinalizeService.finalizeExpiredQuestions();

        verify(gameService).autoFinalizeIfExpired("222222");
    }
}
