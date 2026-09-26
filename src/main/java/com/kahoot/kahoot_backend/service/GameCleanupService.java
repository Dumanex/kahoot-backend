package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameCleanupService {
    private final GameSessionRepository gameSessionRepository;
    private final GameMessageService gameMessageService;

    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;
    private static final int WAITING_EXPIRY_MINUTES = 30;
    private static final int IN_PROGRESS_EXPIRY_HOURS = 2;

    // Never-started games are deleted; their players are removed by cascade
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    @Transactional
    public void deleteExpiredWaitingGames() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(WAITING_EXPIRY_MINUTES);
        int deleted = gameSessionRepository.deleteByStatusAndCreatedAtBefore(GameSessionStatus.WAITING, cutoff);

        if (deleted > 0) {
            log.info("Deleted {} WAITING game(s) older than {} minutes", deleted, WAITING_EXPIRY_MINUTES);
        }
    }

    // Games where the current question hasn't changed for too long are ended, so they move to history
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    @Transactional
    public void completeAbandonedGames() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(IN_PROGRESS_EXPIRY_HOURS);
        List<GameSession> abandoned = gameSessionRepository.findByStatusAndQuestionStartedAtBefore(GameSessionStatus.IN_PROGRESS, cutoff);

        for (GameSession session : abandoned) {
            session.setStatus(GameSessionStatus.COMPLETED);
            session.setEndedAt(LocalDateTime.now());
            gameSessionRepository.save(session);

            gameMessageService.broadcastFinalResults(session.getPinCode(), session);
            log.info("Auto-completed abandoned game {}", session.getPinCode());
        }
    }
}
