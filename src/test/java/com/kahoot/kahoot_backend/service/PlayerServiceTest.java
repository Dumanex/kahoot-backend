package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerResultDTO;
import com.kahoot.kahoot_backend.DTOs.game.AnswerSubmitRequest;
import com.kahoot.kahoot_backend.DTOs.game.PlayerResponse;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.*;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlayerServiceTest {
    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerAnswerRepository playerAnswerRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ScoringService scoringService;

    @InjectMocks
    private PlayerService playerService;

    private Quiz quiz;
    private Question question;
    private Answer correctAnswer;
    private Answer wrongAnswer;
    private GameSession session;
    private Player player;

    @BeforeEach
    void setUp() {
        quiz = Quiz.builder()
                .id(50L)
                .title("Quiz")
                .build();

        correctAnswer = Answer.builder()
                .id(1L)
                .answerText("A")
                .isCorrect(true)
                .orderIndex(0)
                .build();

        wrongAnswer = Answer.builder()
                .id(2L)
                .answerText("B")
                .isCorrect(false)
                .orderIndex(1)
                .build();

        question = Question.builder()
                .id(10L)
                .quiz(quiz)
                .timeLimitSeconds(20)
                .orderIndex(0)
                .answers(List.of(correctAnswer, wrongAnswer))
                .build();

        session = GameSession.builder()
                .id(20L)
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.IN_PROGRESS)
                .currentQuestionIndex(0)
                .build();

        player = Player.builder()
                .id(5L)
                .gameSession(session)
                .nickname("p1")
                .score(0)
                .streak(2)
                .build();
    }

    private AnswerSubmitRequest submitRequest(Long answerId) {
        return AnswerSubmitRequest.builder()
                .playerId(5L)
                .questionId(10L)
                .answerId(answerId)
                .responseTimeMs(3000)
                .build();
    }

    // Join Game
    @Test
    void joinGame_validNickname_shouldReturnPlayerResponse() {
        GameSession waitingSession = GameSession.builder()
                .id(200L)
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .build();

        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(waitingSession));
        when(playerRepository.existsByGameSessionIdAndNickname(200L, "newPlayer")).thenReturn(false);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerResponse response = playerService.joinGame("123456", "newPlayer");

        assertThat(response.getNickname()).isEqualTo("newPlayer");
        assertThat(response.getScore()).isZero();
    }

    @Test
    void joinGame_duplicateNickname_shouldThrow() {
        GameSession waitingSession = GameSession.builder()
                .id(200L)
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .build();

        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(waitingSession));
        when(playerRepository.existsByGameSessionIdAndNickname(200L, "p1")).thenReturn(true);

        assertThatThrownBy(() -> playerService.joinGame("123456", "p1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void joinGame_sessionNotWaiting_shouldThrow() {
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> playerService.joinGame("123456", "newPlayer"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void joinGame_sessionNotFound_shouldThrow() {
        when(gameSessionRepository.findByPinCode("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.joinGame("000000", "newPlayer"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Submit Answer
    @Test
    void submitAnswer_correctAnswer_shouldReturnCorrectResultAndUpdateScore() {
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);
        when(scoringService.calculatePoints(eq(true), eq(3000), eq(20), eq(2))).thenReturn(1200);
        when(scoringService.calculateStreak(true, 2)).thenReturn(3);

        AnswerResultDTO result = playerService.submitAnswer("123456", 5L, submitRequest(1L));

        assertThat(result.getIsCorrect()).isTrue();
        assertThat(result.getChosenAnswerId()).isEqualTo(1L);
        assertThat(result.getPointsEarned()).isEqualTo(1200);
        assertThat(result.getTotalScore()).isEqualTo(1200);
        assertThat(result.getStreak()).isEqualTo(3);
        verify(playerAnswerRepository).save(any(PlayerAnswer.class));
    }

    @Test
    void submitAnswer_incorrectAnswer_shouldReturnZeroPointsAndResetStreak() {
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);
        when(scoringService.calculatePoints(eq(false), eq(3000), eq(20), eq(2))).thenReturn(0);
        when(scoringService.calculateStreak(false, 2)).thenReturn(0);

        AnswerResultDTO result = playerService.submitAnswer("123456", 5L, submitRequest(2L));

        assertThat(result.getIsCorrect()).isFalse();
        assertThat(result.getChosenAnswerId()).isEqualTo(2L);
        assertThat(result.getPointsEarned()).isZero();
        assertThat(result.getStreak()).isZero();
    }

    @Test
    void submitAnswer_sessionNotInProgress_shouldThrow() {
        GameSession waitingSession = GameSession.builder()
                .id(200L)
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .currentQuestionIndex(0)
                .build();

        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submitAnswer_playerNotInSession_shouldThrow() {
        GameSession otherSession = GameSession.builder()
                .id(999L)
                .quiz(quiz)
                .pinCode("999999")
                .status(GameSessionStatus.WAITING)
                .currentQuestionIndex(0)
                .build();

        Player otherPlayer = Player.builder()
                .id(5L)
                .gameSession(otherSession)
                .nickname("p1")
                .score(0)
                .streak(0)
                .build();

        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(otherPlayer));

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitAnswer_mismatchedQuestionId_shouldThrow() {
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        AnswerSubmitRequest req = AnswerSubmitRequest.builder()
                .playerId(5L)
                .questionId(999L)
                .answerId(1L)
                .responseTimeMs(3000)
                .build();

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitAnswer_alreadyAnswered_shouldThrow() {
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitAnswer_noCorrectAnswerDefined_shouldThrowIllegalState() {
        Question brokenQuestion = Question.builder()
                .id(10L)
                .quiz(quiz)
                .timeLimitSeconds(20)
                .orderIndex(0)
                .answers(List.of(wrongAnswer))
                .build();

        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(brokenQuestion));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(2L)))
                .isInstanceOf(IllegalStateException.class);
    }

    // Finalize Unanswered Players
    @Test
    void finalizeUnansweredPlayers_playerDidNotAnswer_shouldResetStreakAndReturnResult() {
        when(playerRepository.findByGameSessionId(20L)).thenReturn(List.of(player));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AnswerResultDTO> results = playerService.finalizeUnansweredPlayers(session, question);

        assertThat(results).hasSize(1);
        AnswerResultDTO result = results.get(0);
        assertThat(result.getPlayerId()).isEqualTo(5L);
        assertThat(result.getIsCorrect()).isFalse();
        assertThat(result.getChosenAnswerId()).isNull();
        assertThat(result.getPointsEarned()).isZero();
        assertThat(result.getStreak()).isZero();
        assertThat(result.getCorrectAnswer().getId()).isEqualTo(1L);
        assertThat(player.getStreak()).isZero();
        verify(playerRepository).save(player);
    }

    @Test
    void finalizeUnansweredPlayers_playerAlreadyAnswered_shouldBeExcluded() {
        when(playerRepository.findByGameSessionId(20L)).thenReturn(List.of(player));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(true);

        List<AnswerResultDTO> results = playerService.finalizeUnansweredPlayers(session, question);

        assertThat(results).isEmpty();
        verify(playerRepository, never()).save(any(Player.class));
    }










}
