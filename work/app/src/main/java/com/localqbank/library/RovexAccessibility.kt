package com.localqbank.library

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Small, dependency-light accessibility policy shared by legacy View-based screens.
 * This is intentionally not a second UI framework: it standardizes the minimum
 * semantics/touch target expected from newly touched Rovex controls.
 */
object RovexAccessibility {
    private val MIN_TOUCH_TARGET_DP = 48

    fun action(view: View, description: CharSequence? = null) {
        description?.let { view.contentDescription = it }
        view.isClickable = true
        view.isFocusable = true
        val density = view.resources.displayMetrics.density
        val min = (MIN_TOUCH_TARGET_DP * density).toInt()
        view.minimumWidth = maxOf(view.minimumWidth, min)
        view.minimumHeight = maxOf(view.minimumHeight, min)
        ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
                info.isClickable = true
            }
        })
    }
}
