package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.game.AnswerDTO;
import com.kahoot.kahoot_backend.DTOs.game.AnswerResultDTO;
import com.kahoot.kahoot_backend.DTOs.game.LeaderboardEntryDTO;
import com.kahoot.kahoot_backend.DTOs.game.PlayerInfoDTO;
import com.kahoot.kahoot_backend.DTOs.game.QuestionDTO;
import com.kahoot.kahoot_backend.model.Answer;
import com.kahoot.kahoot_backend.model.Player;
import com.kahoot.kahoot_backend.model.PlayerAnswer;
import com.kahoot.kahoot_backend.model.Question;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

final class GameDtoMapper {
    private GameDtoMapper() {
    }

    static AnswerDTO toAnswerDTO(Answer answer) {
        return AnswerDTO.builder()
                .id(answer.getId())
                .answerText(answer.getAnswerText())
                .symbol(answer.getSymbol())
                .color(answer.getColor())
                .orderIndex(answer.getOrderIndex())
                .build();
    }

    static QuestionDTO toQuestionDTO(Question question, LocalDateTime questionStartedAt) {
        return QuestionDTO.builder()
                .id(question.getId())
                .questionType(question.getQuestionType())
                .questionText(question.getQuestionText())
                .imageUrl(question.getImageUrl())
                .audioUrl(question.getAudioUrl())
                .timeLimitSeconds(question.getTimeLimitSeconds())
                .orderIndex(question.getOrderIndex())
                .answers(question.getAnswers().stream().map(GameDtoMapper::toAnswerDTO).toList())
                .questionStartedAt(questionStartedAt != null ? toEpochMillis(questionStartedAt) : null)
                .serverTime(System.currentTimeMillis())
                .build();
    }

    static long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static AnswerResultDTO toAnswerResult(Player player, PlayerAnswer playerAnswer, Question question) {
        Answer correctAnswer = question.getAnswers().stream()
                .filter(Answer::getIsCorrect)
                .findFirst()
                .orElse(null);

        return AnswerResultDTO.builder()
                .playerId(player.getId())
                .nickname(player.getNickname())
                .isCorrect(playerAnswer != null && Boolean.TRUE.equals(playerAnswer.getIsCorrect()))
                .chosenAnswerId(playerAnswer != null && playerAnswer.getAnswer() != null ? playerAnswer.getAnswer().getId() : null)
                .pointsEarned(playerAnswer != null && playerAnswer.getPointsEarned() != null ? playerAnswer.getPointsEarned() : 0)
                .totalScore(player.getScore())
                .streak(player.getStreak())
                .correctAnswer(correctAnswer != null ? toAnswerDTO(correctAnswer) : null)
                .build();
    }

    static List<PlayerInfoDTO> toPlayerInfos(List<Player> players) {
        return players.stream()
                .map(player -> PlayerInfoDTO.builder()
                        .id(player.getId())
                        .nickname(player.getNickname())
                        .score(player.getScore())
                        .streak(player.getStreak())
                        .build())
                .toList();
    }

    static List<LeaderboardEntryDTO> toLeaderboard(List<Player> players) {
        return players.stream()
                .sorted(Comparator.comparing(Player::getScore).reversed())
                .map(player -> LeaderboardEntryDTO.builder()
                        .playerId(player.getId())
                        .nickname(player.getNickname())
                        .score(player.getScore())
                        .streak(player.getStreak())
                        .build())
                .toList();
    }
}
