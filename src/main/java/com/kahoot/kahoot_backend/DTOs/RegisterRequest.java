package com.kahoot.kahoot_backend.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    // No ':' so a username can never match a player's WebSocket name ("player:<id>")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may contain only letters, digits, '_', '.' and '-'")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email max 100 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 3, max = 50, message = "Password must be 3-50 characters")
    private String password;
}
