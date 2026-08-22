package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuizIdOrderByOrderIndex(Long quizId);
}
