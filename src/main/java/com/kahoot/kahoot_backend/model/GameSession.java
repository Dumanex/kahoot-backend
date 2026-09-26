package com.kahoot.kahoot_backend.model;

import com.kahoot.kahoot_backend.enums.GameSessionStatus;
import com.kahoot.kahoot_backend.enums.GameSessionVisibility;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_sessions", indexes = {
        @Index(name = "idx_game_sessions_pin", columnList = "pin_code")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSession {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "pin_code", unique = true, nullable = false)
    private String pinCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameSessionStatus status = GameSessionStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private GameSessionVisibility visibility = GameSessionVisibility.PRIVATE;

    @Column(name = "current_question_index")
    private Integer currentQuestionIndex = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "question_started_at")
    private LocalDateTime questionStartedAt;

    @Column(name = "question_finalized", nullable = false)
    private boolean questionFinalized;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
