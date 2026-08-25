package com.kahoot.kahoot_backend.helper;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameSessionHelper {

    private final GameSessionRepository gameSessionRepository;

    /**
     * Retrieves a GameSession by PIN code or throws ResourceNotFoundException.
     */
    public GameSession getSessionOrThrow(String pinCode) {
        return gameSessionRepository.findByPinCode(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("Game session not found with PIN: " + pinCode));
    }

    /**
     * Validates that the session is in the expected status.
     */
    public void validateStatus(GameSession session, GameSessionStatus expectedStatus, String errorMessage) {
        if (session.getStatus() != expectedStatus) {
            throw new IllegalStateException(errorMessage + ". Current status: " + session.getStatus());
        }
    }

    /**
     * Validates that the session is in one of the expected statuses.
     */
    public void validateStatusIn(GameSession session, GameSessionStatus... expectedStatuses) {
        for (GameSessionStatus status : expectedStatuses) {
            if (session.getStatus() == status) {
                return;
            }
        }
        throw new IllegalStateException("Invalid session status. Expected one of: " + java.util.Arrays.toString(expectedStatuses)
                + ". Current status: " + session.getStatus());
    }

    /**
     * Validates that the user is the host (quiz creator) of the session.
     */
    public void validateHost(GameSession session, Long userId) {
        if (!session.getQuiz().getCreator().getId().equals(userId)) {
            throw new SecurityException("You are not the creator of this quiz");
        }
    }
}