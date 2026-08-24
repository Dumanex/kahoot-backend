package com.kahoot.kahoot_backend.DTOs.game;

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
public class AnswerDTO {
    private Long id;

    private String answerText;

    private AnswerSymbolType symbol;

    private AnswerColor color;

    private Integer orderIndex;
}
