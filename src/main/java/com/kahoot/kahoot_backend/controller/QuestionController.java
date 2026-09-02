package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.quiz.QuestionCreateRequest;
import com.kahoot.kahoot_backend.DTOs.quiz.QuestionResponse;
import com.kahoot.kahoot_backend.DTOs.quiz.QuestionUpdateRequest;
import com.kahoot.kahoot_backend.DTOs.quiz.QuizResponse;
import com.kahoot.kahoot_backend.config.UserPrincipal;
import com.kahoot.kahoot_backend.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class QuestionController {
    private final QuizService quizService;
    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

    @GetMapping("/api/quizzes/{quizId}/questions")
    public ResponseEntity<List<QuestionResponse>> getQuestionsForQuiz(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable Long quizId) {
        QuizResponse quiz = quizService.getQuizById(quizId);

        return ResponseEntity.ok(quiz.getQuestions());
    }

    @PostMapping("/api/quizzes/{quizId}/questions")
    public ResponseEntity<QuestionResponse> addQuestion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long quizId,
            @Valid @RequestBody QuestionCreateRequest request) {
        Long userId = userPrincipal.getUser().getId();


        QuestionResponse question = quizService.addQuestion(quizId, userId, request);
        log.debug("Question created with ID: {}", question.getId());
        return ResponseEntity.ok(question);
    }

    @PutMapping("/api/questions/{id}")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody QuestionUpdateRequest request) {
        Long userId = userPrincipal.getUser().getId();

        QuestionResponse question = quizService.updateQuestion(id, userId, request);
        log.debug("Question updated with ID: {}", question.getId());

        return ResponseEntity.ok(question);
    }

    @DeleteMapping("/api/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        Long userId = userPrincipal.getUser().getId();
        quizService.deleteQuestion(id, userId);

        return ResponseEntity.noContent().build();
    }
}
