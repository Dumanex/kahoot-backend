package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.*;
import com.kahoot.kahoot_backend.config.PlayerPrincipal;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.*;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerAnswerRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.Principal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameMessageService {
    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAnswerRepository playerAnswerRepository;
    private final QuestionRepository questionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlayerService playerService;

    private static final String TOPIC_PREFIX = "/topic/game/";

    // Private destinations; the client subscribes to /user/queue/..., Spring delivers only to that user's connections
    private static final String ANSWER_ACCEPTED_QUEUE = "/queue/answer-accepted";
    private static final String ANSWER_RESULT_QUEUE = "/queue/answer-result";
    private static final String ERRORS_QUEUE = "/queue/errors";

    // ================== PLAYER ANSWER ==================
    public void handlePlayerAnswer(String pinCode, AnswerSubmitRequest request, Principal principal) {
        try {
            AnswerAcceptedDTO accepted = playerService.submitAnswer(pinCode, request.getPlayerId(), request);

            sendToUser(PlayerPrincipal.nameOf(request.getPlayerId()), ANSWER_ACCEPTED_QUEUE, accepted);

            broadcastAnsweredCount(pinCode, accepted.getQuestionId());
        } catch (SecurityException e) {
            log.warn("Rejected answer for game {} (player {}): {}", pinCode, request.getPlayerId(), e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException | ResourceNotFoundException e) {
            sendErrorToUser(principal, e.getMessage());
        }
    }

    public void broadcastGameStarted(String pinCode, GameSession session) {
        send(TOPIC_PREFIX + pinCode + "/started", new GameStartedDTO());

        broadcastCurrentQuestion(pinCode, session);
    }

    public void sendAnswerResults(List<AnswerResultDTO> results) {
        results.forEach(result -> sendToUser(PlayerPrincipal.nameOf(result.getPlayerId()), ANSWER_RESULT_QUEUE, result));
    }

    // ================== BROADCAST METHODS ==================

    public void broadcastRoundResults(String pinCode, List<AnswerResultDTO> roundResults) {
        send(TOPIC_PREFIX + pinCode + "/round-results", roundResults);
    }

    public void broadcastPlayerList(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        send(TOPIC_PREFIX + pinCode + "/players", GameDtoMapper.toPlayerInfos(players));
    }

    private void broadcastAnsweredCount(String pinCode, Long questionId) {
        GameSession session = getSessionOrThrow(pinCode);

        AnsweredCountDTO answeredCount = AnsweredCountDTO.builder()
                .answeredCount(playerAnswerRepository.countByQuestionIdAndPlayerGameSessionId(questionId, session.getId()))
                .totalPlayers(playerRepository.countByGameSessionId(session.getId()))
                .build();

        send(TOPIC_PREFIX + pinCode + "/answered", answeredCount);
    }

    public void broadcastLeaderboard(String pinCode, GameSession session) {
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

    public void sendErrorToUser(Principal principal, String message) {
        if (principal == null) {
            log.warn("WebSocket error for anonymous connection: {}", message);
            return;
        }

        ErrorDTO error = ErrorDTO.builder()
                .message(message)
                .build();

        sendToUser(principal.getName(), ERRORS_QUEUE, error);
    }

    private void send(String destination, Object payload) {
        afterCommit(() -> messagingTemplate.convertAndSend(destination, payload));
    }

    private void sendToUser(String user, String destination, Object payload) {
        afterCommit(() -> messagingTemplate.convertAndSendToUser(user, destination, payload));
    }

    // Inside a transaction, wait for the commit so clients never see state that isn't saved yet
    // (e.g. /state called right after /question) and nothing is sent if the transaction rolls back.
    // Outside a transaction (unit tests, handlePlayerAnswer after submitAnswer committed) send right away.
    private void afterCommit(Runnable sendAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAction.run();
                }
            });
        } else {
            sendAction.run();
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
