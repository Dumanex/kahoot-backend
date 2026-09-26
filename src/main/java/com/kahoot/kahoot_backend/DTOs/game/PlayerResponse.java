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
public class PlayerResponse {
    private Long id;

    private String nickname;

    private Integer score;

    private Integer streak;

    private LocalDateTime joinedAt;

    private String rejoinToken;

    private Boolean answeredCurrentQuestion;

    // Only in the rejoin response, when answeredCurrentQuestion is true; same shape as /answer-result
    private AnswerResultDTO currentAnswerResult;
}
