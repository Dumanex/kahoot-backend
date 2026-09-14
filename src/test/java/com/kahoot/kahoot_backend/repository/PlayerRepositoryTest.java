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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PlayerRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlayerRepository playerRepository;

    private GameSession persistGameSession() {
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
        entityManager.persistAndFlush(quiz);

        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .visibility(GameSessionVisibility.PRIVATE)
                .currentQuestionIndex(0)
                .build();

        return entityManager.persistAndFlush(session);
    }

    private Player player(GameSession session, String nickname) {
        return Player.builder()
                .gameSession(session)
                .nickname(nickname)
                .score(0)
                .streak(0)
                .build();
    }

    @Test
    void findByGameSessionId_sessionHasPlayers_shouldReturnAllPlayers() {
        GameSession session = persistGameSession();
        entityManager.persistAndFlush(player(session, "player1"));
        entityManager.persistAndFlush(player(session, "player2"));

        List<Player> result = playerRepository.findByGameSessionId(session.getId());

        assertThat(result).hasSize(2);
    }

    @Test
    void findByGameSessionId_sessionHasNoPlayers_shouldReturnEmptyList() {
        GameSession session = persistGameSession();

        List<Player> result = playerRepository.findByGameSessionId(session.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void existsByGameSessionIdAndNickname_freeNickname_shouldReturnFalse() {
        GameSession session = persistGameSession();
        entityManager.persistAndFlush(player(session, "player1"));

        boolean result = playerRepository.existsByGameSessionIdAndNickname(session.getId(), "player2");

        assertThat(result).isFalse();
    }

    @Test
    void existsByGameSessionIdAndNickname_takenNickname_shouldReturnTrue() {
        GameSession session = persistGameSession();
        entityManager.persistAndFlush(player(session, "player1"));

        boolean result = playerRepository.existsByGameSessionIdAndNickname(session.getId(), "player1");

        assertThat(result).isTrue();
    }
}
