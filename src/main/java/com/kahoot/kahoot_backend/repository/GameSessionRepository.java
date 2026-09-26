package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.model.GameSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByPinCode(String pinCode);

    boolean existsByPinCode(String pinCode);

    List<GameSession> findByQuizCreatorIdOrderByCreatedAtDesc(Long creatorId);

    List<GameSession> findByStatusAndQuestionStartedAtBefore(GameSessionStatus status, LocalDateTime cutoff);

    // Bulk delete; players are removed by ON DELETE CASCADE in the database
    @Modifying
    @Query("DELETE FROM GameSession gs WHERE gs.status = :status AND gs.createdAt < :cutoff")
    int deleteByStatusAndCreatedAtBefore(@Param("status") GameSessionStatus status,
                                         @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT gs FROM GameSession gs JOIN gs.quiz q JOIN q.creator u " +
            "WHERE gs.status = :status AND gs.visibility = :visibility " +
            "AND (CAST(:q AS string) IS NULL " +
            "OR LOWER(q.title) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) " +
            "OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) " +
            "OR gs.pinCode LIKE CONCAT('%', CAST(:q AS string), '%'))")
    Page<GameSession> searchPublicSessions(@Param("status") GameSessionStatus status,
                                           @Param("visibility")GameSessionVisibility visibility,
                                           @Param("q") String q,
                                           Pageable pageable);
}
