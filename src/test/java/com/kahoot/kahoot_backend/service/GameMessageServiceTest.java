package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerResultDTO;
import com.kahoot.kahoot_backend.DTOs.game.AnswerSubmitRequest;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.model.Answer;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Question;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
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
    @Test
    void handlePlayerAnswer_validAnswer_shouldBroadcastResultAndLeaderboard() {
        AnswerResultDTO result = AnswerResultDTO.builder()
                .playerId(5L)
                .nickname("p1")
                .isCorrect(true)
                .build();
        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .playerId(5L)
                .questionId(10L)
                .answerId(1L)
                .responseTimeMs(2000)
                .build();

        when(playerService.submitAnswer(PIN, 5L, request)).thenReturn(result);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(session(GameSessionStatus.IN_PROGRESS,0)));
        when(playerRepository.findByGameSessionId(200L)).thenReturn(List.of());

        gameMessageService.handlePlayerAnswer(PIN, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/answer-result"), eq(result));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/leaderboard"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
    }

    @Test
    void handlePlayerAnswer_submitThrows_shouldSendErrorOnly() {
        AnswerSubmitRequest request = AnswerSubmitRequest.builder()
                .playerId(5L)
                .questionId(10L)
                .answerId(1L)
                .responseTimeMs(2000)
                .build();

        when(playerService.submitAnswer(PIN, 5L, request)).thenThrow(new IllegalStateException("Game is not IN_PROGRESS"));

        gameMessageService.handlePlayerAnswer(PIN, request);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/answer-result"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/leaderboard"), any(Object.class));
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

    // Finalize Unanswered Players
    @Test
    void finalizeUnansweredPlayers_playersDidNotAnswer_shouldBroadcastResultForEachOfThem() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        AnswerResultDTO unansweredResult = AnswerResultDTO.builder()
                .playerId(5L)
                .nickname("p1")
                .isCorrect(false)
                .chosenAnswerId(null)
                .pointsEarned(0)
                .streak(0)
                .build();

        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerService.finalizeUnansweredPlayers(inProgressSession, question)).thenReturn(List.of(unansweredResult));

        gameMessageService.finalizeUnansweredPlayers(PIN, inProgressSession);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/answer-result"), eq(unansweredResult));
        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    void finalizeUnansweredPlayers_noCurrentQuestion_shouldDoNothing() {
        GameSession outOfRangeSession = session(GameSessionStatus.IN_PROGRESS, 5);
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        gameMessageService.finalizeUnansweredPlayers(PIN, outOfRangeSession);

        verify(playerService, never()).finalizeUnansweredPlayers(any(), any());
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
