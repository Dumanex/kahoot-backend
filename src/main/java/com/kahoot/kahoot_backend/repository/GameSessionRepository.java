package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByPinCode(String pinCode);

    boolean existsByPinCode(String pinCode);
}
