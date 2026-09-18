package com.localqbank.library

import android.content.Context
import android.os.Build
import android.os.PowerManager

/** Small read-only health facade for the intelligence console. Thermal state is observed here and
 * consumed by Ben's resource policy; this class does not own inference scheduling. */
class EngineHealthManager(context: Context) {
    private val app = context.applicationContext
    private val powerManager = app.getSystemService(PowerManager::class.java)
    @Volatile private var observedThermalStatus = currentThermalStatus()

    private val thermalListener = if (Build.VERSION.SDK_INT >= 29) {
        PowerManager.OnThermalStatusChangedListener { status -> observedThermalStatus = status }
    } else null

    init {
        if (Build.VERSION.SDK_INT >= 29) {
            thermalListener?.also { listener ->
                runCatching { powerManager?.addThermalStatusListener(app.mainExecutor, listener) }
            }
        }
    }

    data class Snapshot(
        val overall: Int,
        val safeMode: Boolean,
        val crashCount: Int,
        val lastError: String?,
        val recovery: ResilienceManager.RecoveryState?,
        val thermalStatus: Int = 0,
        val thermalSevere: Boolean = false,
    )

    fun snapshot(): Snapshot {
        val p = app.getSharedPreferences("resilience", Context.MODE_PRIVATE)
        val thermal = observedThermalStatus
        return Snapshot(
            overall = PerformanceManager.healthScore(),
            safeMode = PerformanceManager.isSafeMode(),
            crashCount = p.getInt("crash_count", 0),
            lastError = p.getString("last_error", null),
            recovery = ResilienceManager.recovery(app),
            thermalStatus = thermal,
            thermalSevere = thermal >= PowerManager.THERMAL_STATUS_SEVERE,
        )
    }

    private fun currentThermalStatus(): Int = if (Build.VERSION.SDK_INT >= 29) {
        runCatching { powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
    } else PowerManager.THERMAL_STATUS_NONE
}
