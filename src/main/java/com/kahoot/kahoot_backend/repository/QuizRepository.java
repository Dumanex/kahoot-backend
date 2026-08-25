package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByCreatorId(Long creatorId);

    Page<Quiz> findByCreatorId(Long creatorId, Pageable pageable);
}
