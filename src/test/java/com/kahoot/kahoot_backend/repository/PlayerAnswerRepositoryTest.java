package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.enums.QuestionType;
import com.kahoot.kahoot_backend.model.*;
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
public class PlayerAnswerRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlayerAnswerRepository playerAnswerRepository;

    private Question persistQuestion() {
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

        Question question = Question.builder()
                .quiz(quiz)
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionText("Question")
                .orderIndex(0)
                .build();

        return entityManager.persistAndFlush(question);
    }

    private Player persistPlayer(Quiz quiz) {
        GameSession session = GameSession.builder()
                .quiz(quiz)
                .pinCode("123456")
                .status(GameSessionStatus.IN_PROGRESS)
                .visibility(GameSessionVisibility.PRIVATE)
                .currentQuestionIndex(0)
                .build();
        entityManager.persistAndFlush(session);

        Player player = Player.builder()
                .gameSession(session)
                .nickname("player1")
                .score(0)
                .streak(0)
                .build();

        return entityManager.persistAndFlush(player);
    }

    private PlayerAnswer playerAnswer(Player player, Question question) {
        return PlayerAnswer.builder()
                .player(player)
                .question(question)
                .responseTimeMs(2000)
                .isCorrect(true)
                .pointsEarned(1000)
                .build();
    }

    @Test
    void findByPlayerIdAndQuestionId_existingAnswer_shouldReturnPlayerAnswer() {
        Question question = persistQuestion();
        Player player = persistPlayer(question.getQuiz());
        entityManager.persistAndFlush(playerAnswer(player, question));

        Optional<PlayerAnswer> result = playerAnswerRepository.findByPlayerIdAndQuestionId(player.getId(), question.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPointsEarned()).isEqualTo(1000);
    }

    @Test
    void findByPlayerIdAndQuestionId_noAnswer_shouldReturnEmpty() {
        Question question = persistQuestion();
        Player player = persistPlayer(question.getQuiz());

        Optional<PlayerAnswer> result = playerAnswerRepository.findByPlayerIdAndQuestionId(player.getId(), question.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void existsByPlayerIdAndQuestionId_alreadyAnswered_shouldReturnTrue() {
        Question question = persistQuestion();
        Player player = persistPlayer(question.getQuiz());
        entityManager.persistAndFlush(playerAnswer(player, question));

        boolean result = playerAnswerRepository.existsByPlayerIdAndQuestionId(player.getId(), question.getId());

        assertThat(result).isTrue();
    }

    @Test
    void existsByPlayerIdAndQuestionId_notAnswered_shouldReturnFalse() {
        Question question = persistQuestion();
        Player player = persistPlayer(question.getQuiz());

        boolean result = playerAnswerRepository.existsByPlayerIdAndQuestionId(player.getId(), question.getId());

        assertThat(result).isFalse();
    }
}
