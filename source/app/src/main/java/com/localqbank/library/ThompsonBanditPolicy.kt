package com.localqbank.library

import kotlin.random.Random

/**
 * Pure Thompson-sampling bandit used by [AdaptiveEngineManager] to choose between bounded
 * runtime performance actions (prefetch depth, cache TTL).
 *
 * Extracted so the sampling and reward-update math can be unit tested without a Context or
 * SharedPreferences. Behavior-preserving extraction: the approximation and decay constant
 * below are copied verbatim from the original inline implementation.
 *
 * This is a lightweight approximate sampler (normal approximation to a Beta distribution),
 * not a full Beta-distribution implementation - matching the original inline comment
 * "Lightweight Thompson-style sampler; no statistics dependency is needed."
 */
class ThompsonBanditPolicy(private val random: Random = Random.Default) {

    /** Approximates a draw from Beta(alpha0, beta0) via a normal approximation, clamped to [0,1]. */
    fun sampleBeta(alpha0: Double, beta0: Double): Double {
        val mean = alpha0 / (alpha0 + beta0).coerceAtLeast(1.0)
        val variance = mean * (1.0 - mean) / (alpha0 + beta0 + 1.0)
        return (mean + random.nextDouble(-1.0, 1.0) * kotlin.math.sqrt(variance) * 1.8)
            .coerceIn(0.0, 1.0)
    }

    /**
     * Picks the candidate with the highest sampled value for its (alpha, beta) belief.
     * [candidates] maps each candidate to its current (alpha, beta) belief.
     * Returns null only if [candidates] is empty.
     */
    fun <T> selectAction(candidates: Map<T, Pair<Double, Double>>): T? =
        candidates.maxByOrNull { (_, ab) -> sampleBeta(ab.first, ab.second) }?.key

    /**
     * Updates a Beta(alpha, beta) belief given an observed [reward] in [0,1], with mild
     * forgetting (0.995 decay per update) so the policy stays responsive to changing
     * user/device behaviour rather than converging permanently on early data.
     */
    fun updateBelief(alpha: Double, beta: Double, reward: Double): Pair<Double, Double> {
        val r = reward.coerceIn(0.0, 1.0)
        val newAlpha = alpha * DECAY + r
        val newBeta = beta * DECAY + (1.0 - r)
        return newAlpha to newBeta
    }

    companion object {
        private const val DECAY = 0.995
    }
}
