package com.kahoot.kahoot_backend.DTOs.quiz;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {
    private Long id;

    private String title;

    private String description;

    private Long creatorId;

    private Integer timePerQuestion;

    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private List<QuestionResponse> questions;
}
