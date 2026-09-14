package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.GameCreateRequest;
import com.kahoot.kahoot_backend.DTOs.game.GameSessionResponse;
import com.kahoot.kahoot_backend.DTOs.game.PublicGameSummaryResponse;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import com.kahoot.kahoot_backend.repository.QuizRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameSessionRepository gameSessionRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final GameMessageService gameMessageService;

    private static final int PIN_LENGTH = 6;
    private static final int MAX_PIN_RETRIES = 10;
    private final Random random = new Random();

    // ========================= HOST OPERATIONS =========================

    @Transactional
    public GameSessionResponse createSession(Long userId, GameCreateRequest request) {
        Quiz quiz = quizRepository.findById(request.getQuizId())
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + request.getQuizId()));

        if (!quiz.getCreator().getId().equals(userId)) {
            throw new SecurityException("You are not the creator of this quiz");
        }

        String pinCode = generateUniquePin();

        int totalQuestions = questionRepository.countByQuizId(quiz.getId());

        GameSessionVisibility visibility = request.getVisibility() != null ? request.getVisibility() : GameSessionVisibility.PRIVATE;

        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode(pinCode)
                .status(GameSessionStatus.WAITING)
                .visibility(visibility)
                .currentQuestionIndex(0)
                .createdAt(LocalDateTime.now())
                .build();

        session = gameSessionRepository.save(session);

        return mapToSessionResponse(session, totalQuestions);
    }

    @Transactional
    public GameSessionResponse startGame(String pinCode ,Long userId) {
        GameSession session = getSessionOrThrow(pinCode);

        validateHost(session, userId);
        validateStatus(session, GameSessionStatus.WAITING, "Game can only be started from WAITING status");

        session.setStatus(GameSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        session.setCurrentQuestionIndex(0);
        session = gameSessionRepository.save(session);

        gameMessageService.broadcastGameStarted(pinCode, session);

        int totalQuestions = questionRepository.countByQuizId(session.getQuiz().getId());
        return mapToSessionResponse(session, totalQuestions);
    }

    @Transactional
    public GameSessionResponse nextQuestion(String pinCode, Long userId) {
        GameSession session = getSessionOrThrow(pinCode);

        validateHost(session, userId);
        validateStatus(session, GameSessionStatus.IN_PROGRESS, "Game must be IN_PROGRESS to advance question");

        gameMessageService.finalizeUnansweredPlayers(pinCode, session);

        int totalQuestion = questionRepository.countByQuizId(session.getQuiz().getId());
        int nextIndex = session.getCurrentQuestionIndex() + 1;

        if (nextIndex >= totalQuestion) {
            return endGame(pinCode, userId);
        }

        session.setCurrentQuestionIndex(nextIndex);
        session = gameSessionRepository.save(session);

        gameMessageService.broadcastCurrentQuestion(pinCode, session);

        return mapToSessionResponse(session, totalQuestion);
    }

    @Transactional
    public GameSessionResponse endGame(String pinCode, Long userId) {
        GameSession session = getSessionOrThrow(pinCode);

        validateHost(session, userId);
        validateStatus(session, GameSessionStatus.IN_PROGRESS, "Game can only be ended from IN_PROGRESS status");

        session.setStatus(GameSessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        session = gameSessionRepository.save(session);

        gameMessageService.broadcastFinalResults(pinCode, session);

        int totalQuestion = questionRepository.countByQuizId(session.getQuiz().getId());
        return mapToSessionResponse(session, totalQuestion);
    }

    // ========================= PUBLIC OPERATIONS (without JWT) =========================

    public GameSessionResponse getSessionByPin(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);
        int totalQuestions = questionRepository.countByQuizId(session.getQuiz().getId());
        return mapToSessionResponse(session, totalQuestions);
    }

    public Page<PublicGameSummaryResponse> listPublicSessions(String q, Pageable pageable) {
        String search = (q == null || q.isBlank()) ? null : q.trim();

        Page<GameSession> sessions = gameSessionRepository.searchPublicSessions(GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, search, pageable);

        return sessions.map(this::mapToPublicSummary);
    }

    // ========================= HELPERS =========================

    private String generateUniquePin() {
        for (int i = 0; i < MAX_PIN_RETRIES; i++) {
            int pin = 100000 + random.nextInt(900000);
            String pinCode = String.valueOf(pin);
            if (!gameSessionRepository.existsByPinCode(pinCode)) {
                return pinCode;
            }
        }

        throw new IllegalStateException("Failed to generate unique PIN after " + MAX_PIN_RETRIES + " attempts");
    }

    private GameSessionResponse mapToSessionResponse(GameSession session, int totalQuestions) {
        return GameSessionResponse.builder()
                .id(session.getId())
                .pinCode(session.getPinCode())
                .status(session.getStatus())
                .visibility(session.getVisibility())
                .quizId(session.getQuiz().getId())
                .quizTitle(session.getQuiz().getTitle())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .totalQuestions(totalQuestions)
                .createdAt(session.getCreatedAt())
                .build();
    }

    private PublicGameSummaryResponse mapToPublicSummary(GameSession session) {
        int totalQuestions = questionRepository.countByQuizId(session.getQuiz().getId());
        int playerCount = playerRepository.countByGameSessionId(session.getId());

        return PublicGameSummaryResponse.builder()
                .pinCode(session.getPinCode())
                .quizTitle(session.getQuiz().getTitle())
                .hostName(session.getQuiz().getCreator().getUsername())
                .totalQuestions(totalQuestions)
                .playerCount(playerCount)
                .createdAt(session.getCreatedAt())
                .build();
    }

    private GameSession getSessionOrThrow(String pinCode) {
        return gameSessionRepository.findByPinCode(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("Game session not found with PIN: " + pinCode));
    }

    private void validateHost(GameSession session, Long userId) {
        if (!session.getQuiz().getCreator().getId().equals(userId)) {
            throw new SecurityException("You are not the creator of this quiz");
        }
    }

    private void validateStatus(GameSession session, GameSessionStatus expectedStatus, String errorMessage) {
        if (session.getStatus() != expectedStatus) {
            throw new IllegalStateException(errorMessage + ". Current status: " +session.getStatus());
        }
    }
}
