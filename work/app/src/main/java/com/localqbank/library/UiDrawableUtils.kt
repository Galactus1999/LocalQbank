package com.localqbank.library

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** Shared, presentation-only drawable helpers used by Home UI components. */
internal object UiDrawableUtils {
    fun roundedDrawable(context: Context, color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * context.resources.displayMetrics.density
        }
}

