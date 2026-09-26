package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerAcceptedDTO;
import com.kahoot.kahoot_backend.DTOs.game.AnswerResultDTO;
import com.kahoot.kahoot_backend.DTOs.game.AnswerSubmitRequest;
import com.kahoot.kahoot_backend.DTOs.game.AnsweredCountDTO;
import com.kahoot.kahoot_backend.DTOs.game.ErrorDTO;
import com.kahoot.kahoot_backend.config.PlayerPrincipal;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.model.Answer;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Question;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerAnswerRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameMessageServiceTest {
    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerAnswerRepository playerAnswerRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PlayerService playerService;

    @InjectMocks
    private GameMessageService gameMessageService;

    private Quiz quiz;
    private Question question;
    private static final String PIN = "123456";

    @BeforeEach
    void setUp() {
        quiz = Quiz.builder()
                .id(50L)
                .title("Quiz")
                .build();

        Answer correctAnswer = Answer.builder()
                .id(1L)
                .answerText("A")
                .isCorrect(true)
                .orderIndex(0)
                .build();

        question = Question.builder()
                .id(10L)
                .quiz(quiz)
                .timeLimitSeconds(20)
                .orderIndex(0)
                .answers(List.of(correctAnswer))
                .build();
    }

    private GameSession session(GameSessionStatus status, int currentQuestionIndex) {
        return GameSession.builder()
                .id(200L)
                .quiz(quiz)
                .pinCode(PIN)
                .status(status)
                .currentQuestionIndex(currentQuestionIndex)
                .build();
    }

    // Broadcast Player List (called after REST join)
    @Test
    void broadcastPlayerList_shouldSendPlayersToTopic() {
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(session(GameSessionStatus.WAITING, 0)));
        when(playerRepository.findByGameSessionId(200L)).thenReturn(List.of());

        gameMessageService.broadcastPlayerList(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/players"), any(Object.class));
    }

    // Handle Player Answer
    private AnswerSubmitRequest answerRequest() {
        return AnswerSubmitRequest.builder()
                .playerId(5L)
                .rejoinToken("token-1")
                .questionId(10L)
                .answerId(1L)
                .build();
    }

    @Test
    void handlePlayerAnswer_validAnswer_shouldSendAcceptedPrivatelyAndCountPublicly() {
        AnswerSubmitRequest request = answerRequest();
        AnswerAcceptedDTO accepted = AnswerAcceptedDTO.builder().questionId(10L).chosenAnswerId(1L).build();

        when(playerService.submitAnswer(PIN, 5L, request)).thenReturn(accepted);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(session(GameSessionStatus.IN_PROGRESS, 0)));
        when(playerAnswerRepository.countByQuestionIdAndPlayerGameSessionId(10L, 200L)).thenReturn(1);
        when(playerRepository.countByGameSessionId(200L)).thenReturn(3);

        gameMessageService.handlePlayerAnswer(PIN, request, new PlayerPrincipal(5L));

        verify(messagingTemplate).convertAndSendToUser("player:5", "/queue/answer-accepted", accepted);
        verify(messagingTemplate).convertAndSend("/topic/game/" + PIN + "/answered",
                (Object) AnsweredCountDTO.builder().answeredCount(1).totalPlayers(3).build());
        // Nothing about the answer itself goes to everyone
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/leaderboard"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), eq("/queue/errors"), any(Object.class));
    }

    @Test
    void handlePlayerAnswer_submitThrows_shouldSendErrorOnlyToSender() {
        AnswerSubmitRequest request = answerRequest();
        when(playerService.submitAnswer(PIN, 5L, request)).thenThrow(new IllegalStateException("Time is up for this question"));

        gameMessageService.handlePlayerAnswer(PIN, request, new PlayerPrincipal(5L));

        verify(messagingTemplate).convertAndSendToUser("player:5", "/queue/errors",
                ErrorDTO.builder().message("Time is up for this question").build());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void handlePlayerAnswer_submitThrowsForAnonymousConnection_shouldOnlyLog() {
        AnswerSubmitRequest request = answerRequest();
        when(playerService.submitAnswer(PIN, 5L, request)).thenThrow(new IllegalStateException("Game is not IN_PROGRESS"));

        gameMessageService.handlePlayerAnswer(PIN, request, null);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handlePlayerAnswer_wrongToken_shouldOnlyLog() {
        AnswerSubmitRequest request = answerRequest();
        when(playerService.submitAnswer(PIN, 5L, request)).thenThrow(new SecurityException("Invalid rejoin token"));

        gameMessageService.handlePlayerAnswer(PIN, request, new PlayerPrincipal(7L));

        verifyNoInteractions(messagingTemplate);
    }

    // Send Error To User (host commands)
    @Test
    void sendErrorToUser_host_shouldSendToHostsUsername() {
        Principal host = () -> "host";

        gameMessageService.sendErrorToUser(host, "Game is not IN_PROGRESS");

        verify(messagingTemplate).convertAndSendToUser("host", "/queue/errors",
                ErrorDTO.builder().message("Game is not IN_PROGRESS").build());
    }

    // Broadcast Game Started
    @Test
    void broadcastGameStarted_shouldSendStartedAndFirstQuestion() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        gameMessageService.broadcastGameStarted(PIN, inProgressSession);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/started"), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/question"), any(Object.class));
    }

    // Send Answer Results (after finalize)
    @Test
    void sendAnswerResults_shouldSendEachResultOnlyToItsPlayer() {
        AnswerResultDTO result5 = AnswerResultDTO.builder().playerId(5L).nickname("p1").isCorrect(true).build();
        AnswerResultDTO result6 = AnswerResultDTO.builder().playerId(6L).nickname("p2").isCorrect(false).build();

        gameMessageService.sendAnswerResults(List.of(result5, result6));

        verify(messagingTemplate).convertAndSendToUser("player:5", "/queue/answer-result", result5);
        verify(messagingTemplate).convertAndSendToUser("player:6", "/queue/answer-result", result6);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // Broadcast Final Results
    @Test
    void broadcastFinalResults_shouldSendEndedWithLeaderboard() {
        when(playerRepository.findByGameSessionId(200L)).thenReturn(List.of());

        gameMessageService.broadcastFinalResults(PIN, session(GameSessionStatus.COMPLETED, 0));

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/ended"), any(Object.class));
    }

    // Inside a transaction: nothing is sent until the commit
    @Test
    void broadcast_insideTransaction_shouldSendOnlyAfterCommit() {
        when(playerRepository.findByGameSessionId(200L)).thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            gameMessageService.broadcastFinalResults(PIN, session(GameSessionStatus.COMPLETED, 0));

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/ended"), any(Object.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
