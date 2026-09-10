package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class UserRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_existingUsername_shouldReturnUser() {
        User user = User.builder()
                .username("player1")
                .email("player1@test.com")
                .passwordHash("hashedPassword")
                .build();
        entityManager.persistAndFlush(user);

        Optional<User> result = userRepository.findByUsername("player1");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("player1@test.com");
    }

    @Test
    void findByUsername_unknownUsername_shouldReturnEmpty() {
        Optional<User> result = userRepository.findByUsername("ghost");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_existingEmail_shouldReturnUser() {
        User user = User.builder()
                .username("player2")
                .email("player2@test.com")
                .passwordHash("hashedPassword")
                .build();
        entityManager.persistAndFlush(user);

        Optional<User> result = userRepository.findByEmail("player2@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("player2");
    }

    @Test
    void findByEmail_unknownEmail_shouldReturnEmpty() {
        Optional<User> result = userRepository.findByEmail("ghost@test.com");

        assertThat(result).isEmpty();
    }
}
