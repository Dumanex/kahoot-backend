package com.kahoot.kahoot_backend.DTOs.quiz;

import com.kahoot.kahoot_backend.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionUpdateRequest {
    @NotNull(message = "Question type is required")
    private QuestionType questionType;

    @NotBlank(message = "Question text is required")
    private String questionText;

    private String imageUrl;

    private String audioUrl;

    @Positive(message = "Time limit must be positive")
    private Integer timeLimitSeconds;

    @NotNull(message = "Order index is required")
    private Integer orderIndex;

    @NotNull(message = "Answers are required")
    @Size(min = 2, max = 4, message = "Must have 2-4 answers")
    private List<AnswerCreateRequest> answers;
}
