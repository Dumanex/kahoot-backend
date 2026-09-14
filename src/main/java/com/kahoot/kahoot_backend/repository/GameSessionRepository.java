package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import com.kahoot.kahoot_backend.model.GameSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByPinCode(String pinCode);

    boolean existsByPinCode(String pinCode);

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
