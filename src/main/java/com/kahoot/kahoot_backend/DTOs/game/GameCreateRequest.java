package com.kahoot.kahoot_backend.DTOs.game;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameCreateRequest {
    @NotNull(message = "Quiz ID is required")
    private Long quizId;
}
