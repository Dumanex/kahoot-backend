package com.kahoot.kahoot_backend.config;

import java.security.Principal;

public record PlayerPrincipal(Long playerId) implements Principal {
    public static String nameOf(Long playerId) {
        return "player:" + playerId;
    }

    @Override
    public String getName() {
        return nameOf(playerId);
    }
}
