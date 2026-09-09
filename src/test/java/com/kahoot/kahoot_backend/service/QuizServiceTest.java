package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.quiz.AnswerCreateRequest;
import com.kahoot.kahoot_backend.DTOs.quiz.QuestionCreateRequest;
import com.kahoot.kahoot_backend.DTOs.quiz.QuestionResponse;
import com.kahoot.kahoot_backend.enums.QuestionType;
import com.kahoot.kahoot_backend.model.Question;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.AnswerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import com.kahoot.kahoot_backend.repository.QuizRepository;
import com.kahoot.kahoot_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class QuizServiceTest {
    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private QuizService quizService;

    private Quiz quiz;

    @BeforeEach
    public void setUp() {
        User creator = User.builder()
                .id(1L)
                .username("host")
                .build();

        quiz = Quiz.builder()
                .id(10L)
                .title("Quiz")
                .creator(creator)
                .build();
    }

    private AnswerCreateRequest answer(boolean isCorrect) {
        return AnswerCreateRequest.builder()
                .answerText("answer")
                .isCorrect(isCorrect)
                .orderIndex(0)
                .build();
    }

    private QuestionCreateRequest request(QuestionType type, List<AnswerCreateRequest> answers) {
        return QuestionCreateRequest.builder()
                .questionType(type)
                .questionText("question")
                .timeLimitSeconds(20)
                .orderIndex(0)
                .answers(answers)
                .build();
    }

    @Test
    void addQuestion_multipleChoiceWithOneCorrectAndValidCount_shouldSucceed() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionCreateRequest req = request(QuestionType.MULTIPLE_CHOICE, List.of(answer(true), answer(false), answer(false)));

        QuestionResponse response = quizService.addQuestion(10L, 1L, req);

        assertThat(response.getQuestionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
    }

    @Test
    void addQuestion_multipleChoiceWithTooFewAnswers_shouldThrow() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        QuestionCreateRequest req = request(QuestionType.MULTIPLE_CHOICE, List.of(answer(true)));

        assertThatThrownBy(() -> quizService.addQuestion(10L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2-4 answers");
    }

    @Test
    void addQuestion_multipleChoiceWithTooManyAnswers_shouldThrow() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        QuestionCreateRequest req = request(QuestionType.MULTIPLE_CHOICE, List.of(answer(true), answer(false), answer(false), answer(false), answer(false)));

        assertThatThrownBy(() -> quizService.addQuestion(10L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2-4 answers");
    }

    @Test
    void addQuestion_multipleChoiceWithNoCorrectAnswer_shouldThrow() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        QuestionCreateRequest req = request(QuestionType.MULTIPLE_CHOICE, List.of(answer(false), answer(false)));

        assertThatThrownBy(() -> quizService.addQuestion(10L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one answer must be correct");
    }

    @Test
    void addQuestion_multipleChoiceWithTwoCorrectAnswers_shouldThrow() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        QuestionCreateRequest req = request(QuestionType.MULTIPLE_CHOICE, List.of(answer(true), answer(true)));

        assertThatThrownBy(() -> quizService.addQuestion(10L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 1 correct answer");
    }

    @Test
    void addQuestion_trueFalseWithExactlyTwoAnswersOneCorrect_shouldSucceed() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionCreateRequest req = request(QuestionType.TRUE_FALSE, List.of(answer(true), answer(false)));

        QuestionResponse response = quizService.addQuestion(10L, 1L, req);

        assertThat(response.getQuestionType()).isEqualTo(QuestionType.TRUE_FALSE);
    }

    @Test
    void addQuestion_trueFalseWithThreeAnswers_shouldThrow() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        QuestionCreateRequest req = request(QuestionType.TRUE_FALSE, List.of(answer(true), answer(false), answer(false)));

        assertThatThrownBy(() -> quizService.addQuestion(10L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 2 answers");
    }

    @Test
    void addQuestion_imageRecognitionWithValidAnswers_shouldSucceed() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionCreateRequest req = request(QuestionType.IMAGE_RECOGNITION, List.of(answer(true), answer(false), answer(false)));

        QuestionResponse response = quizService.addQuestion(10L, 1L, req);

        assertThat(response.getQuestionType()).isEqualTo(QuestionType.IMAGE_RECOGNITION);
    }

    @Test
    void addQuestion_audioWithFewAnswers_shouldThrow() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        QuestionCreateRequest req = request(QuestionType.AUDIO, List.of(answer(true)));

        assertThatThrownBy(() -> quizService.addQuestion(10L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2-4 answers");
    }
}
