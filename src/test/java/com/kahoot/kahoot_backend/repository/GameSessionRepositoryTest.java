package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Player;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
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

    private Quiz persistQuiz(String title, String username) {
        User creator = User.builder()
                .username(username)
                .email(username + "@test.com")
                .passwordHash("hashedPassword")
                .build();
        entityManager.persistAndFlush(creator);

        Quiz quiz = Quiz.builder()
                .title(title)
                .creator(creator)
                .build();

        return entityManager.persistAndFlush(quiz);
    }

    private GameSession persistSession(Quiz quiz, String pinCode, GameSessionStatus status, GameSessionVisibility visibility) {
        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode(pinCode)
                .status(status)
                .visibility(visibility)
                .currentQuestionIndex(0)
                .build();

        return entityManager.persistAndFlush(session);
    }

    @Test
    void deleteByStatusAndCreatedAtBefore_shouldDeleteOnlyOldWaitingSessionsWithTheirPlayers() {
        Quiz quiz = persistQuiz("Quiz", "creator");
        GameSession oldWaiting = persistSession(quiz, "111111", GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC);
        GameSession newWaiting = persistSession(quiz, "222222", GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC);
        GameSession oldInProgress = persistSession(quiz, "333333", GameSessionStatus.IN_PROGRESS, GameSessionVisibility.PUBLIC);

        // createdAt is set by @PrePersist, so age the sessions after persisting
        oldWaiting.setCreatedAt(LocalDateTime.now().minusHours(1));
        oldInProgress.setCreatedAt(LocalDateTime.now().minusHours(1));
        Player player = entityManager.persist(Player.builder().gameSession(oldWaiting).nickname("Ana").score(0).streak(0).build());
        entityManager.flush();
        Long playerId = player.getId();

        int deleted = gameSessionRepository.deleteByStatusAndCreatedAtBefore(GameSessionStatus.WAITING, LocalDateTime.now().minusMinutes(30));
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(gameSessionRepository.findById(oldWaiting.getId())).isEmpty();
        assertThat(entityManager.find(Player.class, playerId)).isNull();
        assertThat(gameSessionRepository.findById(newWaiting.getId())).isPresent();
        assertThat(gameSessionRepository.findById(oldInProgress.getId())).isPresent();
    }

    @Test
    void findByPinCode_existingPin_shouldReturnGameSession() {
        Quiz quiz = persistQuiz("Quiz", "creator");
        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .visibility(GameSessionVisibility.PRIVATE)
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
        Quiz quiz = persistQuiz("Quiz", "creator");
        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode("654321")
                .status(GameSessionStatus.WAITING)
                .visibility(GameSessionVisibility.PRIVATE)
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

    @Test
    void searchPublicSessions_waitingAndPublicNoQuery_shouldReturnSession() {
        Quiz quiz = persistQuiz("Quiz", "creator1");
        persistSession(quiz, "111111", GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC);

        Page<GameSession> result = gameSessionRepository.searchPublicSessions(
                GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPinCode()).isEqualTo("111111");
    }

    @Test
    void searchPublicSessions_waitingAndPrivateNoQuery_shouldNotReturnSession() {
        Quiz quiz = persistQuiz("Quiz", "creator2");
        persistSession(quiz, "222222", GameSessionStatus.WAITING, GameSessionVisibility.PRIVATE);

        Page<GameSession> result = gameSessionRepository.searchPublicSessions(
                GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchPublicSessions_queryMatchesQuizTitle_shouldReturnSession() {
        Quiz quiz = persistQuiz("Science Quiz", "creator3");
        persistSession(quiz, "333333", GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC);

        Page<GameSession> result = gameSessionRepository.searchPublicSessions(
                GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, "quiz", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPinCode()).isEqualTo("333333");
    }

    @Test
    void searchPublicSessions_queryMatchesPartialPinCode_shouldReturnMatchingSessions() {
        Quiz quiz = persistQuiz("Quiz", "creator4");
        persistSession(quiz, "123456", GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC);
        persistSession(quiz, "234683", GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC);
        persistSession(quiz, "999999", GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC);

        Page<GameSession> result = gameSessionRepository.searchPublicSessions(
                GameSessionStatus.WAITING, GameSessionVisibility.PUBLIC, "234", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2)
                .extracting(GameSession::getPinCode)
                .containsExactlyInAnyOrder("123456", "234683");
    }
}
