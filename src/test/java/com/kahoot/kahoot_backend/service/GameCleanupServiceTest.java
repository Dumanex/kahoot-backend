package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GameCleanupServiceTest {
    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private GameMessageService gameMessageService;

    @InjectMocks
    private GameCleanupService gameCleanupService;

    @Test
    void completeAbandonedGames_shouldCompleteSessionAndBroadcastFinalResults() {
        GameSession abandoned = GameSession.builder()
                .id(100L)
                .pinCode("123456")
                .status(GameSessionStatus.IN_PROGRESS)
                .questionStartedAt(LocalDateTime.now().minusHours(3))
                .build();
        when(gameSessionRepository.findByStatusAndQuestionStartedAtBefore(eq(GameSessionStatus.IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of(abandoned));

        gameCleanupService.completeAbandonedGames();

        assertThat(abandoned.getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
        assertThat(abandoned.getEndedAt()).isNotNull();
        verify(gameSessionRepository).save(abandoned);
        verify(gameMessageService).broadcastFinalResults("123456", abandoned);
    }
}
