package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.model.GameSession;
import com.kahoot.kahoot_backend.model.Question;

import java.time.Duration;
import java.time.LocalDateTime;

// When answering a question opens and closes, measured on the server (the client's clock is not trusted).
// The frontend shows each question 3 s ("Spremi se...") before answers are allowed.
final class QuestionTiming {
    static final long READY_PHASE_MS = 3000;
    // The client syncs its clock via serverTime, so allow a click slightly before the official start
    static final long EARLY_TOLERANCE_MS = 500;
    // Network delay after the timer ends on the client
    static final long LATE_TOLERANCE_MS = 1000;

    private QuestionTiming() {
    }

    static LocalDateTime answeringOpensAt(GameSession session) {
        return session.getQuestionStartedAt().plus(Duration.ofMillis(READY_PHASE_MS));
    }

    // Last moment an answer is accepted; after it the server finalizes the question on its own
    static LocalDateTime answerDeadline(GameSession session, Question question) {
        return answeringOpensAt(session).plus(Duration.ofMillis(timeLimitMs(question) + LATE_TOLERANCE_MS));
    }

    static long timeLimitMs(Question question) {
        return question.getTimeLimitSeconds() * 1000L;
    }
}
