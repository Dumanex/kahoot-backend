package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.AuthResponse;
import com.kahoot.kahoot_backend.exception.DuplicateResourceException;
import com.kahoot.kahoot_backend.exception.InvalidCredentialsException;
import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User existingUser;

    @BeforeEach
    public void setUp() {
        existingUser = User.builder()
                .id(1L)
                .username("player1")
                .email("player1@example.com")
                .passwordHash("hashed-password")
                .build();
    }

    @Test
    void register_newUsernameAndEmail_shouldReturnAuthResponse() {
        when(userRepository.findByUsername("newUser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2L);
            return u;
        });

        AuthResponse response = authService.register("newUser", "new@example.com", "password");

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getUsername()).isEqualTo("newUser");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void register_duplicateUsername_shouldThrow() {
        when(userRepository.findByUsername("player1")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.register("player1", "other@example.com", "password"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void register_duplicateEmail_shouldThrow() {
        when(userRepository.findByUsername("newUser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("player1@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.register("newUser", "player1@example.com", "password"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    void login_correctCredentials_shouldReturnAuthResponseWithToken() {
        when(userRepository.findByUsername("player1")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(existingUser)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        AuthResponse response = authService.login("player1", "password");

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86_400_000L);
    }

    @Test
    void login_unknownUsername_shouldThrow() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_wrongPassword_shouldThrow() {
        when(userRepository.findByUsername("player1")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPassword", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("player1", "wrongPassword"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
