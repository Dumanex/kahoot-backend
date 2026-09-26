package com.kahoot.kahoot_backend.DTOs.game;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
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

    // From the join response; checked against the player so nobody can answer for someone else
    @NotBlank(message = "Rejoin token is required")
    private String rejoinToken;

    @NotNull(message = "QuestionID is required")
    private Long questionId;

    @NotNull(message = "AnswerID is required")
    private Long answerId;

    @NotNull(message = "Response time is required")
    @Positive(message = "Response time must be positive")
    @Max(value = 300000, message = "Response time max 5 minutes")
    private Integer responseTimeMs;
}
