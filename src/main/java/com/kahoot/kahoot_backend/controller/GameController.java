package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.game.*;
import com.kahoot.kahoot_backend.config.UserPrincipal;
import com.kahoot.kahoot_backend.service.GameMessageService;
import com.kahoot.kahoot_backend.service.GameService;
import com.kahoot.kahoot_backend.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final PlayerService playerService;
    private final GameMessageService gameMessageService;

    // ========================= HOST ENDPOINTS =========================

    @PostMapping("/api/games/host")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GameSessionResponse> createGame(@AuthenticationPrincipal UserPrincipal userPrincipal, @Valid @RequestBody GameCreateRequest request) {
        Long userId = userPrincipal.getUser().getId();
        GameSessionResponse response = gameService.createSession(userId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/games/{pinCode}/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GameSessionResponse> startGame(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable String pinCode) {
        Long userId = userPrincipal.getUser().getId();
        GameSessionResponse session = gameService.startGame(pinCode, userId);

        return ResponseEntity.ok(session);
    }

    @PostMapping("/api/games/{pinCode}/next")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GameSessionResponse> nextQuestion(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable String pinCode) {
        Long userId = userPrincipal.getUser().getId();
        GameSessionResponse session = gameService.nextQuestion(pinCode, userId);

        return ResponseEntity.ok(session);
    }

    @PostMapping("/api/games/{pinCode}/end")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GameSessionResponse> endGame(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable String pinCode) {
        Long userId = userPrincipal.getUser().getId();
        GameSessionResponse session = gameService.endGame(pinCode, userId);

        return ResponseEntity.ok(session);
    }

    @GetMapping("/api/games/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HostGameSummaryResponse>> listMyGames(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Long userId = userPrincipal.getUser().getId();
        List<HostGameSummaryResponse> games = gameService.listHostSessions(userId);

        return ResponseEntity.ok(games);
    }

    // ========================= PUBLIC ENDPOINTS (no JWT) =========================

    @GetMapping("/api/games/public")
    public ResponseEntity<Page<PublicGameSummaryResponse>> listPublicGames(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PublicGameSummaryResponse> games = gameService.listPublicSessions(q, sortedPageable);

        return ResponseEntity.ok(games);
    }

    @GetMapping("/api/games/{pinCode}")
    public ResponseEntity<GameSessionResponse> getGame(@PathVariable String pinCode) {
        GameSessionResponse session = gameService.getSessionByPin(pinCode);

        return ResponseEntity.ok(session);
    }

    @PostMapping("/api/games/{pinCode}/join")
    public ResponseEntity<PlayerResponse> joinGame(@PathVariable String pinCode, @Valid @RequestBody PlayerJoinRequest request) {
        PlayerResponse player = playerService.joinGame(pinCode, request.getNickname());
        gameMessageService.broadcastPlayerList(pinCode);

        return ResponseEntity.ok(player);
    }

    @PostMapping("/api/games/{pinCode}/rejoin")
    public ResponseEntity<PlayerResponse> rejoinGame(@PathVariable String pinCode, @Valid @RequestBody PlayerRejoinRequest request) {
        PlayerResponse player = playerService.rejoinGame(pinCode, request.getPlayerId(), request.getRejoinToken());

        return ResponseEntity.ok(player);
    }

    @GetMapping("/api/games/{pinCode}/state")
    public ResponseEntity<GameStateResponse> getGameState(@PathVariable String pinCode) {
        GameStateResponse state = gameService.getGameState(pinCode);

        return ResponseEntity.ok(state);
    }
}
