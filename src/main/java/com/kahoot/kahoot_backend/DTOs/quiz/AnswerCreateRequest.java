package com.kahoot.kahoot_backend.DTOs.quiz;

import com.kahoot.kahoot_backend.enums.AnswerColor;
import com.kahoot.kahoot_backend.enums.AnswerSymbolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerCreateRequest {
    private Long id;

    @NotBlank(message = "Answer text is required")
    private String answerText;

    @NotNull(message = "isCorrect is required")
    private Boolean isCorrect;

    @NotNull(message = "Order index is required")
    private Integer orderIndex;

    private AnswerSymbolType symbol;

    private AnswerColor color;
}
