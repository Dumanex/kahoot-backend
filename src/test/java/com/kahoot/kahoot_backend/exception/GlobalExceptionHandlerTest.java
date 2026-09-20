package com.kahoot.kahoot_backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalExceptionHandlerTest {
    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void handleResourceNotFoundException_shouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Quiz not found: 1");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Quiz not found: 1");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    void handleDuplicateResource_shouldReturn409() {
        DuplicateResourceException ex = new DuplicateResourceException("Username already exists");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateResource(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Username already exists");
    }

    @Test
    void handleInvalidCredentials_shouldReturn401() {
        InvalidCredentialsException ex = new InvalidCredentialsException("Invalid credentials");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void handleDataIntegrityViolation_usernameConstraint_shouldReturnUsernameMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed",
                new RuntimeException("duplicate key value violates unique constraint \"username\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Username already exists");
    }

    @Test
    void handleDataIntegrityViolation_emailConstraint_shouldReturnEmailMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed",
                new RuntimeException("duplicate key value violates unique constraint \"email\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getBody().getMessage()).isEqualTo("Email already exists");
    }

    @Test
    void handleDataIntegrityViolation_duplicateAnswerConstraint_shouldReturnAlreadyAnsweredMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed",
                new RuntimeException("violates unique constraint \"uk_player_answers_player_question\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getBody().getMessage()).isEqualTo("You have already answered this question");
    }

    @Test
    void handleDataIntegrityViolation_duplicateNicknameConstraint_shouldReturnNicknameTakenMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed",
                new RuntimeException("violates unique constraint \"uk_game_session_nickname\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getBody().getMessage()).isEqualTo("Nickname is already taken in this game");
    }

    @Test
    void handleDataIntegrityViolation_unknownConstraint_shouldReturnGenericMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed",
                new RuntimeException("some other constraint violation"));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getBody().getMessage()).isEqualTo("This action conflicts with existing data");
    }

    @Test
    void handleUserNotFound_shouldReturn404() {
        UsernameNotFoundException ex = new UsernameNotFoundException("User not found: player1");

        ResponseEntity<ErrorResponse> response = handler.handleUserNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("User not found: player1");
    }

    @Test
    void handleValidationErrors_shouldReturn400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("request", "username", "must not be blank")));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getErrors()).containsEntry("username", "must not be blank");
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("/secured/resource");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied: Authentication required");
    }

    @Test
    void handleSecurityException_shouldReturn403() {
        SecurityException ex = new SecurityException("You are not the creator of this quiz");

        ResponseEntity<ErrorResponse> response = handler.handleSecurityException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("You are not the creator of this quiz");
    }

    @Test
    void handleOptimisticLocking_shouldReturn409() {
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException(String.class, 1L);

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLocking(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("This record was modified concurrently, please retry");
    }

    @Test
    void handleGlobalException_shouldReturn500() {
        Exception ex = new RuntimeException("Unexpected failure");

        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }
}
