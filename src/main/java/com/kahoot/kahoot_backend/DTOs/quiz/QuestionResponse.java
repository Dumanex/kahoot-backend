package com.kahoot.kahoot_backend.DTOs.quiz;

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
public class QuestionResponse {
    private Long id;

    private QuestionType questionType;

    private String questionText;

    private String imageUrl;

    private String audioUrl;

    private Integer timeLimitSeconds;

    private Integer orderIndex;

    private List<AnswerResponse> answers;
}
