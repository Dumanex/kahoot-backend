package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.quiz.*;
import com.kahoot.kahoot_backend.enums.QuestionType;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.model.Answer;
import com.kahoot.kahoot_backend.model.Question;
import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.model.User;
import com.kahoot.kahoot_backend.repository.AnswerRepository;
import com.kahoot.kahoot_backend.repository.QuestionRepository;
import com.kahoot.kahoot_backend.repository.QuizRepository;
import com.kahoot.kahoot_backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class QuizService {
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    // =================== QUIZ CRUD ===================
    @Transactional
    public QuizResponse createQuiz(Long userId, QuizCreateRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Quiz quiz = Quiz.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .timePerQuestion(request.getTimePerQuestion())
                .creator(creator)
                .build();

        quiz = quizRepository.save(quiz);

        return mapToQuizResponse(quiz);
    }

    public QuizResponse getQuizById(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        validateOwnership(quiz, userId);

        return mapToQuizResponseWithQuestions(quiz);
    }

    public Page<QuizResponse> getUserQuizzes(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return quizRepository.findByCreatorId(userId, pageable)
                .map(this::mapToQuizResponseWithQuestions);
    }

    @Transactional
    public QuizResponse updateQuiz(Long quizId, Long userId, QuizUpdateRequest request) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        validateOwnership(quiz, userId);

        quiz.setTitle(request.getTitle());
        quiz.setDescription(request.getDescription());
        quiz.setTimePerQuestion(request.getTimePerQuestion());

        quiz = quizRepository.save(quiz);

        return mapToQuizResponse(quiz);
    }

    @Transactional
    public void deleteQuiz(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        validateOwnership(quiz, userId);

        quizRepository.delete(quiz);
    }

    // =================== QUESTION CRUD ===================
    @Transactional
    public QuestionResponse addQuestion(Long quizId, Long userId, QuestionCreateRequest request) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + quizId));

        validateOwnership(quiz, userId);
        validateQuestionRequest(request);

        Question question = Question.builder()
                .quiz(quiz)
                .questionType(request.getQuestionType())
                .questionText(request.getQuestionText())
                .imageUrl(request.getImageUrl())
                .audioUrl(request.getAudioUrl())
                .timeLimitSeconds(request.getTimeLimitSeconds())
                .orderIndex(request.getOrderIndex())
                .build();

        question = questionRepository.save(question);

        Question finalQuestion = question;
        List<Answer> answers = request.getAnswers()
                .stream()
                .map(answerReq -> Answer.builder()
                        .question(finalQuestion)
                        .answerText(answerReq.getAnswerText())
                        .isCorrect(answerReq.getIsCorrect())
                        .orderIndex(answerReq.getOrderIndex())
                        .symbol(answerReq.getSymbol())
                        .color(answerReq.getColor())
                        .build())
                .collect(Collectors.toList());

        question.setAnswers(answers);
        question = questionRepository.save(question);

        log.info("Question saved with ID: {}, answers count: {}", question.getId(), answers.size());

        try {
            QuestionResponse response = mapToQuestionResponseWithAnswers(question, answers);
            log.info("Response built successfully: {}", response.getId());
            return response;
        } catch (Exception e) {
            log.error("Error building response", e);
            throw e;
        }
    }

    @Transactional
    public QuestionResponse updateQuestion(Long questionId, Long userId, QuestionUpdateRequest request) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));

        validateOwnership(question.getQuiz(), userId);
        validateQuestionRequest(request);

        question.setQuestionType(request.getQuestionType());
        question.setQuestionText(request.getQuestionText());
        question.setImageUrl(request.getImageUrl());
        question.setAudioUrl(request.getAudioUrl());
        question.setTimeLimitSeconds(request.getTimeLimitSeconds());
        question.setOrderIndex(request.getOrderIndex());

        final Question questionToUpdate = question;

        List<Answer> existingAnswers = questionToUpdate.getAnswers();
        existingAnswers.clear();

        List<Answer> newAnswers = request.getAnswers()
                .stream()
                .map(answerReq -> Answer.builder()
                        .question(questionToUpdate)
                        .answerText(answerReq.getAnswerText())
                        .isCorrect(answerReq.getIsCorrect())
                        .orderIndex(answerReq.getOrderIndex())
                        .symbol(answerReq.getSymbol())
                        .color(answerReq.getColor())
                        .build())
                .collect(Collectors.toList());

        existingAnswers.addAll(newAnswers);
        question = questionRepository.save(questionToUpdate);

        return mapToQuestionResponseWithAnswers(question, newAnswers);
    }

    @Transactional
    public void deleteQuestion(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));

        validateOwnership(question.getQuiz(), userId);

        questionRepository.delete(question);
    }

    // =================== HELPER METHODS ===================
    private QuizResponse mapToQuizResponse(Quiz quiz) {
        return QuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .creatorId(quiz.getCreator().getId())
                .timePerQuestion(quiz.getTimePerQuestion())
                .createdAt(quiz.getCreatedAt())
                .build();
    }

    private QuizResponse mapToQuizResponseWithQuestions(Quiz quiz) {
        QuizResponse response = mapToQuizResponse(quiz);
        List<QuestionResponse> questions = questionRepository.findByQuizIdOrderByOrderIndex(quiz.getId())
                .stream()
                .map(this::mapToQuestionResponse)
                .collect(Collectors.toList());

        response.setQuestions(questions);

        return response;
    }

    private QuestionResponse mapToQuestionResponse(Question question) {
        List<AnswerResponse> answers = question.getAnswers()
                .stream()
                .map(this::mapToAnswerResponse)
                .collect(Collectors.toList());

        return QuestionResponse.builder()
                .id(question.getId())
                .questionType(question.getQuestionType())
                .questionText(question.getQuestionText())
                .imageUrl(question.getImageUrl())
                .audioUrl(question.getAudioUrl())
                .timeLimitSeconds(question.getTimeLimitSeconds())
                .orderIndex(question.getOrderIndex())
                .answers(answers)
                .build();
    }

    private AnswerResponse mapToAnswerResponse(Answer answer) {
        return AnswerResponse.builder()
                .id(answer.getId())
                .answerText(answer.getAnswerText())
                .isCorrect(answer.getIsCorrect())
                .orderIndex(answer.getOrderIndex())
                .symbol(answer.getSymbol())
                .color(answer.getColor())
                .build();
    }

    private QuestionResponse mapToQuestionResponseWithAnswers(Question question, List<Answer> answers) {
        List<AnswerResponse> answerResponses = answers
                .stream()
                .map(this::mapToAnswerResponse)
                .toList();

        return QuestionResponse.builder()
                .id(question.getId())
                .questionType(question.getQuestionType())
                .questionText(question.getQuestionText())
                .imageUrl(question.getImageUrl())
                .audioUrl(question.getAudioUrl())
                .timeLimitSeconds(question.getTimeLimitSeconds())
                .orderIndex(question.getOrderIndex())
                .answers(answerResponses)
                .build();
    }

    private void validateOwnership(Quiz quiz, Long userId) {
        if (!quiz.getCreator().getId().equals(userId)) {
            throw new SecurityException("You are not creator of this quiz, DO NOT HAVE PERMISSION TO MODIFY");
        }
    }

    private void validateQuestionRequest(QuestionCreateRequest request) {
        validateAnswers(request.getQuestionType(), request.getAnswers());
    }

    private void validateQuestionRequest(QuestionUpdateRequest request) {
        validateAnswers(request.getQuestionType(), request.getAnswers());
    }

    private void validateAnswers(QuestionType questionType, List<AnswerCreateRequest> answers) {
        long correctCount = answers
                .stream()
                .filter(AnswerCreateRequest::getIsCorrect)
                .count();

        if (correctCount == 0) {
            throw new IllegalArgumentException("At least one answer must be correct");
        }

        switch (questionType) {
            case MULTIPLE_CHOICE:
                if (answers.size() < 2 || answers.size() > 4) {
                    throw new IllegalArgumentException("MULTIPLE_CHOICE must have 2-4 answers");
                }
                if (correctCount != 1) {
                    throw new IllegalArgumentException("MULTIPLE_CHOICE must have exactly 1 correct answer");
                }
                break;
            case TRUE_FALSE:
                if (answers.size() != 2) {
                    throw new IllegalArgumentException("TRUE_FALSE must have exactly 2 answers");
                }
                if (correctCount != 1) {
                    throw new IllegalArgumentException("TRUE_FALSE must have exactly 1 correct answer");
                }
                break;
            case IMAGE_RECOGNITION:
            case AUDIO:
                if (answers.size() < 2 || answers.size() > 4) {
                    throw new IllegalArgumentException("IMAGE_RECOGNITION and AUDIO must have 2-4 answers");
                }
                if (correctCount != 1) {
                    throw new IllegalArgumentException("IMAGE_RECOGNITION and AUDIO must have exactly 1 correct answer");
                }
                break;
        }
    }
}
