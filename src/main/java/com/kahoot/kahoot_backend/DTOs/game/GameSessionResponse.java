package com.kahoot.kahoot_backend.DTOs.game;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSessionResponse {
    private Long id;

    private String pinCode;

    private GameSessionStatus status;

    private Long quizId;

    private String quizTitle;

    private Integer currentQuestionIndex;

    private Integer totalQuestions;

    private LocalDateTime createdAt;
}
