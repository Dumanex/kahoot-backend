package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerAcceptedDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
                .questionStartedAt(LocalDateTime.now().minusSeconds(6))
                .build();

        player = Player.builder()
                .id(5L)
                .gameSession(session)
                .nickname("p1")
                .score(0)
                .streak(2)
                .rejoinToken("token-1")
                .build();
    }

    private AnswerSubmitRequest submitRequest(Long answerId) {
        return AnswerSubmitRequest.builder()
                .playerId(5L)
                .rejoinToken("token-1")
                .questionId(10L)
                .answerId(answerId)
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
        assertThat(response.getRejoinToken()).isNotBlank();
        assertThat(response.getAnsweredCurrentQuestion()).isFalse();
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
    void submitAnswer_correctAnswer_shouldStoreAnswerWithServerTimeAndNotChangeScoreYet() {
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);
        when(scoringService.calculatePoints(eq(true), anyInt(), eq(20), eq(2))).thenReturn(1200);

        AnswerAcceptedDTO accepted = playerService.submitAnswer("123456", 5L, submitRequest(1L));

        assertThat(accepted.getQuestionId()).isEqualTo(10L);
        assertThat(accepted.getChosenAnswerId()).isEqualTo(1L);

        ArgumentCaptor<PlayerAnswer> saved = ArgumentCaptor.forClass(PlayerAnswer.class);
        verify(playerAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getIsCorrect()).isTrue();
        assertThat(saved.getValue().getPointsEarned()).isEqualTo(1200);
        assertThat(saved.getValue().getResponseTimeMs()).isBetween(3000, 4000);

        assertThat(player.getScore()).isZero();
        assertThat(player.getStreak()).isEqualTo(2);
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void submitAnswer_incorrectAnswer_shouldStoreZeroPoints() {
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);
        when(scoringService.calculatePoints(eq(false), anyInt(), eq(20), eq(2))).thenReturn(0);

        AnswerAcceptedDTO accepted = playerService.submitAnswer("123456", 5L, submitRequest(2L));

        assertThat(accepted.getChosenAnswerId()).isEqualTo(2L);

        ArgumentCaptor<PlayerAnswer> saved = ArgumentCaptor.forClass(PlayerAnswer.class);
        verify(playerAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getIsCorrect()).isFalse();
        assertThat(saved.getValue().getPointsEarned()).isZero();
    }

    @Test
    void submitAnswer_duringReadyPhase_shouldThrowNotStarted() {
        session.setQuestionStartedAt(LocalDateTime.now().minusSeconds(1));
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Answering has not started yet");
        verify(playerAnswerRepository, never()).save(any(PlayerAnswer.class));
    }

    @Test
    void submitAnswer_slightlyBeforeAnsweringOpens_shouldAcceptWithZeroTime() {
        session.setQuestionStartedAt(LocalDateTime.now().minusNanos(2_800_000_000L));
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);
        when(scoringService.calculatePoints(eq(true), eq(0), eq(20), eq(2))).thenReturn(1500);

        playerService.submitAnswer("123456", 5L, submitRequest(1L));

        ArgumentCaptor<PlayerAnswer> saved = ArgumentCaptor.forClass(PlayerAnswer.class);
        verify(playerAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getResponseTimeMs()).isZero();
    }

    @Test
    void submitAnswer_withinLateTolerance_shouldAcceptWithFullTime() {
        session.setQuestionStartedAt(LocalDateTime.now().minusNanos(23_500_000_000L));
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);
        when(scoringService.calculatePoints(eq(true), eq(20000), eq(20), eq(2))).thenReturn(1000);

        playerService.submitAnswer("123456", 5L, submitRequest(1L));

        ArgumentCaptor<PlayerAnswer> saved = ArgumentCaptor.forClass(PlayerAnswer.class);
        verify(playerAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getResponseTimeMs()).isEqualTo(20000);
    }

    @Test
    void submitAnswer_afterDeadline_shouldThrowTimeIsUp() {
        session.setQuestionStartedAt(LocalDateTime.now().minusSeconds(25));
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Time is up for this question");
        verify(playerAnswerRepository, never()).save(any(PlayerAnswer.class));
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

        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(waitingSession));

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

        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(otherPlayer));

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitAnswer_mismatchedQuestionId_shouldThrow() {
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        AnswerSubmitRequest req = AnswerSubmitRequest.builder()
                .playerId(5L)
                .rejoinToken("token-1")
                .questionId(999L)
                .answerId(1L)
                .build();

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitAnswer_alreadyAnswered_shouldThrow() {
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitAnswer_wrongRejoinToken_shouldThrowSecurityExceptionAndNotSave() {
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        AnswerSubmitRequest req = submitRequest(1L);
        req.setRejoinToken("someone-elses-token");

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, req))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid rejoin token");
        verify(playerAnswerRepository, never()).save(any(PlayerAnswer.class));
    }

    @Test
    void submitAnswer_questionAlreadyFinalized_shouldThrowIllegalState() {
        session.setQuestionFinalized(true);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Question is already finalized");
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

        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(brokenQuestion));
        when(playerAnswerRepository.existsByPlayerIdAndQuestionId(5L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> playerService.submitAnswer("123456", 5L, submitRequest(2L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applyRoundScores_correctAnswer_shouldAddPointsAndIncreaseStreak() {
        PlayerAnswer playerAnswer = PlayerAnswer.builder()
                .player(player)
                .question(question)
                .answer(correctAnswer)
                .isCorrect(true)
                .pointsEarned(1200)
                .build();
        when(playerAnswerRepository.findByQuestionIdAndPlayerGameSessionId(10L, 20L)).thenReturn(List.of(playerAnswer));
        when(playerRepository.findByGameSessionId(20L)).thenReturn(List.of(player));
        when(scoringService.calculateStreak(true, 2)).thenReturn(3);

        List<AnswerResultDTO> results = playerService.applyRoundScores(session, question);

        assertThat(results).hasSize(1);
        AnswerResultDTO result = results.get(0);
        assertThat(result.getIsCorrect()).isTrue();
        assertThat(result.getChosenAnswerId()).isEqualTo(1L);
        assertThat(result.getPointsEarned()).isEqualTo(1200);
        assertThat(result.getTotalScore()).isEqualTo(1200);
        assertThat(result.getStreak()).isEqualTo(3);
        assertThat(player.getScore()).isEqualTo(1200);
        verify(playerRepository).save(player);
    }

    @Test
    void applyRoundScores_playerDidNotAnswer_shouldResetStreakAndReturnResult() {
        when(playerAnswerRepository.findByQuestionIdAndPlayerGameSessionId(10L, 20L)).thenReturn(List.of());
        when(playerRepository.findByGameSessionId(20L)).thenReturn(List.of(player));
        when(scoringService.calculateStreak(false, 2)).thenReturn(0);

        List<AnswerResultDTO> results = playerService.applyRoundScores(session, question);

        assertThat(results).hasSize(1);
        AnswerResultDTO result = results.get(0);
        assertThat(result.getPlayerId()).isEqualTo(5L);
        assertThat(result.getIsCorrect()).isFalse();
        assertThat(result.getChosenAnswerId()).isNull();
        assertThat(result.getPointsEarned()).isZero();
        assertThat(result.getStreak()).isZero();
        assertThat(result.getCorrectAnswer().getId()).isEqualTo(1L);
        assertThat(player.getScore()).isZero();
        assertThat(player.getStreak()).isZero();
        verify(playerRepository).save(player);
    }

    @Test
    void isValidPlayer_matchingToken_shouldReturnTrue() {
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThat(playerService.isValidPlayer(5L, "token-1")).isTrue();
    }

    @Test
    void isValidPlayer_wrongTokenOrUnknownPlayer_shouldReturnFalse() {
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(playerRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(playerService.isValidPlayer(5L, "wrong")).isFalse();
        assertThat(playerService.isValidPlayer(77L, "token-1")).isFalse();
    }

    @Test
    void rejoinGame_answeredButNotFinalized_shouldReturnOnlyChosenAnswer() {
        PlayerAnswer playerAnswer = PlayerAnswer.builder()
                .player(player)
                .question(question)
                .answer(wrongAnswer)
                .isCorrect(false)
                .pointsEarned(0)
                .build();
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.findByPlayerIdAndQuestionId(5L, 10L)).thenReturn(Optional.of(playerAnswer));

        PlayerResponse response = playerService.rejoinGame("123456", 5L, "token-1");

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getNickname()).isEqualTo("p1");
        assertThat(response.getRejoinToken()).isEqualTo("token-1");
        assertThat(response.getAnsweredCurrentQuestion()).isTrue();
        assertThat(response.getChosenAnswerId()).isEqualTo(2L);
        assertThat(response.getCurrentAnswerResult()).isNull();
    }

    @Test
    void rejoinGame_answeredAndFinalized_shouldReturnOwnResult() {
        session.setQuestionFinalized(true);
        PlayerAnswer playerAnswer = PlayerAnswer.builder()
                .player(player)
                .question(question)
                .answer(wrongAnswer)
                .isCorrect(false)
                .pointsEarned(0)
                .build();
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.findByPlayerIdAndQuestionId(5L, 10L)).thenReturn(Optional.of(playerAnswer));

        PlayerResponse response = playerService.rejoinGame("123456", 5L, "token-1");

        assertThat(response.getChosenAnswerId()).isEqualTo(2L);
        assertThat(response.getCurrentAnswerResult().getChosenAnswerId()).isEqualTo(2L);
        assertThat(response.getCurrentAnswerResult().getIsCorrect()).isFalse();
        assertThat(response.getCurrentAnswerResult().getCorrectAnswer().getId()).isEqualTo(1L);
    }

    @Test
    void rejoinGame_notAnsweredAndFinalized_shouldReturnUnansweredResult() {
        session.setQuestionFinalized(true);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.findByPlayerIdAndQuestionId(5L, 10L)).thenReturn(Optional.empty());

        PlayerResponse response = playerService.rejoinGame("123456", 5L, "token-1");

        assertThat(response.getAnsweredCurrentQuestion()).isFalse();
        assertThat(response.getChosenAnswerId()).isNull();
        assertThat(response.getCurrentAnswerResult().getChosenAnswerId()).isNull();
        assertThat(response.getCurrentAnswerResult().getPointsEarned()).isZero();
    }

    @Test
    void rejoinGame_inProgressNotAnswered_shouldHaveNoResult() {
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question));
        when(playerAnswerRepository.findByPlayerIdAndQuestionId(5L, 10L)).thenReturn(Optional.empty());

        PlayerResponse response = playerService.rejoinGame("123456", 5L, "token-1");

        assertThat(response.getAnsweredCurrentQuestion()).isFalse();
        assertThat(response.getCurrentAnswerResult()).isNull();
    }

    @Test
    void rejoinGame_waiting_shouldReturnPlayerWithoutCheckingAnswers() {
        session.setStatus(GameSessionStatus.WAITING);
        player.setRejoinToken("token-1");
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        PlayerResponse response = playerService.rejoinGame("123456", 5L, "token-1");

        assertThat(response.getAnsweredCurrentQuestion()).isFalse();
        verify(playerAnswerRepository, never()).findByPlayerIdAndQuestionId(any(), any());
    }

    @Test
    void rejoinGame_wrongToken_shouldThrowSecurityException() {
        player.setRejoinToken("token-1");
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerService.rejoinGame("123456", 5L, "wrong"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid rejoin token");
    }

    @Test
    void rejoinGame_playerWithoutToken_shouldThrowSecurityException() {
        player.setRejoinToken(null);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerService.rejoinGame("123456", 5L, "token-1"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejoinGame_playerFromOtherGame_shouldThrowSecurityException() {
        GameSession otherSession = GameSession.builder().id(999L).quiz(quiz).build();
        player.setGameSession(otherSession);
        player.setRejoinToken("token-1");
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerService.rejoinGame("123456", 5L, "token-1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid rejoin token");
    }

    @Test
    void rejoinGame_unknownPlayer_shouldThrowSecurityException() {
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));
        when(playerRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.rejoinGame("123456", 77L, "token-1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid rejoin token");
    }

    @Test
    void rejoinGame_completedGame_shouldThrowIllegalState() {
        session.setStatus(GameSessionStatus.COMPLETED);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> playerService.rejoinGame("123456", 5L, "token-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Can only rejoin while game is WAITING or IN_PROGRESS. Current status: COMPLETED");
    }









}
