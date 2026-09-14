package com.kahoot.kahoot_backend.DTOs.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicGameSummaryResponse {
    private String pinCode;
    private String quizTitle;
    private String hostName;
    private Integer totalQuestions;
    private Integer playerCount;
    private LocalDateTime createdAt;
}
