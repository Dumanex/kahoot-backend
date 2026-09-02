package com.kahoot.kahoot_backend.model;

import com.kahoot.kahoot_backend.enums.AnswerColor;
import com.kahoot.kahoot_backend.enums.AnswerSymbolType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "answers", indexes = {
        @Index(name = "idx_answers_question", columnList = "question_id")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Answer {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "answer_text", length = 500)
    private String answerText;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol")
    private AnswerSymbolType symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "color")
    private AnswerColor color;
}
