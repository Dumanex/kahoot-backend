package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.quiz.QuizCreateRequest;
import com.kahoot.kahoot_backend.DTOs.quiz.QuizResponse;
import com.kahoot.kahoot_backend.DTOs.quiz.QuizUpdateRequest;
import com.kahoot.kahoot_backend.config.UserPrincipal;
import com.kahoot.kahoot_backend.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {
    private final QuizService quizService;

    @GetMapping
    public ResponseEntity<Page<QuizResponse>> getMyQuizzes (
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = userPrincipal.getUser().getId();
        Page<QuizResponse> quizzes = quizService.getUserQuizzes(userId, pageable);

        return ResponseEntity.ok(quizzes);
    }

    @PostMapping
    public ResponseEntity<QuizResponse> createQuiz(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody QuizCreateRequest request) {
        Long userId = userPrincipal.getUser().getId();
        QuizResponse quiz = quizService.createQuiz(userId, request);

        return ResponseEntity.ok(quiz);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuizResponse> getQuizById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        Long userId = userPrincipal.getUser().getId();
        QuizResponse quiz = quizService.getQuizById(id, userId);

        return ResponseEntity.ok(quiz);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuizResponse> updateQuiz(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody QuizUpdateRequest request) {
        Long userId = userPrincipal.getUser().getId();
        QuizResponse quiz = quizService.updateQuiz(id, userId, request);

        return ResponseEntity.ok(quiz);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuiz(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        Long userId = userPrincipal.getUser().getId();
        quizService.deleteQuiz(id, userId);

        return ResponseEntity.noContent().build();
    }
}
