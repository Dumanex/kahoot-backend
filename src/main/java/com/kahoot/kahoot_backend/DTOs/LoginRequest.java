package com.kahoot.kahoot_backend.DTOs;

import lombok.Getter;

public class LoginRequest {
    @Getter
    private String username;

    @Getter
    private String password;
}
