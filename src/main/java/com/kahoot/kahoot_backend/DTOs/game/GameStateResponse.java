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

    private QuestionDTO currentQuestion;

    private Long questionStartedAt;

    private Long serverTime;

    private Integer answeredCount;

    private Boolean questionFinalized;

    private List<AnswerResultDTO> roundResults;

    private List<PlayerInfoDTO> players;

    private List<LeaderboardEntryDTO> leaderboard;
}
