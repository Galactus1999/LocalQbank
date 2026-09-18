package com.localqbank.library

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Reads cheap device signals and delegates the decision to a pure policy.
 * It never loads, schedules, or owns an inference runtime.
 */
class BenAiResourceGovernor(context: Context) {
    private val app = context.applicationContext
    private val activityManager = app.getSystemService(ActivityManager::class.java)
    private val powerManager = app.getSystemService(PowerManager::class.java)

    data class Snapshot(
        val availableMemoryMb: Int,
        val thermalStatus: Int,
        val powerSave: Boolean,
        val foreground: Boolean = true
    )

    fun snapshot(foreground: Boolean = true): Snapshot {
        val info = ActivityManager.MemoryInfo()
        runCatching { activityManager?.getMemoryInfo(info) }
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            runCatching { powerManager?.currentThermalStatus ?: 0 }.getOrDefault(0)
        } else 0
        val save = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)
        return Snapshot((info.availMem / (1024L * 1024L)).coerceAtLeast(0L).toInt(), thermal, save, foreground)
    }

    fun mayRun(policy: BenAiRuntimePolicy, modelMb: Int, foreground: Boolean = true): Boolean {
        val s = snapshot(foreground)
        return BenAiResourceGate.mayRun(policy.enabled, policy.conservativeMode, modelMb, s.availableMemoryMb, s.thermalStatus, s.powerSave, s.foreground)
    }
}
