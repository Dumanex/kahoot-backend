package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtServiceTest {
    private static final String SECRET = "cffCtU8RbpX3SnZxV2U4hxZFk3FAB5VodH0iYWxOfr4";
    private static final long EXPIRATION_MS = 86_400_000L;

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);

        user = User.builder()
                .id(1L)
                .username("player1")
                .build();
    }

    @Test
    void generateToken_thenExtractUsername_shouldReturnOriginalUsername() {
        String token = jwtService.generateToken(user);

        String extractedUsername = jwtService.extractUsername(token);

        assertThat(extractedUsername).isEqualTo(user.getUsername());
    }

    @Test
    void generateToken_thenExtractUserId_shouldReturnOriginalUserId() {
        String token = jwtService.generateToken(user);

        Long extractedUserId = jwtService.extractUserId(token);

        assertThat(extractedUserId).isEqualTo(user.getId());
    }

    @Test
    void isTokenValid_matchingUsername_shouldReturnTrue() {
        String token = jwtService.generateToken(user);

        boolean valid = jwtService.isTokenValid(token, user.getUsername());

        assertThat(valid).isTrue();
    }

    @Test
    void isTokenValid_mismatchingUsername_shouldReturnFalse() {
        String token = jwtService.generateToken(user);

        boolean valid = jwtService.isTokenValid(token, "player2");

        assertThat(valid).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_shouldThrowExpiredJwtException() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", 1L)
                .issuedAt(new Date(System.currentTimeMillis() - 2000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtService.isTokenValid(expiredToken, user.getUsername()));
    }
}
