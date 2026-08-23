package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findByGameSessionId(Long gameSessionId);

    boolean existsByGameSessionIdAndNickname(Long sessionId, String nickname);
}
