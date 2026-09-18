package com.localqbank.library

import android.app.Activity

/** Release-safe no-op. JankStats diagnostics are debug-only. */
object JankStatsMonitor {
    fun onResumed(activity: Activity) = Unit
    fun onPaused(activity: Activity) = Unit
    fun onDestroyed(activity: Activity) = Unit
}
