package com.localqbank.library

import android.content.Context
import android.provider.Settings

/** Central animation gate. Respects the user's Android animation-scale accessibility setting. */
object AnimationPolicy {
    fun enabled(context: Context): Boolean {
        val resolver = context.contentResolver
        return runCatching {
            val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            animator > 0f && transition > 0f
        }.getOrDefault(true)
    }
}
