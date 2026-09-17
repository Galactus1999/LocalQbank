package com.localqbank.library

/** Pure, deterministic gate for future Ben model runtimes. No Android APIs or I/O. */
object BenAiRuntimeGate {
    const val CONSERVATIVE_MODEL_MB_LIMIT = 1200

    fun mayStart(enabled: Boolean, conservativeMode: Boolean, estimatedModelMb: Int): Boolean {
        if (!enabled || estimatedModelMb <= 0) return false
        return !conservativeMode || estimatedModelMb <= CONSERVATIVE_MODEL_MB_LIMIT
    }
}

/** Runtime resource governor for an optional Ben model. Pure policy; no model lifecycle ownership. */
object BenAiResourceGate {
    const val BASE_PROMPT_WINDOW_CHARS = 8_000
    const val MODERATE_PROMPT_WINDOW_CHARS = 5_000
    const val MIN_FREE_HEADROOM_MB = 512
    const val MAX_THERMAL_STATUS = 2 // LIGHT; 3+ is too hot for opportunistic model work.

    fun promptBudgetChars(thermalStatus: Int): Int = when {
        thermalStatus >= 3 -> 0
        thermalStatus == 2 -> MODERATE_PROMPT_WINDOW_CHARS
        else -> BASE_PROMPT_WINDOW_CHARS
    }

    fun mayRun(
        enabled: Boolean,
        conservativeMode: Boolean,
        estimatedModelMb: Int,
        availableMemoryMb: Int,
        thermalStatus: Int,
        powerSave: Boolean,
        foreground: Boolean
    ): Boolean {
        if (!BenAiRuntimeGate.mayStart(enabled, conservativeMode, estimatedModelMb)) return false
        if (!foreground) return false
        if (availableMemoryMb <= 0) return false
        if (availableMemoryMb < estimatedModelMb + MIN_FREE_HEADROOM_MB) return false
        if (thermalStatus > MAX_THERMAL_STATUS) return false
        // Power-save must not block a user-initiated foreground study/model interaction.
        // Background/bulk work remains denied by the foreground gate.
        return true
    }
}
