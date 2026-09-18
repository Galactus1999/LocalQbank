package com.localqbank.library

import android.app.Activity
import android.util.Log
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** Debug-only UI performance telemetry. */
object JankStatsMonitor {
    private const val TAG = "RovexJank"
    private val stats = Collections.synchronizedMap(WeakHashMap<Activity, JankStats>())
    private val jankCounts = Collections.synchronizedMap(WeakHashMap<Activity, Int>())

    fun onResumed(activity: Activity) {
        val existing = stats[activity]
        if (existing != null) {
            existing.isTrackingEnabled = true
            return
        }
        runCatching {
            // Never let the JankStats listener strongly retain its Activity. A strong
            // listener capture would defeat WeakHashMap's weak-key behavior.
            val activityRef = WeakReference(activity)
            val jank = JankStats.createAndTrack(activity.window) { frame ->
                val owner = activityRef.get() ?: return@createAndTrack
                if (frame.isJank) jankCounts[owner] = (jankCounts[owner] ?: 0) + 1
            }
            stats[activity] = jank
            activity.window.decorView?.let { root ->
                PerformanceMetricsState.getHolderForHierarchy(root).state?.putState("Activity", activity.javaClass.simpleName)
            }
        }.onFailure { Log.d(TAG, "JankStats unavailable for ${activity.javaClass.simpleName}", it) }
    }

    fun onPaused(activity: Activity) {
        stats[activity]?.isTrackingEnabled = false
        val count = jankCounts.remove(activity) ?: 0
        if (count > 0) Log.d(TAG, "${activity.javaClass.simpleName}: $count janky frames")
    }

    fun onDestroyed(activity: Activity) {
        stats.remove(activity)
        jankCounts.remove(activity)
    }
}
