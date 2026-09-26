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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlayerService {
    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final PlayerAnswerRepository playerAnswerRepository;
    private final QuestionRepository questionRepository;
    private final ScoringService scoringService;

    // ======================== JOIN GAME (REST + WebSocket) ========================

    @Transactional
    public PlayerResponse joinGame(String pinCode, String nickname) {
        GameSession session = getSessionOrThrow(pinCode);
        validateStatus(session, GameSessionStatus.WAITING, "Can only join while game is WAITING");

        if (playerRepository.existsByGameSessionIdAndNickname(session.getId(), nickname)) {
            throw new IllegalArgumentException("Nickname '" + nickname + "' is already taken in this game");
        }

        Player player = Player.builder()
                .gameSession(session)
                .nickname(nickname)
                .score(0)
                .streak(0)
                .rejoinToken(UUID.randomUUID().toString())
                .joinedAt(LocalDateTime.now())
                .build();

        player = playerRepository.save(player);

        return mapToPlayerResponse(player, false);
    }

    // ======================== REJOIN GAME (REST, after page refresh) ========================

    @Transactional
    public PlayerResponse rejoinGame(String pinCode, Long playerId, String rejoinToken) {
        GameSession session = getSessionOrThrow(pinCode);

        if (session.getStatus() == GameSessionStatus.COMPLETED) {
            throw new IllegalStateException("Can only rejoin while game is WAITING or IN_PROGRESS. Current status: " + session.getStatus());
        }

        Player player = playerRepository.findById(playerId)
                .filter(p -> p.getGameSession().getId().equals(session.getId()))
                .filter(p -> tokensMatch(p.getRejoinToken(), rejoinToken))
                .orElseThrow(() -> new SecurityException("Invalid rejoin token"));

        PlayerResponse response = mapToPlayerResponse(player, false);

        if (session.getStatus() == GameSessionStatus.IN_PROGRESS) {
            Question currentQuestion = getCurrentQuestion(session);

            if (currentQuestion != null) {
                PlayerAnswer playerAnswer = playerAnswerRepository.findByPlayerIdAndQuestionId(player.getId(), currentQuestion.getId()).orElse(null);

                if (playerAnswer != null) {
                    response.setAnsweredCurrentQuestion(true);
                    response.setChosenAnswerId(playerAnswer.getAnswer() != null ? playerAnswer.getAnswer().getId() : null);
                }

                if (session.isQuestionFinalized()) {
                    response.setCurrentAnswerResult(GameDtoMapper.toAnswerResult(player, playerAnswer, currentQuestion));
                }
            }
        }

        return response;
    }

    public boolean isValidPlayer(Long playerId, String rejoinToken) {
        return playerRepository.findById(playerId)
                .map(player -> tokensMatch(player.getRejoinToken(), rejoinToken))
                .orElse(false);
    }

    // ======================== SUBMIT ANSWER (WebSocket) ========================

    @Transactional
    public AnswerAcceptedDTO submitAnswer(String pinCode, Long playerId, AnswerSubmitRequest request) {
        GameSession session = gameSessionRepository.findByPinCodeForUpdate(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("Game session not found with PIN: " + pinCode));
        validateStatus(session, GameSessionStatus.IN_PROGRESS, "Game is not IN_PROGRESS");

        Player player = getPlayerOrThrow(playerId);
        validatePlayerInSession(player, session.getId());

        if (!tokensMatch(player.getRejoinToken(), request.getRejoinToken())) {
            throw new SecurityException("Invalid rejoin token");
        }

        Question currentQuestion = getCurrentQuestion(session);
        if (currentQuestion == null || !currentQuestion.getId().equals(request.getQuestionId())) {
            throw new IllegalArgumentException("Invalid question");
        }

        if (session.isQuestionFinalized()) {
            throw new IllegalStateException("Question is already finalized");
        }

        int responseTimeMs = measureResponseTime(session, currentQuestion);

        if (playerAnswerRepository.existsByPlayerIdAndQuestionId(playerId, request.getQuestionId())) {
            throw new IllegalArgumentException("Already answered this question");
        }

        Answer correctAnswer = currentQuestion.getAnswers()
                .stream()
                .filter(Answer::getIsCorrect)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No correct answer defined for question"));

        boolean isCorrect = correctAnswer.getId().equals(request.getAnswerId());

        int pointsEarned = scoringService.calculatePoints(
                isCorrect,
                responseTimeMs,
                currentQuestion.getTimeLimitSeconds(),
                player.getStreak()
        );

        Answer selectedAnswer = currentQuestion.getAnswers()
                .stream()
                .filter(answer -> answer.getId().equals(request.getAnswerId()))
                .findFirst()
                .orElse(null);

        PlayerAnswer playerAnswer = PlayerAnswer.builder()
                .player(player)
                .question(currentQuestion)
                .answer(selectedAnswer)
                .responseTimeMs(responseTimeMs)
                .isCorrect(isCorrect)
                .pointsEarned(pointsEarned)
                .answeredAt(LocalDateTime.now())
                .build();

        playerAnswerRepository.save(playerAnswer);

        return AnswerAcceptedDTO.builder()
                .questionId(currentQuestion.getId())
                .chosenAnswerId(request.getAnswerId())
                .build();
    }

    // ======================== APPLY ROUND SCORES (finalize) ========================

    @Transactional
    public List<AnswerResultDTO> applyRoundScores(GameSession session, Question question) {
        Map<Long, PlayerAnswer> answersByPlayer = playerAnswerRepository
                .findByQuestionIdAndPlayerGameSessionId(question.getId(), session.getId())
                .stream()
                .collect(Collectors.toMap(answer -> answer.getPlayer().getId(), Function.identity()));

        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        return players.stream()
                .map(player -> {
                    PlayerAnswer playerAnswer = answersByPlayer.get(player.getId());
                    boolean isCorrect = playerAnswer != null && Boolean.TRUE.equals(playerAnswer.getIsCorrect());
                    int pointsEarned = playerAnswer != null && playerAnswer.getPointsEarned() != null ? playerAnswer.getPointsEarned() : 0;

                    player.setScore(player.getScore() + pointsEarned);
                    player.setStreak(scoringService.calculateStreak(isCorrect, player.getStreak()));
                    playerRepository.save(player);

                    return GameDtoMapper.toAnswerResult(player, playerAnswer, question);
                })
                .toList();
    }

    // ======================== QUERY METHODS ========================

    public List<Player> getPlayers(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);

        return playerRepository.findByGameSessionId(session.getId());
    }

    public Player getPlayerById(Long playerId) {
        return getPlayerOrThrow(playerId);
    }

    // ======================== HELPERS ========================

    private GameSession getSessionOrThrow(String pinCode) {
        return gameSessionRepository.findByPinCode(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("Game session not found with PIN: " + pinCode));
    }

    private Player getPlayerOrThrow(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found: " + playerId));
    }

    private void validateStatus(GameSession session, GameSessionStatus expectedStatus, String errorMessage) {
        if (session.getStatus() != expectedStatus) {
            throw new IllegalStateException(errorMessage + ". Current status: " + session.getStatus());
        }
    }

    private void validatePlayerInSession(Player player, Long sessionId) {
        if (!player.getGameSession().getId().equals(sessionId)) {
            throw new IllegalArgumentException("Player does not belong to this game session");
        }
    }

    private Question getCurrentQuestion(GameSession session) {
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(session.getQuiz().getId());
        int index = session.getCurrentQuestionIndex();
        if (index >= 0 && index < questions.size()) {
            return questions.get(index);
        }
        return null;
    }

    private int measureResponseTime(GameSession session, Question question) {
        LocalDateTime now = LocalDateTime.now();
        long elapsedMs = Duration.between(QuestionTiming.answeringOpensAt(session), now).toMillis();

        if (elapsedMs < -QuestionTiming.EARLY_TOLERANCE_MS) {
            throw new IllegalStateException("Answering has not started yet");
        }

        if (now.isAfter(QuestionTiming.answerDeadline(session, question))) {
            throw new IllegalStateException("Time is up for this question");
        }

        return (int) Math.min(Math.max(elapsedMs, 0), QuestionTiming.timeLimitMs(question));
    }

    private boolean tokensMatch(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }

        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    private PlayerResponse mapToPlayerResponse(Player player, boolean answeredCurrentQuestion) {
        return PlayerResponse.builder()
                .id(player.getId())
                .nickname(player.getNickname())
                .score(player.getScore())
                .streak(player.getStreak())
                .joinedAt(player.getJoinedAt())
                .rejoinToken(player.getRejoinToken())
                .answeredCurrentQuestion(answeredCurrentQuestion)
                .build();
    }
}
