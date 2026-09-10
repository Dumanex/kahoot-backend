package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.quiz.QuizCreateRequest;
import com.kahoot.kahoot_backend.DTOs.quiz.QuizResponse;
import com.kahoot.kahoot_backend.DTOs.quiz.QuizUpdateRequest;
import com.kahoot.kahoot_backend.config.SecurityConfig;
import com.kahoot.kahoot_backend.config.WithMockUserPrincipal;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.JwtService;
import com.kahoot.kahoot_backend.service.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QuizController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public class QuizControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QuizService quizService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private QuizResponse quizResponse() {
        return QuizResponse.builder()
                .id(1L)
                .title("Quiz")
                .creatorId(1L)
                .timePerQuestion(20)
                .build();
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void getMyQuizzes_authenticated_shouldReturn200() throws Exception {
        when(quizService.getUserQuizzes(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(quizResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/quizzes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Quiz"));
    }

    @Test
    void getMyQuizzes_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/quizzes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void createQuiz_authenticated_shouldReturn200() throws Exception {
        QuizCreateRequest request = QuizCreateRequest.builder()
                .title("Quiz")
                .timePerQuestion(20)
                .build();

        when(quizService.createQuiz(eq(1L), any())).thenReturn(quizResponse());
        mockMvc.perform(post("/api/quizzes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Quiz"));
    }

    @Test
    void createQuiz_unauthenticated_shouldReturn401() throws Exception {
        QuizCreateRequest request = QuizCreateRequest.builder()
                .title("Quiz")
                .timePerQuestion(20)
                .build();

        mockMvc.perform(post("/api/quizzes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void getQuizById_authenticated_shouldReturn200() throws Exception {
        when(quizService.getQuizById(1L, 1L)).thenReturn(quizResponse());

        mockMvc.perform(get("/api/quizzes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getQuizById_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/quizzes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void updateQuiz_authenticated_shouldReturn200() throws Exception {
        QuizUpdateRequest request = QuizUpdateRequest.builder()
                .title("Updated Quiz")
                .timePerQuestion(25)
                .build();

        when(quizService.updateQuiz(eq(1L), eq(1L), any())).thenReturn(quizResponse());

        mockMvc.perform(put("/api/quizzes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateQuiz_unauthenticated_shouldReturn401() throws Exception {
        QuizUpdateRequest request = QuizUpdateRequest.builder()
                .title("Updated Quiz")
                .timePerQuestion(25)
                .build();

        mockMvc.perform(put("/api/quizzes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void deleteQuiz_authenticated_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/quizzes/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteQuiz_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(delete("/api/quizzes/1"))
                .andExpect(status().isUnauthorized());
    }
}
