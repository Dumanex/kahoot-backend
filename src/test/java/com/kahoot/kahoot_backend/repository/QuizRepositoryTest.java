package com.kahoot.kahoot_backend.repository;

import com.kahoot.kahoot_backend.model.Quiz;
import com.kahoot.kahoot_backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class QuizRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private QuizRepository quizRepository;

    private User persistUser(String username) {
        User user = User.builder()
                .username(username)
                .email(username + "@test.com")
                .passwordHash("hashedPassword")
                .build();

        return entityManager.persistAndFlush(user);
    }

    @Test
    void findByCreatorId_ownerHasQuizzes_shouldReturnOnlyOwnerQuizzes() {
        User owner = persistUser("owner");
        User other = persistUser("other");

        entityManager.persistAndFlush(Quiz.builder().title("Quiz A").creator(owner).build());
        entityManager.persistAndFlush(Quiz.builder().title("Quiz B").creator(owner).build());
        entityManager.persistAndFlush(Quiz.builder().title("Quiz C").creator(other).build());

        Page<Quiz> result = quizRepository.findByCreatorId(owner.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Quiz::getTitle).containsExactlyInAnyOrder("Quiz A", "Quiz B");
    }

    @Test
    void findByCreatorId_ownerHasNoQuizzes_shouldReturnEmptyPage() {
        User owner = persistUser("owner");

        Page<Quiz> result = quizRepository.findByCreatorId(owner.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void findByCreatorId_pageableLimitsResults_shouldRespectPageSize() {
        User owner = persistUser("owner");

        entityManager.persistAndFlush(Quiz.builder().title("Quiz A").creator(owner).build());
        entityManager.persistAndFlush(Quiz.builder().title("Quiz B").creator(owner).build());
        entityManager.persistAndFlush(Quiz.builder().title("Quiz C").creator(owner).build());

        Page<Quiz> result = quizRepository.findByCreatorId(owner.getId(), PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }
}
