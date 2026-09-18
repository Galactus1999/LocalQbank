package com.localqbank.library

import kotlin.math.abs

/**
 * Phase-5 contextual SRS shadow policy. It recommends a scheduling posture from card context
 * without changing the authoritative deterministic scheduler. This is intentionally pure and
 * can be evaluated/benchmarked before any future actuation is permitted.
 */
class ContextualSrsShadowPolicy {
    enum class Action { CONSERVATIVE, BALANCED, ACCELERATED }

    data class Context(
        val intervalDays: Int,
        val easeFactor: Float,
        val repetitions: Int,
        val lapses: Int,
        val overdueDays: Int
    )

    data class Recommendation(val action: Action, val confidence: Int, val reason: String)

    fun recommend(context: Context): Recommendation {
        val lapseRisk = (context.lapses * 18).coerceAtMost(54)
        val overdueRisk = (context.overdueDays * 10).coerceAtMost(30)
        val lowEase = ((2.5f - context.easeFactor) * 45f).toInt().coerceIn(0, 35)
        val weakness = (lapseRisk + overdueRisk + lowEase).coerceIn(0, 100)
        return when {
            weakness >= 55 -> Recommendation(Action.CONSERVATIVE, (55 + weakness / 2).coerceAtMost(95), "High lapse/overdue or low-ease risk")
            weakness <= 15 && context.repetitions >= 3 && context.intervalDays >= 7 -> Recommendation(Action.ACCELERATED, (70 + (15 - weakness) * 2).coerceAtMost(95), "Stable recall profile with low observed risk")
            else -> Recommendation(Action.BALANCED, (60 + abs(50 - weakness) / 3).coerceAtMost(90), "No strong reason to depart from the balanced schedule")
        }
    }
}
