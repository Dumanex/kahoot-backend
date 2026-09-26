package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.GameCreateRequest;
import com.kahoot.kahoot_backend.DTOs.game.GameSessionResponse;
import com.kahoot.kahoot_backend.DTOs.game.PublicGameSummaryResponse;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import com.kahoot.kahoot_backend.repository.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private GameMessageService gameMessageService;

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
    void nextQuestion_notLastQuestion_shouldAdvanceIndex() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.nextQuestion("123456", HOST_ID);

        assertThat(response.getCurrentQuestionIndex()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.IN_PROGRESS);
        verify(gameMessageService).finalizeUnansweredPlayers(eq("123456"), any(GameSession.class));
        verify(gameMessageService).broadcastCurrentQuestion(eq("123456"), any(GameSession.class));
        verify(gameMessageService, never()).broadcastFinalResults(anyString(), any(GameSession.class));
    }

    @Test
    void nextQuestion_lastQuestion_shouldAutoCompleteGame() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 4);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.nextQuestion("123456", HOST_ID);

        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
        verify(gameMessageService).finalizeUnansweredPlayers(eq("123456"), any(GameSession.class));
        verify(gameMessageService).broadcastFinalResults(eq("123456"), any(GameSession.class));
        verify(gameMessageService, never()).broadcastCurrentQuestion(anyString(), any(GameSession.class));
    }

    @Test
    void nextQuestion_notInProgress_shouldThrow() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> gameService.nextQuestion("123456", HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    // End Game
    @Test
    void endGame_fromInProgress_shouldTransitionToCompleted() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 4);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(inProgressSession));
        when(questionRepository.countByQuizId(50L)).thenReturn(5);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSessionResponse response = gameService.endGame("123456", HOST_ID);

        assertThat(response.getStatus()).isEqualTo(GameSessionStatus.COMPLETED);
    }

    @Test
    void endGame_notFromInProgress_shouldThrow() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> gameService.endGame("123456", HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    // Finalize Unanswered
    @Test
    void finalizeUnanswered_inProgressAsHost_shouldFinalizeWithoutAdvancing() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(inProgressSession));

        gameService.finalizeUnanswered("123456", HOST_ID);

        verify(gameMessageService).finalizeUnansweredPlayers("123456", inProgressSession);
        verify(gameSessionRepository, never()).save(any(GameSession.class));
    }

    @Test
    void finalizeUnanswered_notHost_shouldThrowSecurityException() {
        GameSession inProgressSession = session(GameSessionStatus.IN_PROGRESS, 0);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(inProgressSession));

        assertThatThrownBy(() -> gameService.finalizeUnanswered("123456", 999L))
                .isInstanceOf(SecurityException.class);

        verify(gameMessageService, never()).finalizeUnansweredPlayers(anyString(), any(GameSession.class));
    }

    @Test
    void finalizeUnanswered_notInProgress_shouldThrow() {
        GameSession waitingSession = session(GameSessionStatus.WAITING, 0);
        when(gameSessionRepository.findByPinCode("123456")).thenReturn(Optional.of(waitingSession));

        assertThatThrownBy(() -> gameService.finalizeUnanswered("123456", HOST_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(gameMessageService, never()).finalizeUnansweredPlayers(anyString(), any(GameSession.class));
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
    void listPublicSessions_blankQuery_shouldPassNullToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(gameSessionRepository.searchPublicSessions(GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, null, pageable))
                .thenReturn(Page.empty());

        gameService.listPublicSessions("   ", pageable);

        verify(gameSessionRepository).searchPublicSessions(GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, null, pageable);
    }
}
