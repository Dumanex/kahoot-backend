package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.*;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.*;
import com.kahoot.kahoot_backend.repository.GameSessionRepository;
import com.kahoot.kahoot_backend.repository.PlayerAnswerRepository;
import com.kahoot.kahoot_backend.repository.PlayerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameMessageService {
    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final QuestionRepository questionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlayerService playerService;

    private static final String TOPIC_PREFIX = "/topic/game/";

    // ================== PLAYER JOIN (WebSocket) ==================
    @Transactional
    public void handlePlayerJoin(String pinCode, String nickname) {
        try {
            playerService.joinGame(pinCode, nickname);
        } catch (IllegalArgumentException | IllegalStateException | ResourceNotFoundException e) {
            sendError(pinCode, e.getMessage());
            return;
        }

        GameSession session = getSessionOrThrow(pinCode);

        // Broadcast players list to everyone
        broadcastPlayerList(pinCode, session);
    }

    // ================== PLAYER ANSWER ==================

    @Transactional
    public void handlePlayerAnswer(String pinCode, AnswerSubmitRequest request) {
        try {
            AnswerResultDTO result = playerService.submitAnswer(pinCode, request.getPlayerId(), request);

            // Broadcast answer result to player
            messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/answer-result", result);

            // Broadcast leaderboard
            broadcastLeaderboard(pinCode, getSessionOrThrow(pinCode));
        } catch (IllegalArgumentException | IllegalStateException | ResourceNotFoundException e) {
            sendError(pinCode, e.getMessage());
        }
    }

    // ================== HOST: START GAME ==================

    @Transactional
    public void handleGameStart(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);

        if (session.getStatus() != GameSessionStatus.WAITING) {
            if (session.getStatus() == GameSessionStatus.IN_PROGRESS) {
                sendError(pinCode, "Game already started");
                return;
            }

            sendError(pinCode, "Game ended");
            return;
        }

        session.setStatus(GameSessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());
        session.setCurrentQuestionIndex(0);
        gameSessionRepository.save(session);

        broadcastGameStarted(pinCode, session);
    }

    public void broadcastGameStarted(String pinCode, GameSession session) {
        // Broadcast started event
        messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/started", new GameStartedDTO());

        // Broadcast first question
        broadcastCurrentQuestion(pinCode, session);
    }

    // ================== HOST: NEXT QUESTION ==================

    @Transactional
    public void handleNextQuestion(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);

        if (session.getStatus() != GameSessionStatus.IN_PROGRESS) {
            sendError(pinCode, "Game is not IN PROGRESS");
            return;
        }

        int totalQuestions = questionRepository.countByQuizId(session.getQuiz().getId());
        int nextIndex = session.getCurrentQuestionIndex() + 1;

        if (nextIndex >= totalQuestions) {
            handleEndGame(pinCode);
            return;
        }

        session.setCurrentQuestionIndex(nextIndex);
        gameSessionRepository.save(session);

        // Broadcast next question
        broadcastCurrentQuestion(pinCode, session);
    }

    // ================== HOST: END GAME ==================

    @Transactional
    public void handleEndGame(String pinCode) {
        GameSession session = getSessionOrThrow(pinCode);

        if (session.getStatus() != GameSessionStatus.IN_PROGRESS) {
            sendError(pinCode, "Game is not IN_PROGRESS");
            return;
        }

        session.setStatus(GameSessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        gameSessionRepository.save(session);

        // Broadcast final results
        broadcastFinalResults(pinCode, session);
    }

    // ================== BROADCAST METHODS ==================

    private void broadcastPlayerList(String pinCode, GameSession session) {
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        List<PlayerInfoDTO> playerInfos = players
                .stream()
                .map(player -> PlayerInfoDTO.builder()
                        .id(player.getId())
                        .nickname(player.getNickname())
                        .score(player.getScore())
                        .streak(player.getStreak())
                        .build())
                .collect(Collectors.toList());

        messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/players", playerInfos);
    }

    private void broadcastLeaderboard(String pinCode, GameSession session) {
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        List<LeaderboardEntryDTO> leaderboard = players
                .stream()
                .sorted(Comparator.comparing(Player::getScore).reversed())
                .map(player -> LeaderboardEntryDTO.builder()
                        .playerId(player.getId())
                        .nickname(player.getNickname())
                        .score(player.getScore())
                        .streak(player.getStreak())
                        .build())
                .collect(Collectors.toList());

        messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/leaderboard", leaderboard);
    }

    public void broadcastCurrentQuestion(String pinCode, GameSession session) {
        Question question = getCurrentQuestion(session);

        if (question == null) {
            return;
        }

        List<AnswerDTO> answers = question.getAnswers()
                .stream()
                .map(answer -> AnswerDTO.builder()
                        .id(answer.getId())
                        .answerText(answer.getAnswerText())
                        .symbol(answer.getSymbol())
                        .color(answer.getColor())
                        .orderIndex(answer.getOrderIndex())
                        .build())
                .collect(Collectors.toList());

        QuestionDTO questionDTO = QuestionDTO.builder()
                .id(question.getId())
                .questionType(question.getQuestionType())
                .questionText(question.getQuestionText())
                .imageUrl(question.getImageUrl())
                .audioUrl(question.getAudioUrl())
                .timeLimitSeconds(question.getTimeLimitSeconds())
                .orderIndex(question.getOrderIndex())
                .answers(answers)
                .build();

        messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/question", questionDTO);
    }

    public void broadcastFinalResults(String pinCode, GameSession session) {
        List<Player> players = playerRepository.findByGameSessionId(session.getId());

        List<LeaderboardEntryDTO> finalLeaderboard = players
                .stream()
                .sorted(Comparator.comparing(Player::getScore).reversed())
                .map(player -> LeaderboardEntryDTO.builder()
                        .playerId(player.getId())
                        .nickname(player.getNickname())
                        .score(player.getScore())
                        .streak(player.getStreak())
                        .build())
                .collect(Collectors.toList());

        FinalResultDTO finalResult = FinalResultDTO.builder()
                .quizTitle(session.getQuiz().getTitle())
                .leaderboard(finalLeaderboard)
                .build();

        messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/ended", finalResult);
    }

    // ================== HELPERS ==================

    private void sendError(String pinCode, String message) {
        ErrorDTO error = ErrorDTO.builder()
                .message(message)
                .build();


        messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/error", error);
    }

    private GameSession getSessionOrThrow(String pinCode) {
        return gameSessionRepository.findByPinCode(pinCode)
                .orElseThrow(() -> new ResourceNotFoundException("Game session not found: " + pinCode));
    }

    private Question getCurrentQuestion(GameSession session) {
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(session.getQuiz().getId());
        int index = session.getCurrentQuestionIndex();

        if (index >= 0 && index < questions.size()) {
            return questions.get(index);
        }

        return null;
    }
}
