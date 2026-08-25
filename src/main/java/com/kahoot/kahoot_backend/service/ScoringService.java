package com.kahoot.kahoot_backend.service;

import org.springframework.stereotype.Service;

@Service
public class ScoringService {
    private static final int BASE_POINTS = 1000;
    private static final int MAX_SPEED_BONUS = 500;
    private static final int STREAK_BONUS = 50;

    /**
     * Calculates points based on IMPLEMENTATION_PLAN.md formula:
     * Points = 1000(base) + 500(MAX_SPEED_BONUS) * (1 - responseTime / timeLimit) + streak * 50(STREAK_BONUS)
     *
     * @param isCorrect       whether the answer is correct
     * @param responseTimeMs  response time in milliseconds
     * @param timeLimitSeconds time limit for the question in seconds
     * @param currentStreak   player's current streak before this answer
     * @return calculated points (0 if incorrect)
     */
    public int calculatePoints(boolean isCorrect, int responseTimeMs, int timeLimitSeconds, int currentStreak) {
        if (!isCorrect) {
            return 0;
        }
        if (responseTimeMs < 0) responseTimeMs = 0; // clamp negative
        if (timeLimitSeconds <= 0) timeLimitSeconds = 1; // avoid div by zero

        double timeLimitMs = timeLimitSeconds * 1000.0;
        double speedRatio = Math.max(0.0, 1.0 - (responseTimeMs / timeLimitMs));
        int speedBonus = (int) Math.round(MAX_SPEED_BONUS * speedRatio);

        int streakBonus = currentStreak * STREAK_BONUS;

        return BASE_POINTS + speedBonus + streakBonus;
    }

    public int calculateStreak(boolean isCorrect, int currentStreak) {
        return isCorrect ? currentStreak + 1 : 0;
    }
}
