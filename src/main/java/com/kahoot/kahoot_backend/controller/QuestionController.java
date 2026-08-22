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

import java.util.List;

@RestController
@RequiredArgsConstructor
public class QuestionController {
    private final QuizService quizService;

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

        try {
            QuestionResponse question = quizService.addQuestion(quizId, userId, request);
            System.out.println("SUCCESS: Question created with ID: " + question.getId());
            return ResponseEntity.ok(question);
        } catch (Exception e) {
            System.out.println("CONTROLLER ERROR: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PutMapping("/api/questions/{id}")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestBody QuestionUpdateRequest request) {
        Long userId = userPrincipal.getUser().getId();

        try {
            QuestionResponse question = quizService.updateQuestion(id, userId, request);
            System.out.println("SUCCESS: Question updated with ID: " + question.getId());
            return ResponseEntity.ok(question);
        } catch (Exception e) {
            System.out.println("CONTROLLER ERROR: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @DeleteMapping("/api/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable Long id) {
        Long userId = userPrincipal.getUser().getId();
        quizService.deleteQuestion(id, userId);

        return ResponseEntity.noContent().build();
    }
}
