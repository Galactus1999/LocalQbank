package com.localqbank.library

import android.os.SystemClock
import android.view.View

/**
 * Central interaction guard for high-value actions. Prevents accidental double-submit while
 * keeping the UI thread completely non-blocking. It is intentionally not a business-logic owner.
 */
object UiActionGuard {
    private const val DEFAULT_WINDOW_MS = 450L

    fun View.setGuardedClick(windowMs: Long = DEFAULT_WINDOW_MS, action: () -> Unit) {
        var lastClick = Long.MIN_VALUE
        setOnClickListener {
            val now = SystemClock.uptimeMillis()
            if (now - lastClick < windowMs) return@setOnClickListener
            lastClick = now
            action()
        }
    }
}
