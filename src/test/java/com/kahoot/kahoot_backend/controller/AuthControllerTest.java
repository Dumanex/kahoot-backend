package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.AuthResponse;
import com.kahoot.kahoot_backend.DTOs.LoginRequest;
import com.kahoot.kahoot_backend.DTOs.RegisterRequest;
import com.kahoot.kahoot_backend.config.SecurityConfig;
import com.kahoot.kahoot_backend.exception.DuplicateResourceException;
import com.kahoot.kahoot_backend.exception.InvalidCredentialsException;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.AuthService;
import com.kahoot.kahoot_backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void register_validRequest_shouldReturn200WithUser() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("player1")
                .email("player1@test.com")
                .password("password123")
                .build();

        AuthResponse response = AuthResponse.builder()
                .id(1L)
                .username("player1")
                .email("player1@test.com")
                .build();

        when(authService.register("player1", "player1@test.com", "password123")).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("player1"));
    }

    @Test
    void register_duplicateUsername_shouldReturn409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("player1")
                .email("player1@test.com")
                .password("password123")
                .build();

        when(authService.register("player1", "player1@test.com", "password123")).thenThrow(new DuplicateResourceException("Username already exists"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void register_blankUsername_shouldReturn400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("")
                .email("player1@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_usernameWithColon_shouldReturn400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("player:5")
                .email("player1@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_shouldReturn200WithToken() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("player1")
                .password("password123")
                .build();

        AuthResponse response = AuthResponse.builder()
                .id(1L)
                .username("player1")
                .email("player1@test.com")
                .token("jwt-token")
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .build();

        when(authService.login("player1", "password123")).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_wrongCredentials_shouldReturn401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("player1")
                .password("wrongPassword")
                .build();

        when(authService.login("player1", "wrongPassword")).thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_blankPassword_shouldReturn400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("player1")
                .password("")
                .build();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
