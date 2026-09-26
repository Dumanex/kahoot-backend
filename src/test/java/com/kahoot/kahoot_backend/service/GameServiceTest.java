package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerResultDTO;
import com.kahoot.kahoot_backend.DTOs.game.GameCreateRequest;
import com.kahoot.kahoot_backend.DTOs.game.GameSessionResponse;
import com.kahoot.kahoot_backend.DTOs.game.HostGameSummaryResponse;
import com.kahoot.kahoot_backend.DTOs.game.PublicGameSummaryResponse;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Player;
import com.kahoot.kahoot_backend.model.Question;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerAnswerRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import com.kahoot.kahoot_backend.repository.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameServiceTest {
    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerAnswerRepository playerAnswerRepository;

    @Mock
    private GameMessageService gameMessageService;

    @Mock
    private PlayerService playerService;

    @InjectMocks
    private GameService gameService;

    private Quiz quiz;
    private static final Long HOST_ID = 1L;

    @BeforeEach
    void setUp() {
        User creator = User.builder()
                .id(HOST_ID)
                .username("host")
                .build();

        quiz = Quiz.builder()
                .id(50L)
                .title("Quiz")
                .creator(creator)
                .build();
    }

    private GameSession session(GameSessionStatus status, int currentQuestionIndex) {
        return GameSession.builder()
                .id(100L)
                .quiz(quiz)
                .pinCode("123456")
                .status(status)
                .currentQuestionIndex(currentQuestionIndex)
                .build();
    }

    private Question question(Long id) {
        return Question.builder().id(id).quiz(quiz).timeLimitSeconds(20).answers(List.of()).build();
    }

    // Create Session

    @Test
    void createSession_validRequest_shouldReturnWaitingSession() {
        when(quizRepository.findById(50L)).thenReturn(Optional.of(quiz));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.existsByPinCode(anyString())).thenReturn(false);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.createSession(HOST_ID, GameCreateRequest.builder().quizId(50L).build());

        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.WAITING);
        assertThat(response.getTotalQuestions()).isEqualTo(5);
    }

    @Test
    void createSession_quizNotFound_shouldThrow() {
        when(quizRepository.findById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.createSession(HOST_ID, GameCreateRequest.builder().quizId(50L).build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSession_userNotOwner_shouldThrowSecurityException() {
        when(quizRepository.findById(50L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> gameService.createSession(999L, GameCreateRequest.builder().quizId(50L).build()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void createSession_pinCollisionFirstTries_shouldRetryUntilUniquePin() {
        when(quizRepository.findById(50L)).thenReturn(Optional.of(quiz));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.existsByPinCode(anyString())).thenReturn(true, true, false);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.createSession(HOST_ID, GameCreateRequest.builder().quizId(50L).build());

        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.WAITING);
        verify(gameSessionRepository, times(3)).existsByPinCode(anyString());
    }

    @Test
    void createSession_pinAlwaysTaken_shouldThrowAfterMaxRetries() {
        when(quizRepository.findById(50L)).thenReturn(Optional.of(quiz));
        when(gameSessionRepository.existsByPinCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> gameService.createSession(HOST_ID, GameCreateRequest.builder().quizId(50L).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    // Start Game
    @Test
    void startGame_fromWaitingAsHost_shouldTransitionToInProgress() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode(anyString())).thenReturn(Optional.of(waitingSession));
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);

        GameSessionResponse response = gameService.startGame("123456", HOST_ID);

        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.IN_PROGRESS);
        verify(gameMessageService).broadcastGameStarted(eq("123456"), any(GameSession.class));
    }

    @Test
    void startGame_notFromWaiting_shouldThrow() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCode(anyString())).thenReturn(Optional.of(inProgressSession));

        assertThatThrownBy(() -> gameService.startGame("123456", HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startGame_notHost_shouldThrowSecurityException() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> gameService.startGame("123456", 999L))
                .isInstanceOf(SecurityException.class);
    }

    // Next Question
    @Test
    void nextQuestion_notFinalized_shouldApplyScoresWithoutRoundResultsAndAdvance() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question(10L), question(11L)));
        when(playerService.applyRoundScores(eq(inProgressSession), any(Question.class))).thenReturn(List.of());
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.nextQuestion("123456", HOST_ID);

        assertThat(response.getCurrentQuestionIndex()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.IN_PROGRESS);
        verify(gameMessageService).sendAnswerResults(anyList());
        verify(gameMessageService).broadcastLeaderboard("123456", inProgressSession);
        verify(gameMessageService, never()).broadcastRoundResults(anyString(), anyList());
        verify(gameMessageService).broadcastCurrentQuestion(eq("123456"), any(GameSession.class));
        verify(gameMessageService, never()).broadcastFinalResults(anyString(), any(GameSession.class));
        // Reset for the new question
        assertThat(inProgressSession.isQuestionFinalized()).isFalse();
    }

    @Test
    void nextQuestion_alreadyFinalized_shouldNotApplyScoresAgain() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        inProgressSession.setQuestionFinalized(true);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question(10L), question(11L)));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameService.nextQuestion("123456", HOST_ID);

        verify(playerService, never()).applyRoundScores(any(), any());
        verify(gameMessageService, never()).sendAnswerResults(anyList());
    }

    @Test
    void nextQuestion_lastQuestion_shouldAutoCompleteGame() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 4);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.nextQuestion("123456", HOST_ID);

        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
        verify(gameMessageService).broadcastFinalResults(eq("123456"), any(GameSession.class));
        verify(gameMessageService, never()).broadcastCurrentQuestion(anyString(), any(GameSession.class));
    }

    @Test
    void nextQuestion_notInProgress_shouldThrow() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> gameService.nextQuestion("123456", HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    // End Game
    @Test
    void endGame_fromInProgress_shouldTransitionToCompleted() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 4);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.endGame("123456", HOST_ID);

        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
    }

    @Test
    void endGame_midQuestion_shouldApplyScoresOfCurrentQuestionBeforeFinalResults() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question(10L)));
        when(playerService.applyRoundScores(eq(inProgressSession), any(Question.class))).thenReturn(List.of());
        when(questionRepository.countByQuizId(50L)).thenReturn(1);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameService.endGame("123456", HOST_ID);

        InOrder inOrder = inOrder(playerService, gameMessageService);
        inOrder.verify(playerService).applyRoundScores(eq(inProgressSession), any(Question.class));
        inOrder.verify(gameMessageService).broadcastFinalResults(eq("123456"), any(GameSession.class));
    }

    @Test
    void endGame_notFromInProgress_shouldThrow() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> gameService.endGame("123456", HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finalizeUnanswered_inProgressAsHost_shouldApplyScoresAndSendResultsWithoutAdvancing() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        List<AnswerResultDTO> results = List.of(AnswerResultDTO.builder().playerId(5L).build());
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question(10L)));
        when(playerService.applyRoundScores(eq(inProgressSession), any(Question.class))).thenReturn(results);

        gameService.finalizeUnanswered("123456", HOST_ID);

        assertThat(inProgressSession.isQuestionFinalized()).isTrue();
        assertThat(inProgressSession.getCurrentQuestionIndex()).isZero();
        verify(gameSessionRepository).save(inProgressSession);
        verify(gameMessageService).sendAnswerResults(results);
        verify(gameMessageService).broadcastLeaderboard("123456", inProgressSession);
        verify(gameMessageService).broadcastRoundResults(eq("123456"), anyList());
    }

    @Test
    void finalizeUnanswered_alreadyFinalized_shouldOnlyResendRoundResults() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        inProgressSession.setQuestionFinalized(true);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question(10L)));

        gameService.finalizeUnanswered("123456", HOST_ID);

        verify(playerService, never()).applyRoundScores(any(), any());
        verify(gameMessageService, never()).sendAnswerResults(anyList());
        verify(gameMessageService).broadcastRoundResults(eq("123456"), anyList());
    }

    @Test
    void finalizeUnanswered_notHost_shouldThrowSecurityException() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));

        assertThatThrownBy(() -> gameService.finalizeUnanswered("123456", 999L))
                .isInstanceOf(SecurityException.class);

        verify(playerService, never()).applyRoundScores(any(), any());
    }

    @Test
    void finalizeUnanswered_notInProgress_shouldThrow() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> gameService.finalizeUnanswered("123456", HOST_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(playerService, never()).applyRoundScores(any(), any());
    }

    @Test
    void autoFinalizeIfExpired_deadlinePassed_shouldFinalizeWithRoundResults() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);

        inProgressSession.setQuestionStartedAt(LocalDateTime.now().minusSeconds(25));
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question(10L)));
        when(playerService.applyRoundScores(eq(inProgressSession), any(Question.class))).thenReturn(List.of());

        gameService.autoFinalizeIfExpired("123456");

        assertThat(inProgressSession.isQuestionFinalized()).isTrue();
        verify(gameMessageService).sendAnswerResults(anyList());
        verify(gameMessageService).broadcastRoundResults(eq("123456"), anyList());
    }

    @Test
    void autoFinalizeIfExpired_timeNotUp_shouldDoNothing() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        inProgressSession.setQuestionStartedAt(LocalDateTime.now().minusSeconds(10));
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.findByQuizIdOrderByOrderIndex(50L)).thenReturn(List.of(question(10L)));

        gameService.autoFinalizeIfExpired("123456");

        assertThat(inProgressSession.isQuestionFinalized()).isFalse();
        verify(playerService, never()).applyRoundScores(any(), any());
        verifyNoInteractions(gameMessageService);
    }

    @Test
    void autoFinalizeIfExpired_hostAlreadyFinalized_shouldDoNothing() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        inProgressSession.setQuestionStartedAt(LocalDateTime.now().minusSeconds(25));
        inProgressSession.setQuestionFinalized(true);
        when(gameSessionRepository.findByPinCodeForUpdate("123456")).thenReturn(Optional.of(inProgressSession));

        gameService.autoFinalizeIfExpired("123456");

        verify(playerService, never()).applyRoundScores(any(), any());
        verifyNoInteractions(gameMessageService);
    }

    // Get Session By Pin
    @Test
    void getSessionByPin_notFound_shouldThrow() {
        when(gameSessionRepository.findByPinCode("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getSessionByPin("000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Create Session - visibility
    @Test
    void createSession_noVisibilityInRequest_shouldDefaultToPrivate() {
        when(quizRepository.findById(50L)).thenReturn(Optional.of(quiz));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.existsByPinCode(anyString())).thenReturn(false);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.createSession(HOST_ID, GameCreateRequest.builder().quizId(50L).build());

        assertThat(response.getVisibility()).isEqualTo(GameSessionVisibility.PRIVATE);
    }

    @Test
    void createSession_visibilityPublicInRequest_shouldCreatePublicSession() {
        when(quizRepository.findById(50L)).thenReturn(Optional.of(quiz));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.existsByPinCode(anyString())).thenReturn(false);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.createSession(HOST_ID,
                GameCreateRequest.builder().quizId(50L).visibility(GameSessionVisibility.PUBLIC).build());

        assertThat(response.getVisibility()).isEqualTo(GameSessionVisibility.PUBLIC);
    }

    // List Public Sessions
    @Test
    void listPublicSessions_shouldMapSessionsToSummaryResponses() {
        GameSession session = GameSession.builder()
                .id(200L)
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .visibility(GameSessionVisibility.PUBLIC)
                .currentQuestionIndex(0)
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        Page<GameSession> page = new PageImpl<>(List.of(session));

        when(gameSessionRepository.searchPublicSessions(GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, null, pageable))
                .thenReturn(page);
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(playerRepository.countByGameSessionId(200L)).thenReturn(3);

        Page<PublicGameSummaryResponse> result = gameService.listPublicSessions(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        PublicGameSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.getPinCode()).isEqualTo("123456");
        assertThat(summary.getQuizTitle()).isEqualTo("Quiz");
        assertThat(summary.getHostName()).isEqualTo("host");
        assertThat(summary.getTotalQuestions()).isEqualTo(5);
        assertThat(summary.getPlayerCount()).isEqualTo(3);
    }

    @Test
    void listHostSessions_winnerOnlyForCompletedWithPoints() {
        GameSession completed = session(GameSessionStatus.COMPLETED, 4);
        GameSession completedNoPoints = GameSession.builder().id(101L).quiz(quiz).pinCode("222222")
                .status(GameSessionStatus.COMPLETED).currentQuestionIndex(4).build();
        GameSession waiting = GameSession.builder().id(102L).quiz(quiz).pinCode("333333")
                .status(GameSessionStatus.WAITING).currentQuestionIndex(0).build();

        when(gameSessionRepository.findByQuizCreatorIdOrderByCreatedAtDesc(HOST_ID))
                .thenReturn(List.of(completed, completedNoPoints, waiting));
        when(playerRepository.findFirstByGameSessionIdOrderByScoreDescIdAsc(100L))
                .thenReturn(Optional.of(Player.builder().nickname("Mika").score(3450).build()));
        when(playerRepository.findFirstByGameSessionIdOrderByScoreDescIdAsc(101L))
                .thenReturn(Optional.of(Player.builder().nickname("Pera").score(0).build()));

        List<HostGameSummaryResponse> result = gameService.listHostSessions(HOST_ID);

        assertThat(result).extracting(HostGameSummaryResponse::getPinCode).containsExactly("123456", "222222", "333333");
        assertThat(result.get(0).getWinnerNickname()).isEqualTo("Mika");
        assertThat(result.get(0).getWinnerScore()).isEqualTo(3450);
        assertThat(result.get(1).getWinnerNickname()).isNull();
        assertThat(result.get(1).getWinnerScore()).isNull();
        assertThat(result.get(2).getWinnerNickname()).isNull();
        verify(playerRepository, never()).findFirstByGameSessionIdOrderByScoreDescIdAsc(102L);
    }

    @Test
    void listPublicSessions_blankQuery_shouldPassNullToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(gameSessionRepository.searchPublicSessions(GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, null, pageable))
                .thenReturn(Page.empty());

        gameService.listPublicSessions("   ", pageable);

        verify(gameSessionRepository).searchPublicSessions(GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, null, pageable);
    }
}
