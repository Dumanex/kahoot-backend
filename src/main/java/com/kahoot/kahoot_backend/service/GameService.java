package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerResultDTO;
import com.kahoot.kahoot_backend.DTOs.game.GameCreateRequest;
import com.kahoot.kahoot_backend.DTOs.game.GameSessionResponse;
import com.kahoot.kahoot_backend.DTOs.game.GameStateResponse;
import com.kahoot.kahoot_backend.DTOs.game.PublicGameSummaryResponse;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Player;
import com.kahoot.kahoot_backend.model.PlayerAnswer;
import com.kahoot.kahoot_backend.model.Question;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerAnswerRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import com.kahoot.kahoot_backend.repository.QuizRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameSessionRepository gameSessionRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAnswerRepository playerAnswerRepository;
    private final GameMessageService gameMessageService;

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

        LocalDateTime now = LocalDateTime.now();
        session.setStatus(GameSessionStatus.IN_PROGRESS);
        session.setStartedAt(now);
        session.setQuestionStartedAt(now);
        session.setQuestionFinalized(false);
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
        session.setQuestionStartedAt(LocalDateTime.now());
        session.setQuestionFinalized(false);
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

    @Transactional
    public void finalizeUnanswered(String pinCode, Long userId) {
        GameSession session = getSessionOrThrow(pinCode);

        validateHost(session, userId);
        validateStatus(session, GameSessionStatus.IN_PROGRESS, "Game must be IN_PROGRESS to finalize unanswered players");

        gameMessageService.finalizeUnansweredPlayers(pinCode, session);

        // Lets a host that refreshes on the round-results screen restore it via /state
        session.setQuestionFinalized(true);
        gameSessionRepository.save(session);

        // Complete list for hosts that missed individual answer-results (e.g. after a refresh)
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(session.getQuiz().getId());
        int index = session.getCurrentQuestionIndex();
        if (index >= 0 && index < questions.size()) {
            List<Player> players = playerRepository.findByGameSessionId(session.getId());
            gameMessageService.broadcastRoundResults(pinCode, buildRoundResults(session, questions.get(index), players));
        }
    }

    // ========================= PUBLIC OPERATIONS (without JWT) =========================

    public GameSessionResponse getSessionByPin(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);
        int totalQuestions = questionRepository.countByQuizId(session.getQuiz().getId());
        return mapToSessionResponse(session, totalQuestions);
    }

    // Snapshot for a client that (re)connects mid-game; same data as the public /topic broadcasts
    @Transactional
    public GameStateResponse getGameState(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(session.getQuiz().getId());
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        Question currentQuestion = null;
        int index = session.getCurrentQuestionIndex();
        if (session.getStatus() == GameSessionStatus.IN_PROGRESS && index >= 0 && index < questions.size()) {
            currentQuestion = questions.get(index);
        }

        Long questionStartedAt = null;
        int answeredCount = 0;
        boolean questionFinalized = false;
        List<AnswerResultDTO> roundResults = null;
        if (currentQuestion != null) {
            if (session.getQuestionStartedAt() != null) {
                questionStartedAt = GameDtoMapper.toEpochMillis(session.getQuestionStartedAt());
            }
            answeredCount = playerAnswerRepository.countByQuestionIdAndPlayerGameSessionId(currentQuestion.getId(), session.getId());
            questionFinalized = session.isQuestionFinalized();

            // Only after finalize, so correct answers are not revealed while the question is open
            if (questionFinalized) {
                roundResults = buildRoundResults(session, currentQuestion, players);
            }
        }

        return GameStateResponse.builder()
                .pinCode(session.getPinCode())
                .status(session.getStatus())
                .quizTitle(session.getQuiz().getTitle())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .totalQuestions(questions.size())
                .currentQuestion(currentQuestion != null ? GameDtoMapper.toQuestionDTO(currentQuestion, session.getQuestionStartedAt()) : null)
                .questionStartedAt(questionStartedAt)
                .serverTime(System.currentTimeMillis())
                .answeredCount(answeredCount)
                .questionFinalized(questionFinalized)
                .roundResults(roundResults)
                .players(GameDtoMapper.toPlayerInfos(players))
                .leaderboard(GameDtoMapper.toLeaderboard(players))
                .build();
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

    private List<AnswerResultDTO> buildRoundResults(GameSession session, Question question, List<Player> players) {
        Map<Long, PlayerAnswer> answersByPlayer = playerAnswerRepository
                .findByQuestionIdAndPlayerGameSessionId(question.getId(), session.getId())
                .stream()
                .collect(Collectors.toMap(answer -> answer.getPlayer().getId(), Function.identity()));

        return players.stream()
                .map(player -> GameDtoMapper.toAnswerResult(player, answersByPlayer.get(player.getId()), question))
                .toList();
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
