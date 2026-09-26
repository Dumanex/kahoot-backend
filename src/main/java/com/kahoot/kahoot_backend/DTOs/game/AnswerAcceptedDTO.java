package com.kahoot.kahoot_backend.DTOs.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Sent only to the player who answered; the result (correct or not, points) comes after finalize
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerAcceptedDTO {
    private Long questionId;

    private Long chosenAnswerId;
}
