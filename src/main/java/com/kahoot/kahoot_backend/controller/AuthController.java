package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.AuthResponse;
import com.kahoot.kahoot_backend.DTOs.LoginRequest;
import com.kahoot.kahoot_backend.DTOs.RegisterRequest;
import com.kahoot.kahoot_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
         AuthResponse response = authService.register(request.getUsername(), request.getEmail(), request.getPassword());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request.getUsername(), request.getPassword());

        return ResponseEntity.ok(response);
    }
}
