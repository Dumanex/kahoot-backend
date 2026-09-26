package com.kahoot.kahoot_backend.DTOs.game;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRejoinRequest {
    @NotNull(message = "Player id is required")
    private Long playerId;

    @NotBlank(message = "Rejoin token is required")
    private String rejoinToken;
}
