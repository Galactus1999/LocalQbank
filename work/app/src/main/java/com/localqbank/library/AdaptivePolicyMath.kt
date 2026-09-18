package com.localqbank.library

/**
 * Pure, Android-free calculations used by AdaptiveEngineManager.
 *
 * This class deliberately contains no persistence, UI, database, or manager calls. It exists
 * to make the adaptive decision math independently testable while preserving the existing
 * AdaptiveEngineManager as the authoritative owner of adaptive runtime behaviour.
 */
object AdaptivePolicyMath {
    fun userModel(answers: Long, accuracyEma: Float): String = when {
        answers < 20L -> "cold"
        accuracyEma >= 0.80f -> "strong"
        accuracyEma < 0.60f -> "weak"
        else -> "steady"
    }

    fun confidence(observations: Long, answers: Long): Int =
        (observations * 1.5 + answers * 1.0).toInt().coerceAtMost(100)

    fun navigationReward(latencyMs: Long): Double = when {
        latencyMs in 1L..40L -> 1.0
        latencyMs in 41L..120L -> 0.75
        latencyMs in 121L..300L -> 0.35
        latencyMs > 300L -> 0.0
        else -> 0.5
    }
}
