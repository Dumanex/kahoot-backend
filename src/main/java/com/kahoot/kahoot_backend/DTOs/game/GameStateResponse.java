package com.kahoot.kahoot_backend.DTOs.game;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateResponse {
    private String pinCode;

    private GameSessionStatus status;

    private String quizTitle;

    private Integer currentQuestionIndex;

    private Integer totalQuestions;

    // null in WAITING and COMPLETED
    private QuestionDTO currentQuestion;

    // Epoch millis; null when there is no current question
    private Long questionStartedAt;

    // Epoch millis; lets the client correct for clock skew
    private Long serverTime;

    private Integer answeredCount;

    // True once the host finalized the current question
    private Boolean questionFinalized;

    // Same shape as /answer-result, one per player; only when questionFinalized is true, otherwise null
    private List<AnswerResultDTO> roundResults;

    private List<PlayerInfoDTO> players;

    private List<LeaderboardEntryDTO> leaderboard;
}
