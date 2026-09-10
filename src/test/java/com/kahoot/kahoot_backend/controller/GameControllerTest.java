package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.game.GameCreateRequest;
import com.kahoot.kahoot_backend.DTOs.game.GameSessionResponse;
import com.kahoot.kahoot_backend.DTOs.game.PlayerJoinRequest;
import com.kahoot.kahoot_backend.DTOs.game.PlayerResponse;
import com.kahoot.kahoot_backend.config.SecurityConfig;
import com.kahoot.kahoot_backend.config.WithMockUserPrincipal;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.GameService;
import com.kahoot.kahoot_backend.service.JwtService;
import com.kahoot.kahoot_backend.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GameController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public class GameControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private GameSessionResponse session() {
        return GameSessionResponse.builder()
                .id(1L)
                .pinCode("123456")
                .status(GameSessionStatus.WAITING)
                .quizId(10L)
                .currentQuestionIndex(0)
                .totalQuestions(5)
                .build();
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void createGame_authenticated_shouldReturn200() throws Exception {
        GameCreateRequest request = GameCreateRequest.builder()
                .quizId(10L)
                .build();

        when(gameService.createSession(eq(1L), any())).thenReturn(session());

        mockMvc.perform(post("/api/games/host")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinCode").value("123456"));
    }

    @Test
    void createGame_unauthenticated_shouldReturn401() throws Exception {
        GameCreateRequest request = GameCreateRequest.builder().quizId(10L).build();

        mockMvc.perform(post("/api/games/host")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void startGame_authenticated_shouldReturn200() throws Exception {
        when(gameService.startGame(eq("123456"), eq(1L))).thenReturn(session());

        mockMvc.perform(post("/api/games/123456/start"))
                .andExpect(status().isOk());
    }

    @Test
    void startGame_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/games/123456/start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void nextQuestion_authenticated_shouldReturn200() throws Exception {
        when(gameService.nextQuestion(eq("123456"), eq(1L))).thenReturn(session());

        mockMvc.perform(post("/api/games/123456/next"))
                .andExpect(status().isOk());
    }

    @Test
    void nextQuestion_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/games/123456/next"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUserPrincipal(id = 1L, username = "host")
    void endGame_authenticated_shouldReturn200() throws Exception {
        when(gameService.endGame(eq("123456"), eq(1L))).thenReturn(session());

        mockMvc.perform(post("/api/games/123456/end"))
                .andExpect(status().isOk());
    }

    @Test
    void endGame_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/games/123456/end"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGame_noAuth_shouldReturn200() throws Exception {
        when(gameService.getSessionByPin("123456")).thenReturn(session());

        mockMvc.perform(get("/api/games/123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinCode").value("123456"));
    }

    @Test
    void joinGame_noAuth_shouldReturn200() throws Exception {
        PlayerJoinRequest request = PlayerJoinRequest.builder().nickname("player1").build();
        PlayerResponse response = PlayerResponse.builder()
                .id(1L)
                .nickname("player1")
                .score(0)
                .streak(0)
                .build();

        when(playerService.joinGame("123456", "player1")).thenReturn(response);

        mockMvc.perform(post("/api/games/123456/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("player1"));
    }
}
