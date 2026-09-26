package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

// Finalizes a question when its time is up, so players get their results even if the host's tab
// is closed or asleep. The host's own finalize (e.g. when everyone answered) still works earlier.
@Slf4j
@Service
@RequiredArgsConstructor
public class GameAutoFinalizeService {
    private final GameSessionRepository gameSessionRepository;
    private final GameService gameService;

    private static final long CHECK_INTERVAL_MS = 1000;

    @Scheduled(fixedDelay = CHECK_INTERVAL_MS)
    public void finalizeExpiredQuestions() {
        List<GameSession> openQuestions = gameSessionRepository.findByStatusAndQuestionFinalizedFalse(GameSessionStatus.IN_PROGRESS);

        for (GameSession session : openQuestions) {
            // Each game in its own transaction, so one failing game doesn't stop the others
            try {
                gameService.autoFinalizeIfExpired(session.getPinCode());
            } catch (RuntimeException e) {
                log.warn("Auto-finalize failed for game {}: {}", session.getPinCode(), e.getMessage());
            }
        }
    }
}
