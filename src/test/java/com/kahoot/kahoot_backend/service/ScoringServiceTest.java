package com.kahoot.kahoot_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ScoringServiceTest {
    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    @Test
    void calculatePoints_correctAnswerWithNoDelay_shouldReturnMaxSpeedBonus() {
        int points = scoringService.calculatePoints(true, 0, 10, 0);

        assertThat(points).isEqualTo(1500);
    }

    @Test
    void calculatePoints_correctAnswerAtTimeLimit_shouldReturnZeroSpeedBonus() {
        int points = scoringService.calculatePoints(true, 10_000, 10, 0);

        assertThat(points).isEqualTo(1000);
    }

    @Test
    void calculatePoints_incorrectAnswer_shouldReturnZeroRegardlessOfStreak() {
        int points = scoringService.calculatePoints(false, 0, 10, 5);

        assertThat(points).isZero();
    }

    @Test
    void calculatePoints_negativeResponseTime_shouldClampToZero() {
        int pointWithNegative = scoringService.calculatePoints(true, -500, 10, 0);
        int pointsWithZero = scoringService.calculatePoints(true, 0, 10, 0);

        assertThat(pointWithNegative).isEqualTo(pointsWithZero);
    }

    @Test
    void calculatePoints_zeroOrNegativeTimeLimit_shouldClampToOneSecond() {
        int pointsWithZeroLimit = scoringService.calculatePoints(true, 0, 0, 0);
        int pointsWithOneSecondLimit = scoringService.calculatePoints(true, 0, 1, 0);

        assertThat(pointsWithZeroLimit).isEqualTo(pointsWithOneSecondLimit);
    }

    @Test
    void calculatePoints_withStreak_shouldAddStreakBonus() {
        int pointsNoStreak = scoringService.calculatePoints(true, 0, 10, 0);
        int pointsWithStreak = scoringService.calculatePoints(true, 0, 10, 3);

        assertThat(pointsWithStreak).isEqualTo(pointsNoStreak + 3 * 50);
    }

    @Test
    void calculateStreak_correctAnswer_shouldIncrementStreak() {
        int streak = scoringService.calculateStreak(true, 4);

        assertThat(streak).isEqualTo(5);
    }

    @Test
    void calculateStreak_incorrectAnswer_shouldResetStreakToZero() {
        int streak = scoringService.calculateStreak(false, 7);

        assertThat(streak).isZero();
    }
}
