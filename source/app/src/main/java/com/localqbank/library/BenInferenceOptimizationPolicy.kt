package com.localqbank.library

/**
 * Phase-6 optimization gate. Pure/advisory: it never loads, trims, or replaces a model.
 * Decisions are based on measured runtime behavior and device safety signals so optimization
 * cannot silently override the resource governor.
 */
object BenInferenceOptimizationPolicy {
    enum class Action { KEEP_WARM, TRIM_AFTER_REQUEST, DISABLE_NEURAL }

    data class Sample(
        val coldInitMs: Long,
        val warmInferenceMs: Long,
        val availableRamMb: Int,
        val thermalStatus: Int,
        val repeatedFailures: Int,
        val governorAllowsNeural: Boolean
    )

    data class Decision(val action: Action, val reason: String)

    fun decide(sample: Sample): Decision {
        if (!sample.governorAllowsNeural) return Decision(Action.DISABLE_NEURAL, "Resource governor denies neural execution")
        if (sample.repeatedFailures >= 3) return Decision(Action.DISABLE_NEURAL, "Repeated neural failures require a safe fallback")
        if (sample.availableRamMb < 512) return Decision(Action.TRIM_AFTER_REQUEST, "Low available RAM favors releasing warm runtime")
        if (sample.thermalStatus >= 4) return Decision(Action.TRIM_AFTER_REQUEST, "High thermal status favors releasing warm runtime")
        if (sample.coldInitMs >= 10_000L && sample.warmInferenceMs <= 500L) return Decision(Action.KEEP_WARM, "Warm inference is fast enough to justify avoiding repeated cold initialization")
        return Decision(Action.TRIM_AFTER_REQUEST, "No measured evidence yet that keeping the runtime warm is beneficial")
    }
}
