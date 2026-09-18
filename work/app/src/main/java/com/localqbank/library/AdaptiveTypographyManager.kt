package com.localqbank.library

import android.view.View

/**
 * Safe typography compatibility hook.
 *
 * v8.1.4 introduced global native auto-size across the entire view tree. On some
 * layouts that caused Android to aggressively shrink otherwise intentional text
 * sizes. For stability, typography is now opt-in/manual: existing XML and Kotlin
 * textSize values are preserved exactly. The hook remains so existing call sites
 * do not need to change.
 */
object AdaptiveTypographyManager {
    fun apply(root: View) {
        // Intentionally no-op. Never rewrite an explicitly designed text size globally.
    }
}
