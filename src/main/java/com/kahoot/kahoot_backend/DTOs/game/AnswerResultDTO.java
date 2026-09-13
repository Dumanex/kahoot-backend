package com.kahoot.kahoot_backend.DTOs.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResultDTO {
    private Long playerId;

    private String nickname;

    private Boolean isCorrect;

    private Long chosenAnswerId;

    private int pointsEarned;

    private int totalScore;

    private int streak;

    private AnswerDTO correctAnswer;
}
