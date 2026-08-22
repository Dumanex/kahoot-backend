package com.kahoot.kahoot_backend.DTOs;

import lombok.Getter;

public class RegisterRequest {
    @Getter
    private String username;

    @Getter
    private String email;

    @Getter
    private String password;
}
