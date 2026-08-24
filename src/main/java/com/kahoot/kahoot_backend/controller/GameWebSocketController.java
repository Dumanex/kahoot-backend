package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.game.AnswerSubmitRequest;
import com.kahoot.kahoot_backend.DTOs.game.PlayerJoinRequest;
import com.kahoot.kahoot_backend.service.GameMessageService;
import com.kahoot.kahoot_backend.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {
    private final GameService gameService;
    private final GameMessageService gameMessageService;

    @MessageMapping("/game/{pinCode}/join")
    public void joinGame(@DestinationVariable String pinCode, @Payload PlayerJoinRequest request) {
        gameMessageService.handlePlayerJoin(pinCode, request.getNickname());
    }

    @MessageMapping("/game/{pinCode}/answer")
    public void submitAnswer(@DestinationVariable String pinCode, @Payload AnswerSubmitRequest request) {
        gameMessageService.handlePlayerAnswer(pinCode, request);
    }

    @MessageMapping("/game/{pinCode}/start")
    public void startGame(@DestinationVariable String pinCode) {
        gameMessageService.handleGameStart(pinCode);
    }

    @MessageMapping("/game/{pinCode}/next")
    public void nextQuestion(@DestinationVariable String pinCode) {
        gameMessageService.handleNextQuestion(pinCode);
    }

    @MessageMapping("/game/{pinCode}/end")
    public void endGame(@DestinationVariable String pinCode) {
        gameMessageService.handleEndGame(pinCode);
    }
}
