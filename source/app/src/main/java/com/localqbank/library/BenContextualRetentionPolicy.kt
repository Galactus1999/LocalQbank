package com.localqbank.library

import kotlin.math.sqrt

/**
 * Lightweight contextual-bandit advisor for future adaptive SRS.
 *
 * It is deliberately advisory and disabled by default. It never changes the existing scheduler
 * until enough local observations exist and the user explicitly enables adaptive scheduling.
 * The model is a bounded diagonal linear-UCB approximation, not a neural model.
 */
class BenContextualRetentionPolicy {
    data class Context(
        val difficulty: Double,
        val responseLatencyMs: Double,
        val timeOfDay: Double,
        val recentAccuracy: Double,
        val repetitions: Double
    ) {
        fun vector(): DoubleArray = doubleArrayOf(
            1.0,
            difficulty.coerceIn(0.0, 1.0),
            (responseLatencyMs / 30_000.0).coerceIn(0.0, 1.0),
            timeOfDay.coerceIn(0.0, 1.0),
            recentAccuracy.coerceIn(0.0, 1.0),
            (repetitions / 10.0).coerceIn(0.0, 1.0)
        )
    }

    data class State(val count: Int, val rewardSum: Double, val mean: Double)

    fun multiplier(context: Context, state: State, enabled: Boolean): Double {
        if (!enabled || state.count < MIN_SAMPLES) return 1.0
        val x = context.vector()
        // Conservative confidence term: with no learned coefficients we only move a bounded
        // amount around the current interval. This is intentionally not wired into scheduling yet.
        val uncertainty = EXPLORATION / sqrt(state.count.toDouble())
        val centeredReward = (state.mean - 0.5).coerceIn(-0.5, 0.5)
        val signal = (centeredReward + uncertainty * x.average()).coerceIn(-0.25, 0.25)
        return (1.0 + signal).coerceIn(0.75, 1.25)
    }

    companion object { const val MIN_SAMPLES = 30; private const val EXPLORATION = 0.15 }
}
