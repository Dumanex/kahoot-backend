package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.enums.QuestionType;
import com.kahoot.kahoot_backend.model.Question;
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
public class QuestionRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private QuestionRepository questionRepository;

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

    private Question question(Quiz quiz, int orderIndex) {
        return Question.builder()
                .quiz(quiz)
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionText("Question " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    @Test
    void findByQuizIdOrderByOrderIndex_multipleQuestions_shouldReturnInOrderIndexOrder() {
        Quiz quiz = persistQuiz();

        entityManager.persistAndFlush(question(quiz, 1));
        entityManager.persistAndFlush(question(quiz, 0));
        entityManager.persistAndFlush(question(quiz, 2));

        List<Question> result = questionRepository.findByQuizIdOrderByOrderIndex(quiz.getId());

        assertThat(result).extracting(Question::getOrderIndex).containsExactly(0, 1, 2);
    }

    @Test
    void findByQuizIdOrderByOrderIndex_noQuestions_shouldReturnEmptyList() {
        Quiz quiz = persistQuiz();

        List<Question> result = questionRepository.findByQuizIdOrderByOrderIndex(quiz.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void countByQuizId_multipleQuestions_shouldReturnCorrectCount() {
        Quiz quiz = persistQuiz();
        entityManager.persistAndFlush(question(quiz, 0));
        entityManager.persistAndFlush(question(quiz, 1));

        int result = questionRepository.countByQuizId(quiz.getId());

        assertThat(result).isEqualTo(2);
    }

    @Test
    void countByQuizId_noQuestions_shouldReturnZero() {
        Quiz quiz = persistQuiz();

        int result = questionRepository.countByQuizId(quiz.getId());

        assertThat(result).isEqualTo(0);
    }
}
