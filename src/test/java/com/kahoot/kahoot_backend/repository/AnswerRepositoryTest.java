package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.enums.QuestionType;
import com.kahoot.kahoot_backend.model.Answer;
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
public class AnswerRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AnswerRepository answerRepository;

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

    private Answer answer(Question question, int orderIndex, boolean isCorrect) {
        return Answer.builder()
                .question(question)
                .answerText("Answer " + orderIndex)
                .isCorrect(isCorrect)
                .orderIndex(orderIndex)
                .build();
    }

    @Test
    void findByQuestionId_questionHasAnswers_shouldReturnAllAnswers() {
        Question question = persistQuestion();
        entityManager.persistAndFlush(answer(question, 0, true));
        entityManager.persistAndFlush(answer(question, 1, false));

        List<Answer> result = answerRepository.findByQuestionId(question.getId());

        assertThat(result).hasSize(2);
    }

    @Test
    void findByQuestionId_questionHasNoAnswers_shouldReturnEmptyList() {
        Question question = persistQuestion();

        List<Answer> result = answerRepository.findByQuestionId(question.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByQuestionId_questionHasAnswers_shouldRemoveAllAnswersForThatQuestion() {
        Question question = persistQuestion();
        entityManager.persistAndFlush(answer(question, 0, true));
        entityManager.persistAndFlush(answer(question, 1, false));

        answerRepository.deleteByQuestionId(question.getId());
        entityManager.flush();

        List<Answer> result = answerRepository.findByQuestionId(question.getId());
        assertThat(result).isEmpty();
    }
}
