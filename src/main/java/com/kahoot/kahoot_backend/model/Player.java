package com.kahoot.kahoot_backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "players",
        indexes = {
                @Index(name = "idx_players_session", columnList = "game_session_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_session_nickname",
                columnNames = {"game_session_id", "nickname"}
        )
)
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private GameSession gameSession;

    @Column(name = "nickname", length = 30)
    private String nickname;

    @Column(name = "score")
    private Integer score = 0;

    @Column(name = "streak")
    private Integer streak = 0;

    @Column(name = "rejoin_token", length = 36)
    private String rejoinToken;

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
