package com.kahoot.kahoot_backend.DTOs.game;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerJoinRequest {
    @NotBlank(message = "Nickname is required")
    @Size(min = 2, max = 30, message = "Nickname must be between 2 and 30 characters")
    private String nickname;
}
