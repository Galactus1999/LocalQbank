package com.localqbank.library

import android.view.MotionEvent
import android.view.View

/** Noticeable but bounded touch feedback; never changes layout bounds. */
object AliveMotion {
    fun install(view: View) {
        view.isHapticFeedbackEnabled = true
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(0.955f).scaleY(0.955f).translationY(3f * v.resources.displayMetrics.density).setDuration(90L).start()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(180L).start()
                    false
                }
                else -> false
            }
        }
    }
}
