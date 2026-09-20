package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.quiz.*;
import com.kahoot.kahoot_backend.config.SecurityConfig;
import com.kahoot.kahoot_backend.config.WithMockUserPrincipal;
import com.kahoot.kahoot_backend.enums.QuestionType;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.JwtService;
import com.kahoot.kahoot_backend.service.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(controllers = QuestionController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public class QuestionControllerTest {
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

    private List<AnswerCreateRequest> answers() {
        return List.of(
                AnswerCreateRequest.builder()
                        .answerText("A")
                        .isCorrect(true)
                        .orderIndex(0)
                        .build(),
                AnswerCreateRequest.builder()
                        .answerText("B")
                        .isCorrect(false)
                        .orderIndex(1)
                        .build()
        );
    }

    private QuestionResponse questionResponse() {
        return QuestionResponse.builder()
                .id(1L)
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionText("Question")
                .orderIndex(0)
                .build();
    }

    private QuizResponse quizWithQuestions() {
        return QuizResponse.builder()
                .id(1L)
                .title("Quiz")
                .creatorId(1L)
                .questions(List.of(questionResponse()))
                .build();
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void getQuestionsForQuiz_authenticated_shouldReturn200() throws Exception {
        when(quizService.getQuizById(1L, 1L)).thenReturn(quizWithQuestions());

        mockMvc.perform(get("/api/quizzes/1/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].questionText").value("Question"));
    }

    @Test
    void getQuestionsForQuiz_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/quizzes/1/questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void addQuestion_authenticated_shouldReturn200() throws Exception {
        QuestionCreateRequest request = QuestionCreateRequest.builder()
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionText("Question")
                .timeLimitSeconds(30)
                .orderIndex(0)
                .answers(answers())
                .build();

        when(quizService.addQuestion(eq(1L), eq(1L), any())).thenReturn(questionResponse());

        mockMvc.perform(post("/api/quizzes/1/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionText").value("Question"));
    }

    @Test
    void addQuestion_unauthenticated_shouldReturn401() throws Exception {
        QuestionCreateRequest request = QuestionCreateRequest.builder()
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionText("Question")
                .orderIndex(0)
                .answers(answers())
                .build();

        mockMvc.perform(post("/api/quizzes/1/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void updateQuestion_authenticated_shouldReturn200() throws Exception {
        QuestionUpdateRequest request = QuestionUpdateRequest.builder()
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionText("Updated Question")
                .timeLimitSeconds(30)
                .orderIndex(0)
                .answers(answers())
                .build();

        when(quizService.updateQuestion(eq(1L), eq(1L), any())).thenReturn(questionResponse());

        mockMvc.perform(put("/api/questions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateQuestion_unauthenticated_shouldReturn401() throws Exception {
        QuestionUpdateRequest request = QuestionUpdateRequest.builder()
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .questionText("Updated Question")
                .orderIndex(0)
                .answers(answers())
                .build();

        mockMvc.perform(put("/api/questions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void deleteQuestion_authenticated_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/questions/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteQuestion_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(delete("/api/questions/1"))
                .andExpect(status().isUnauthorized());
    }
}
