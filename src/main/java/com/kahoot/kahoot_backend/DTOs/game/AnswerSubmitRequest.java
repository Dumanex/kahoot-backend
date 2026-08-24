package com.kahoot.kahoot_backend.DTOs.game;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerSubmitRequest {
    @NotNull(message = "PlayerID is required")
    private Long playerId;

    @NotNull(message = "QuestionID is required")
    private Long questionId;

    @NotNull(message = "AnswerID is required")
    private Long answerId;

    @NotNull(message = "Response time is required")
    @Positive(message = "Response time must be positive")
    private Integer responseTimeMs;
}
