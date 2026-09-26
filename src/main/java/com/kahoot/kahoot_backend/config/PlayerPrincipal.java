package com.kahoot.kahoot_backend.config;

import java.security.Principal;

// WebSocket identity of a player (set on STOMP CONNECT from playerId + rejoinToken).
// The name is what convertAndSendToUser uses to reach this player's /user/queue/... destinations.
public record PlayerPrincipal(Long playerId) implements Principal {
    public static String nameOf(Long playerId) {
        return "player:" + playerId;
    }

    @Override
    public String getName() {
        return nameOf(playerId);
    }
}
