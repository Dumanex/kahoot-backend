package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.game.AnswerSubmitRequest;
import com.kahoot.kahoot_backend.config.UserPrincipal;
import com.kahoot.kahoot_backend.exception.ResourceNotFoundException;
import com.kahoot.kahoot_backend.service.GameMessageService;
import com.kahoot.kahoot_backend.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.function.Consumer;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {
    private final GameService gameService;
    private final GameMessageService gameMessageService;

    // ======================== PLAYERS (no JWT required; join is REST-only) ========================

    @MessageMapping("/game/{pinCode}/answer")
    public void submitAnswer(@DestinationVariable String pinCode, @Payload AnswerSubmitRequest request, Principal principal) {
        gameMessageService.handlePlayerAnswer(pinCode, request, principal);
    }

    // ======================== HOST (JWT set in STOMP CONNECT) ========================

    @MessageMapping("/game/{pinCode}/start")
    public void startGame(@DestinationVariable String pinCode, Principal principal) {
        executeAsHost(pinCode, principal, userId -> gameService.startGame(pinCode, userId));
    }

    @MessageMapping("/game/{pinCode}/next")
    public void nextQuestion(@DestinationVariable String pinCode, Principal principal) {
        executeAsHost(pinCode, principal, userId -> gameService.nextQuestion(pinCode, userId));
    }

    @MessageMapping("/game/{pinCode}/end")
    public void endGame(@DestinationVariable String pinCode, Principal principal) {
        executeAsHost(pinCode, principal, userId -> gameService.endGame(pinCode, userId));
    }

    @MessageMapping("/game/{pinCode}/finalize")
    public void finalizeUnansweredPlayers(@DestinationVariable String pinCode, Principal principal) {
        executeAsHost(pinCode, principal, userId -> gameService.finalizeUnanswered(pinCode, userId));
    }

    // ======================== HELPERS ========================
    private void executeAsHost(String pinCode, Principal principal, Consumer<Long> action) {
        try {
            action.accept(getUserId(principal));
        } catch (SecurityException e) {
            log.warn("Rejected host command for game {}: {}", pinCode, e.getMessage());
        } catch (IllegalStateException | ResourceNotFoundException e) {
            gameMessageService.sendErrorToUser(principal, e.getMessage());
        }
    }

    private Long getUserId(Principal principal) {
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUser().getId();
        }

        throw new SecurityException("Authentication required: host must send JWT in STOMP CONNECT");
    }
}
