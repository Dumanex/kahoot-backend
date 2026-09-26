package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Question;

import java.time.Duration;
import java.time.LocalDateTime;

final class QuestionTiming {
    static final long READY_PHASE_MS = 3000;
    static final long EARLY_TOLERANCE_MS = 500;
    static final long LATE_TOLERANCE_MS = 1000;

    private QuestionTiming() {
    }

    static LocalDateTime answeringOpensAt(GameSession session) {
        return session.getQuestionStartedAt().plus(Duration.ofMillis(READY_PHASE_MS));
    }

    static LocalDateTime answerDeadline(GameSession session, Question question) {
        return answeringOpensAt(session).plus(Duration.ofMillis(timeLimitMs(question) + LATE_TOLERANCE_MS));
    }

    static long timeLimitMs(Question question) {
        return question.getTimeLimitSeconds() * 1000L;
    }
}
