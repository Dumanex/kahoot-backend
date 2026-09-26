package com.kahoot.kahoot_backend.DTOs.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Public progress of the current question, without revealing who answered what
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnsweredCountDTO {
    private int answeredCount;

    private int totalPlayers;
}
