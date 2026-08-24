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
    private final PlayerAnswerRepository playerAnswerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String TOPIC_PREFIX = "/topic/game/";

    // ================== PLAYER JOIN (WebSocket) ==================
    @Transactional
    public void handlePlayerJoin(String pinCode, String nickname) {
        GameSession session = getSessionOrThrow(pinCode);

        if (session.getStatus() != GameSessionStatus.WAITING) {
            sendError(pinCode, "Cannot join: game is not in WAITING status");
            return;
        }

        if (playerRepository.existsByGameSessionIdAndNickname(session.getId(), nickname)) {
            sendError(pinCode, "Nickname '" + nickname + "' is already taken");
            return;
        }

        Player player = Player.builder()
                .gameSession(session)
                .nickname(nickname)
                .score(0)
                .streak(0)
                .joinedAt(LocalDateTime.now())
                .build();

        player = playerRepository.save(player);

        // Broadcast players list to everyone
        broadcastPlayerList(pinCode, session);
    }

    // ================== PLAYER ANSWER ==================

    @Transactional
    public void handlePlayerAnswer(String pinCode, AnswerSubmitRequest request) {
        GameSession session = getSessionOrThrow(pinCode);

        if (session.getStatus() != GameSessionStatus.IN_PROGRESS) {
            sendError(pinCode, "Game is not started yet");
            return;
        }

        Question currentQuestion = getCurrentQuestion(session);
        if (currentQuestion == null || !currentQuestion.getId().equals(request.getQuestionId())) {
            sendError(pinCode, "Invalid question");
            return;
        }

        // Nadji playera (za sada pretpostavljamo jedan player po konekciji -/frontend šalje nickname u header?)
        // U realnom scenariju bi trebalo da imaš playerId u poruci ili session mapping
        // Za sada: uzmi prvog playera iz sesije (demo)
        List<Player> players = playerRepository.findByGameSessionId(session.getId());
        if (players.isEmpty()) {
            sendError(pinCode, "Players not found");
            return;
        }

        // TODO: U pravoj implementaciji, playerId bi dolazio iz STOMP session attributes ili JWT
        // Za demo uzimamo prvog - FRONTEND MORA DA ŠALJE playerId ILI nickname
        Player player = players.getFirst();
        if (playerAnswerRepository.existsByPlayerIdAndQuestionId(player.getId(), request.getQuestionId())) {
            sendError(pinCode, "Already answered this question");
            return;
        }

        Answer correctAnswer = currentQuestion.getAnswers()
                .stream()
                .filter(Answer::getIsCorrect)
                .findFirst()
                .orElse(null);

        boolean isCorrect = correctAnswer != null && correctAnswer.getId().equals(request.getAnswerId());

        // Racunanje poena (brzi odgovor - vise poena, max 1000)
        int basePoints = isCorrect ? 1000 : 0;
        int timeBonus = isCorrect ? Math.max(0, 1000 - request.getResponseTimeMs()) : 0;
        int pointsEarned = basePoints + timeBonus;

        // Streak update
        int newStreak = isCorrect ? player.getStreak() + 1 : 0;
        player.setStreak(newStreak);
        player.setScore(player.getScore() + pointsEarned);
        playerRepository.save(player);

        Answer selectedAnswer = currentQuestion.getAnswers()
                .stream()
                .filter(answer -> answer.getId().equals(request.getAnswerId()))
                .findFirst()
                .orElse(null);

        // Save PlayerAnswer
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

        // Broadcast answer result to player (private ili na topic)
        broadcastAnswerResult(pinCode, player, currentQuestion, isCorrect, pointsEarned, newStreak);

        // Broadcast leaderboard
        broadcastLeaderboard(pinCode, session);
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

    private void broadcastAnswerResult(String pinCode, Player player, Question question, boolean isCorrect, int pointsEarned, int streak) {
        AnswerDTO correctAnswer = question.getAnswers()
                .stream()
                .filter(Answer::getIsCorrect)
                .map(answer -> AnswerDTO.builder()
                        .id(answer.getId())
                        .answerText(answer.getAnswerText())
                        .symbol(answer.getSymbol())
                        .color(answer.getColor())
                        .orderIndex(answer.getOrderIndex())
                        .build())
                .findFirst()
                .orElse(null);

        AnswerResultDTO result = AnswerResultDTO.builder()
                .playerId(player.getId())
                .nickname(player.getNickname())
                .isCorrect(isCorrect)
                .pointsEarned(pointsEarned)
                .totalScore(player.getScore())
                .streak(streak)
                .correctAnswer(correctAnswer)
                .build();

        messagingTemplate.convertAndSend(TOPIC_PREFIX + pinCode + "/answer-result", result);
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

    private void broadcastCurrentQuestion(String pinCode, GameSession session) {
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

    private void broadcastFinalResults(String pinCode, GameSession session) {
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
