package com.kahoot.kahoot_backend.DTOs.quiz;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kahoot.kahoot_backend.enums.AnswerColor;
import com.kahoot.kahoot_backend.enums.AnswerSymbolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResponse {
    private Long id;

    private String answerText;

    private Boolean isCorrect;

    private Integer orderIndex;

    @JsonProperty("symbol")
    private AnswerSymbolType symbol;

    @JsonProperty("color")
    private AnswerColor color;
}
