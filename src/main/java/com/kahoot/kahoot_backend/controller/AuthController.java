package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.LoginRequest;
import com.kahoot.kahoot_backend.DTOs.RegisterRequest;
import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        User user = authService.register(request.getUsername(), request.getEmail(), request.getPassword());

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());

        return ResponseEntity.ok(Map.of(
                "token", token
        ));
    }
}
