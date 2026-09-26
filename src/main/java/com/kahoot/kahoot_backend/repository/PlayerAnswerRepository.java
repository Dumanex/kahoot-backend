package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.PlayerAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerAnswerRepository extends JpaRepository<PlayerAnswer, Long> {
    Optional<PlayerAnswer> findByPlayerIdAndQuestionId(Long playerId, Long questionId);
    boolean existsByPlayerIdAndQuestionId(Long playerId, Long questionId);

    int countByQuestionIdAndPlayerGameSessionId(Long questionId, Long gameSessionId);

    List<PlayerAnswer> findByQuestionIdAndPlayerGameSessionId(Long questionId, Long gameSessionId);
}
