package com.kahoot.kahoot_backend.DTOs.game;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostGameSummaryResponse {
    private String pinCode;
    private Long quizId;
    private String quizTitle;
    private GameSessionStatus status;
    private GameSessionVisibility visibility;
    private Integer playerCount;
    private Integer currentQuestionIndex;
    private Integer totalQuestions;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    // Only for COMPLETED games with at least one player who scored points
    private String winnerNickname;
    private Integer winnerScore;
}
