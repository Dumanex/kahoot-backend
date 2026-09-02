package com.kahoot.kahoot_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "players",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_session_nickname",
                columnNames = {"game_session_id", "nickname"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_session_id", nullable = false)
    private GameSession gameSession;

    @Column(name = "nickname", length = 30)
    private String nickname;

    @Column(name = "score")
    private Integer score = 0;

    @Column(name = "streak")
    private Integer streak = 0;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
    }
}
