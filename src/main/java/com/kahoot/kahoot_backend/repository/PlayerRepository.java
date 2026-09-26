package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findByGameSessionId(Long gameSessionId);

    int countByGameSessionId(Long gameSessionId);

    boolean existsByGameSessionIdAndNickname(Long sessionId, String nickname);

    // Top scorer; on a tie the player who joined first
    Optional<Player> findFirstByGameSessionIdOrderByScoreDescIdAsc(Long gameSessionId);
}
