package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.*;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.*;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameMessageService {
    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final QuestionRepository questionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlayerService playerService;

    private static final String TOPIC_PREFIX = "/topic/game/";

    // ================== PLAYER ANSWER ==================
    public void handlePlayerAnswer(String pinCode, AnswerSubmitRequest request) {
        try {
            AnswerResultDTO result = playerService.submitAnswer(pinCode, request.getPlayerId(), request);

            // Broadcast answer result to player
            send(TOPIC_PREFIX + pinCode + "/answer-result", result);

            // Broadcast leaderboard
            broadcastLeaderboard(pinCode, getSessionOrThrow(pinCode));
        } catch (SecurityException e) {
            // Same policy as rejected host commands: log only, don't let a forger spam /error for everyone
            log.warn("Rejected answer for game {} (player {}): {}", pinCode, request.getPlayerId(), e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException | ResourceNotFoundException e) {
            sendError(pinCode, e.getMessage());
        }
    }

    public void broadcastGameStarted(String pinCode, GameSession session) {
        // Broadcast started event
        send(TOPIC_PREFIX + pinCode + "/started", new GameStartedDTO());

        // Broadcast first question
        broadcastCurrentQuestion(pinCode, session);
    }

    public void finalizeUnansweredPlayers(String pinCode, GameSession session) {
        Question currentQuestion = getCurrentQuestion(session);

        if (currentQuestion == null) {
            return;
        }

        List<AnswerResultDTO> results = playerService.finalizeUnansweredPlayers(session, currentQuestion);

        results.forEach(result -> send(TOPIC_PREFIX + pinCode + "/answer-result", result));
    }

    // ================== BROADCAST METHODS ==================

    public void broadcastRoundResults(String pinCode, List<AnswerResultDTO> roundResults) {
        send(TOPIC_PREFIX + pinCode + "/round-results", roundResults);
    }

    // Public so the REST join endpoint can announce the new player as well
    public void broadcastPlayerList(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        send(TOPIC_PREFIX + pinCode + "/players", GameDtoMapper.toPlayerInfos(players));
    }

    private void broadcastLeaderboard(String pinCode, GameSession session) {
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        send(TOPIC_PREFIX + pinCode + "/leaderboard", GameDtoMapper.toLeaderboard(players));
    }

    public void broadcastCurrentQuestion(String pinCode, GameSession session) {
        Question question = getCurrentQuestion(session);

        if (question == null) {
            return;
        }

        send(TOPIC_PREFIX + pinCode + "/question", GameDtoMapper.toQuestionDTO(question, session.getQuestionStartedAt()));
    }

    public void broadcastFinalResults(String pinCode, GameSession session) {
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        FinalResultDTO finalResult = FinalResultDTO.builder()
                .quizTitle(session.getQuiz().getTitle())
                .leaderboard(GameDtoMapper.toLeaderboard(players))
                .build();

        send(TOPIC_PREFIX + pinCode + "/ended", finalResult);
    }

    // ================== HELPERS ==================

    public void sendError(String pinCode, String message) {
        ErrorDTO error = ErrorDTO.builder()
                .message(message)
                .build();

        send(TOPIC_PREFIX + pinCode + "/error", error);
    }

    // Inside a transaction, wait for the commit so clients never see state that isn't saved yet
    // (e.g. /state called right after /question) and nothing is sent if the transaction rolls back.
    // Outside a transaction (unit tests, handlePlayerAnswer after submitAnswer committed) send right away.
    private void send(String destination, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(destination, payload);
                }
            });
        } else {
            messagingTemplate.convertAndSend(destination, payload);
        }
    }

    private GameSession getSessionOrThrow(String pinCode) {
        return gameSessionRepository.findByPinCode(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("Game session not found: " + pinCode));
    }

    private Question getCurrentQuestion(GameSession session) {
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(session.getQuiz().getId());
        int index = session.getCurrentQuestionIndex();

        if (index >= 0 && index < questions.size()) {
            return questions.get(index);
        }

        return null;
    }
}
