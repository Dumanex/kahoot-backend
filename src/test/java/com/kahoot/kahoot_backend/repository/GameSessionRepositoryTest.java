package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Quiz;
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
public class GameSessionRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    private Quiz persistQuiz() {
        User creator = User.builder()
                .username("creator")
                .email("creator@test.com")
                .passwordHash("hashedPassword")
                .build();
        entityManager.persistAndFlush(creator);

        Quiz quiz = Quiz.builder()
                .title("Quiz")
                .creator(creator)
                .build();

        return entityManager.persistAndFlush(quiz);
    }

    @Test
    void findByPinCode_existingPin_shouldReturnGameSession() {
        Quiz quiz = persistQuiz();
        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .currentQuestionIndex(0)
                .build();
        entityManager.persistAndFlush(session);

        Optional<GameSession> result = gameSessionRepository.findByPinCode("123456");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(GameSessionStatus.WAITING);
    }

    @Test
    void findByPinCode_unknownPin_shouldReturnEmpty() {
        Optional<GameSession> result = gameSessionRepository.findByPinCode("999999");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByPinCode_existingPin_shouldReturnTrue() {
        Quiz quiz = persistQuiz();
        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode("654321")
                .status(GameSessionStatus.WAITING)
                .currentQuestionIndex(0)
                .build();
        entityManager.persistAndFlush(session);

        boolean result = gameSessionRepository.existsByPinCode("654321");

        assertThat(result).isTrue();
    }

    @Test
    void existsByPinCode_unknownPin_shouldReturnFalse() {
        boolean result = gameSessionRepository.existsByPinCode("000000");

        assertThat(result).isFalse();
    }
}
