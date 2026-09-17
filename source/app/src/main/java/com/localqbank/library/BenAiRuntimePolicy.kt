package com.localqbank.library

import android.content.Context

/**
 * User-visible governor for any future on-device Ben model runtime.
 *
 * Default is conservative and OFF: model inference must be explicitly enabled.
 * The persistent kill switch lets the user stop a problematic model without uninstalling.
 * A bounded failure circuit-breaker automatically denies repeated failing backends until the
 * user resets Ben. This class owns policy only; it never starts threads or loads model resources.
 */
class BenAiRuntimePolicy(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val enabled: Boolean
        get() = if (!prefs.getBoolean(KEY_USER_CHOICE, false)) false else prefs.getBoolean(KEY_ENABLED, false)

    val conservativeMode: Boolean
        get() = prefs.getBoolean(KEY_CONSERVATIVE, true)

    val consecutiveFailures: Int
        get() = prefs.getInt(KEY_FAILURES, 0).coerceAtLeast(0)

    enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

    val circuitState: CircuitState
        get() {
            if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) return CircuitState.CLOSED
            // A native backend that has failed repeatedly must never self-restart.
            // Only an explicit user reset may reopen neural execution.
            return CircuitState.OPEN
        }

    val circuitOpen: Boolean get() = circuitState == CircuitState.OPEN

    fun resetCircuit() {
        prefs.edit().putInt(KEY_FAILURES, 0).putLong(KEY_LAST_FAILURE_AT, 0L).apply()
    }

    fun blockReason(modelMb: Int, runtimeMb: Int, snapshot: BenAiResourceGovernor.Snapshot): String? {
        if (!enabled) return "AI accelerator is OFF"
        if (circuitOpen) return "failure circuit is open after repeated backend failures"
        if (!BenAiRuntimeGate.mayStart(enabled, conservativeMode, modelMb)) return "runtime policy rejected the ${modelMb} MB model"
        if (!snapshot.foreground) return "foreground study execution is required"
        if (snapshot.availableMemoryMb <= 0) return "available RAM could not be read"
        if (snapshot.availableMemoryMb < runtimeMb + BenAiResourceGate.MIN_FREE_HEADROOM_MB) {
            return "RAM headroom too low (${snapshot.availableMemoryMb} MB available; ${runtimeMb + BenAiResourceGate.MIN_FREE_HEADROOM_MB} MB required)"
        }
        if (snapshot.thermalStatus > BenAiResourceGate.MAX_THERMAL_STATUS) return "thermal state is too high (${snapshot.thermalStatus})"
        return null
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).putBoolean(KEY_USER_CHOICE, true).apply()
    }

    fun setConservativeMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_CONSERVATIVE, value).apply()
    }

    fun mayStartModel(estimatedModelMb: Int): Boolean =
        BenAiRuntimeGate.mayStart(enabled && circuitState != CircuitState.OPEN, conservativeMode, estimatedModelMb)

    fun recordBackendFailure() {
        prefs.edit()
            .putInt(KEY_FAILURES, (consecutiveFailures + 1).coerceAtMost(MAX_CONSECUTIVE_FAILURES))
            .putLong(KEY_LAST_FAILURE_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordBackendSuccess() {
        prefs.edit().putInt(KEY_FAILURES, 0).putLong(KEY_LAST_FAILURE_AT, 0L).apply()
    }

    fun forceStop() {
        prefs.edit().putBoolean(KEY_ENABLED, false).putBoolean(KEY_USER_CHOICE, true).putInt(KEY_FAILURES, 0).apply()
    }

    init {
        if (prefs.getString(KEY_POLICY_EPOCH, null) != POLICY_EPOCH) {
            prefs.edit().putString(KEY_POLICY_EPOCH, POLICY_EPOCH).putInt(KEY_FAILURES, 0).apply()
        }
    }

    companion object {
        private const val POLICY_EPOCH = "8.3.212-neural-quarantine-v2"
        private const val KEY_POLICY_EPOCH = "policy_epoch"
        const val CONSERVATIVE_MODEL_MB_LIMIT = BenAiRuntimeGate.CONSERVATIVE_MODEL_MB_LIMIT
        const val MAX_CONSECUTIVE_FAILURES = 3
        private const val PREFS = "ben_ai_runtime"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_USER_CHOICE = "user_choice_v2"
        private const val KEY_CONSERVATIVE = "conservative_mode"
        private const val KEY_FAILURES = "consecutive_failures"
        private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    }
}
