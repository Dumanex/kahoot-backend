package com.kahoot.kahoot_backend.DTOs.game;

import com.kahoot.kahoot_backend.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDTO {
    private Long id;

    private QuestionType questionType;

    private String questionText;

    private String imageUrl;

    private String audioUrl;

    private Integer timeLimitSeconds;

    private Integer orderIndex;

    private List<AnswerDTO> answers;

    // Epoch millis when the server started this question
    private Long questionStartedAt;

    // Epoch millis at send time; lets the client correct for clock skew
    private Long serverTime;
}
