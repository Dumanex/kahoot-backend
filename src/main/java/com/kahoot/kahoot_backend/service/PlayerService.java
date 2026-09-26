package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerDTO;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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

        // Same error for unknown player, other game and wrong token so nothing is revealed
        Player player = playerRepository.findById(playerId)
                .filter(p -> p.getGameSession().getId().equals(session.getId()))
                .filter(p -> tokensMatch(p.getRejoinToken(), rejoinToken))
                .orElseThrow(() -> new SecurityException("Invalid rejoin token"));

        PlayerResponse response = mapToPlayerResponse(player, false);

        if (session.getStatus() == GameSessionStatus.IN_PROGRESS) {
            Question currentQuestion = getCurrentQuestion(session);

            if (currentQuestion != null) {
                playerAnswerRepository.findByPlayerIdAndQuestionId(player.getId(), currentQuestion.getId())
                        .ifPresent(playerAnswer -> {
                            response.setAnsweredCurrentQuestion(true);
                            response.setCurrentAnswerResult(GameDtoMapper.toAnswerResult(player, playerAnswer, currentQuestion));
                        });
            }
        }

        return response;
    }

    // ======================== SUBMIT ANSWER (WebSocket) ========================

    @Transactional
    public AnswerResultDTO submitAnswer(String pinCode, Long playerId, AnswerSubmitRequest request) {
        GameSession session = getSessionOrThrow(pinCode);
        validateStatus(session, GameSessionStatus.IN_PROGRESS, "Game is not IN_PROGRESS");

        Player player = getPlayerOrThrow(playerId);
        validatePlayerInSession(player, session.getId());

        // playerId alone is guessable; the token proves the sender is this player
        if (!tokensMatch(player.getRejoinToken(), request.getRejoinToken())) {
            throw new SecurityException("Invalid rejoin token");
        }

        Question currentQuestion = getCurrentQuestion(session);
        if (currentQuestion == null || !currentQuestion.getId().equals(request.getQuestionId())) {
            throw new IllegalArgumentException("Invalid question");
        }

        // After finalize the round results are already out, a late answer would change them
        if (session.isQuestionFinalized()) {
            throw new IllegalStateException("Question is already finalized");
        }

        // Check if already answered
        if (playerAnswerRepository.existsByPlayerIdAndQuestionId(playerId, request.getQuestionId())) {
            throw new IllegalArgumentException("Already answered this question");
        }

        // Find correct answer
        Answer correctAnswer = currentQuestion.getAnswers()
                .stream()
                .filter(Answer::getIsCorrect)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No correct answer defined for question"));

        boolean isCorrect = correctAnswer.getId().equals(request.getAnswerId());

        // Calculate points using ScoringService
        int pointsEarned = scoringService.calculatePoints(
                isCorrect,
                request.getResponseTimeMs(),
                currentQuestion.getTimeLimitSeconds(),
                player.getStreak()
        );

        int newStreak = scoringService.calculateStreak(isCorrect, player.getStreak());

        // Update player
        player.setStreak(newStreak);
        player.setScore(player.getScore() + pointsEarned);
        playerRepository.save(player);

        Answer selectedAnswer = currentQuestion.getAnswers()
                .stream()
                .filter(answer -> answer.getId().equals(request.getAnswerId()))
                .findFirst()
                .orElse(null);

        PlayerAnswer playerAnswer = PlayerAnswer.builder()
                .player(player)
                .question(currentQuestion)
                .answer(selectedAnswer)
                .responseTimeMs(request.getResponseTimeMs())
                .isCorrect(isCorrect)
                .pointsEarned(pointsEarned)
                .answeredAt(LocalDateTime.now())
                .build();

        playerAnswerRepository.save(playerAnswer);

        return AnswerResultDTO.builder()
                .playerId(player.getId())
                .nickname(player.getNickname())
                .isCorrect(isCorrect)
                .chosenAnswerId(request.getAnswerId())
                .pointsEarned(pointsEarned)
                .totalScore(player.getScore())
                .streak(newStreak)
                .correctAnswer(mapToAnswerDTO(correctAnswer))
                .build();
    }

    // ======================== FINALIZE UNANSWERED PLAYERS ========================

    @Transactional
    public List<AnswerResultDTO> finalizeUnansweredPlayers(GameSession session, Question currentQuestion) {
        Answer correctAnswer = currentQuestion.getAnswers()
                .stream()
                .filter(Answer::getIsCorrect)
                .findFirst()
                .orElse(null);

        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        return players.stream()
                .filter(player -> !playerAnswerRepository.existsByPlayerIdAndQuestionId(player.getId(), currentQuestion.getId()))
                .map(player -> {
                    player.setStreak(0);
                    playerRepository.save(player);

                    return AnswerResultDTO.builder()
                            .playerId(player.getId())
                            .nickname(player.getNickname())
                            .isCorrect(false)
                            .chosenAnswerId(null)
                            .pointsEarned(0)
                            .totalScore(player.getScore())
                            .streak(0)
                            .correctAnswer(correctAnswer != null ? mapToAnswerDTO(correctAnswer) : null)
                            .build();
                })
                .collect(Collectors.toList());
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

    private AnswerDTO mapToAnswerDTO(Answer answer) {
        return GameDtoMapper.toAnswerDTO(answer);
    }
}
