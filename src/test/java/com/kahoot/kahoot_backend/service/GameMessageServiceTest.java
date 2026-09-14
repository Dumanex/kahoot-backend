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

    // Handle Player Join
    @Test
    void handlePlayerJoin_validNickname_shouldBroadcastPlayerList() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(waitingSession));
        when(playerRepository.findByGameSessionId(200L)).thenReturn(List.of());

        gameMessageService.handlePlayerJoin(PIN, "newPlayer");

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/players"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
    }

    @Test
    void handlePlayerJoin_playerServiceThrows_shouldSendErrorAndSkipBroadcast() {
        doThrow(new IllegalArgumentException("Nickname 'p1' is already taken in this game"))
                .when(playerService).joinGame(PIN, "p1");

        gameMessageService.handlePlayerJoin(PIN, "p1");

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/players"), any(Object.class));
        verify(gameSessionRepository, never()).findByPinCode(anyString());
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

    // Handle Game Start
    @Test
    void handleGameStart_fromWaiting_shouldTransitionAndBroadcastStartedAndQuestion() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(waitingSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        gameMessageService.handleGameStart(PIN);

        verify(gameSessionRepository).save(argThat(s -> s.getStatus() == GameSessionStatus.IN_PROGRESS));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/started"), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/question"), any(Object.class));
    }

    @Test
    void handleGameStart_alreadyInProgress_shouldSendError() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(inProgressSession));

        gameMessageService.handleGameStart(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    void handleGameStart_completed_shouldSendError() {
        GameSession completedSession = session(GameSessionStatus.COMPLETED, 4);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(completedSession));

        gameMessageService.handleGameStart(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
        verify(gameSessionRepository, never()).save(any());
    }

    // Handle Next Question
    @Test
    void handleNextQuestion_notLastQuestion_shouldAdvanceAndBroadcastQuestion() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question, question));

        gameMessageService.handleNextQuestion(PIN);

        verify(playerService).finalizeUnansweredPlayers(inProgressSession, question);
        verify(gameSessionRepository).save(argThat(s -> s.getCurrentQuestionIndex() == 1));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/question"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/ended"), any(Object.class));
    }

    @Test
    void handleNextQuestion_playersDidNotAnswer_shouldBroadcastResultForEachOfThem() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        AnswerResultDTO unansweredResult = AnswerResultDTO.builder()
                .playerId(5L)
                .nickname("p1")
                .isCorrect(false)
                .chosenAnswerId(null)
                .pointsEarned(0)
                .streak(0)
                .build();

        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question, question));
        when(playerService.finalizeUnansweredPlayers(inProgressSession, question)).thenReturn(List.of(unansweredResult));

        gameMessageService.handleNextQuestion(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/answer-result"), eq(unansweredResult));
    }

    @Test
    void handleNextQuestion_lastQuestion_shouldAutoCompleteAndBroadcastFinalResults() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 4);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(playerRepository.findByGameSessionId(200L)).thenReturn(List.of());

        gameMessageService.handleNextQuestion(PIN);

        verify(gameSessionRepository).save(argThat(s -> s.getStatus() == GameSessionStatus.COMPLETED));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/ended"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/question"), any(Object.class));
    }

    @Test
    void handleNextQuestion_notInProgress_shouldSendError() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(waitingSession));

        gameMessageService.handleNextQuestion(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
        verify(gameSessionRepository, never()).save(any());
    }

    // Handle Finalize Unanswered
    @Test
    void handleFinalizeUnanswered_inProgress_shouldBroadcastResultsWithoutAdvancing() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        AnswerResultDTO unansweredResult = AnswerResultDTO.builder()
                .playerId(5L)
                .nickname("p1")
                .isCorrect(false)
                .chosenAnswerId(null)
                .pointsEarned(0)
                .streak(0)
                .build();

        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerService.finalizeUnansweredPlayers(inProgressSession, question)).thenReturn(List.of(unansweredResult));

        gameMessageService.handleFinalizeUnanswered(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/answer-result"), eq(unansweredResult));
        verify(gameSessionRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game/" + PIN + "/question"), any(Object.class));
    }

    @Test
    void handleFinalizeUnanswered_notInProgress_shouldSendError() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(waitingSession));

        gameMessageService.handleFinalizeUnanswered(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
        verify(playerService, never()).finalizeUnansweredPlayers(any(), any());
    }

    // Handle End Game
    @Test
    void handleEndGame_fromInProgress_shouldCompleteAndBroadcastFinalResults() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(inProgressSession));
        when(playerRepository.findByGameSessionId(200L)).thenReturn(List.of());

        gameMessageService.handleEndGame(PIN);

        verify(gameSessionRepository).save(argThat(s -> s.getStatus() == GameSessionStatus.COMPLETED));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/ended"), any(Object.class));
    }

    @Test
    void handleEndGame_notInProgress_shouldSendError() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode(PIN)).thenReturn(Optional.of(waitingSession));

        gameMessageService.handleEndGame(PIN);

        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + PIN + "/error"), any(Object.class));
        verify(gameSessionRepository, never()).save(any());
    }
}
