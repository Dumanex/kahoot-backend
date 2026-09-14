package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.game.*;
import com.kahoot.kahoot_backend.config.UserPrincipal;
import com.kahoot.kahoot_backend.service.GameService;
import com.kahoot.kahoot_backend.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final PlayerService playerService;

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

    // ========================= PUBLIC ENDPOINTS (no JWT) =========================

    @GetMapping("/api/games/public")
    public ResponseEntity<Page<PublicGameSummaryResponse>> listPublicGames(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PublicGameSummaryResponse> games = gameService.listPublicSessions(q, pageable);

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

        return ResponseEntity.ok(player);
    }
}
