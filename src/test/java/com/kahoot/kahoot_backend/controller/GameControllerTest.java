package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.game.*;
import com.kahoot.kahoot_backend.config.SecurityConfig;
import com.kahoot.kahoot_backend.config.WithMockUserPrincipal;
import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.repository.UserRepository;
import com.kahoot.kahoot_backend.service.GameService;
import com.kahoot.kahoot_backend.service.JwtService;
import com.kahoot.kahoot_backend.service.PlayerService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
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

    @Test
    void listPublicGames_noAuth_shouldReturn200() throws Exception {
        PublicGameSummaryResponse summary = PublicGameSummaryResponse.builder()
                .pinCode("123456")
                .quizTitle("Quiz")
                .hostName("host")
                .totalQuestions(5)
                .playerCount(2)
                .build();

        when(gameService.listPublicSessions(isNull(), any())).thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/games/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pinCode").value("123456"));
    }

    @Test
    void listPublicGames_withQueryParam_shouldPassQueryToServiceAndReturnList() throws Exception {
        PublicGameSummaryResponse summary = PublicGameSummaryResponse.builder()
                .pinCode("654321")
                .quizTitle("Science Quiz")
                .hostName("host")
                .totalQuestions(5)
                .playerCount(0)
                .build();

        when(gameService.listPublicSessions(eq("science"), any())).thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/games/public").param("q", "science"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pinCode").value("654321"));

        verify(gameService).listPublicSessions(eq("science"), any());
    }

    @Test
    void listPublicGames_withoutQueryParam_shouldCallServiceWithNullQuery() throws Exception {
        when(gameService.listPublicSessions(isNull(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/games/public"))
                .andExpect(status().isOk());

        verify(gameService).listPublicSessions(isNull(), any());
    }

    @Test
    void getGame_withInvalidToken_shouldStillReturn200() throws Exception {
        when(jwtService.extractUsername(anyString())).thenThrow(new MalformedJwtException("bad token"));
        when(gameService.getSessionByPin("123456")).thenReturn(session());

        mockMvc.perform(get("/api/games/123456")
                .header("Authorization", "Bearer garbage-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinCode").value("123456"));
    }

    @Test
    void listPublicGames_withExpiredToken_shouldStillReturn200() throws Exception {
        when(jwtService.extractUsername(anyString())).thenThrow(new ExpiredJwtException(null, null, "JWT expired"));
        when(gameService.listPublicSessions(isNull(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/games/public")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isOk());
    }
}
