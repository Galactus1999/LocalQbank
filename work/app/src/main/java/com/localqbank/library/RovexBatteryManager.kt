package com.localqbank.library

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicReference

/**
 * Central, non-invasive power governor for Rovex.
 *
 * This is a policy layer only: it never owns QBank/Flashcard business logic, never
 * changes Android power settings, and never blocks foreground study interactions.
 * Specialist engines may consult the policy for background/bulk work and prefetch.
 */
class RovexBatteryManager(context: Context) {
    enum class WorkClass { INTERACTIVE, BACKGROUND, BULK_IMPORT }
    enum class Level { PERFORMANCE, BALANCED, EFFICIENT, SAVER }

    data class Snapshot(
        val percent: Int = 100,
        val charging: Boolean = false,
        val powerSave: Boolean = false,
        val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
        val level: Level = Level.BALANCED
    )

    data class Policy(
        val level: Level,
        val foregroundAllowed: Boolean = true,
        val backgroundAllowed: Boolean,
        val prefetchDepth: Int,
        val imageConcurrency: Int,
        val importBatchSize: Int
    )

    private val app = context.applicationContext
    private val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val state = AtomicReference(Snapshot())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh(intent)
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initial = runCatching { app.registerReceiver(batteryReceiver, filter) }.getOrNull()
        refresh(initial)
    }

    fun snapshot(): Snapshot {
        refreshSystemState()
        return state.get()
    }

    fun policy(work: WorkClass): Policy {
        val s = snapshot()
        val backgroundAllowed = when {
            work == WorkClass.INTERACTIVE -> true
            s.level == Level.SAVER && !s.charging -> false
            else -> true
        }
        return when (s.level) {
            Level.PERFORMANCE -> Policy(s.level, true, backgroundAllowed, 3, 2, 240)
            Level.BALANCED -> Policy(s.level, true, backgroundAllowed, 2, 2, 200)
            Level.EFFICIENT -> Policy(s.level, true, backgroundAllowed, 1, 1, 120)
            Level.SAVER -> Policy(s.level, true, backgroundAllowed, 1, 1, 80)
        }
    }

    fun shouldDeferBackground(): Boolean = !policy(WorkClass.BACKGROUND).backgroundAllowed
    fun recommendedPrefetchDepth(): Int = policy(WorkClass.INTERACTIVE).prefetchDepth
    fun recommendedImageConcurrency(): Int = policy(WorkClass.INTERACTIVE).imageConcurrency
    fun recommendedImportBatchSize(): Int = policy(WorkClass.BULK_IMPORT).importBatchSize
    fun recommendedBackgroundBatchSize(): Int = policy(WorkClass.BACKGROUND).importBatchSize

    fun statusLine(): String {
        val s = snapshot()
        val thermal = when (s.thermalStatus) {
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            else -> "normal"
        }
        return "${s.percent}% • ${if (s.charging) "charging" else "battery"} • ${s.level.name.lowercase()} • thermal $thermal"
    }

    private fun refresh(intent: Intent?) {
        runCatching {
            val previous = state.get()
            val i = intent
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: previous.percent
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else previous.percent
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: if (previous.charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val save = power?.isPowerSaveMode == true
            val thermal = if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE else PowerManager.THERMAL_STATUS_NONE
            state.set(Snapshot(percent, charging, save, thermal, classify(percent, charging, save, thermal)))
        }
    }

    private fun refreshSystemState() {
        runCatching {
            val previous = state.get()
            val save = power?.isPowerSaveMode == true
            val thermal = if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE else PowerManager.THERMAL_STATUS_NONE
            state.set(previous.copy(powerSave = save, thermalStatus = thermal,
                level = classify(previous.percent, previous.charging, save, thermal)))
        }
    }

    private fun classify(percent: Int, charging: Boolean, save: Boolean, thermal: Int): Level = when {
        thermal >= PowerManager.THERMAL_STATUS_SEVERE -> Level.SAVER
        save || (!charging && percent < 10) -> Level.SAVER
        !charging && percent < 20 -> Level.EFFICIENT
        !charging && percent < 50 -> Level.BALANCED
        charging && percent >= 50 && thermal <= PowerManager.THERMAL_STATUS_LIGHT -> Level.PERFORMANCE
        else -> Level.BALANCED
    }
}
