package com.localqbank.library

import android.content.Context

/** Small presentation-settings seam; keeps SharedPreferences out of QuizActivity. */
class QuizUiPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun fontScale(): Float = PrefsCompat.float(prefs, "quiz_font_scale", 1f).coerceIn(.85f, 1.30f)
    fun fontFamily(): String = prefs.getString("quiz_font_family", "sans-serif") ?: "sans-serif"
    fun headerMm(): Float = PrefsCompat.float(prefs, "quiz_header_mm", 10f).coerceIn(7f, 13f)

    fun setFontScale(value: Float) = prefs.edit().putFloat("quiz_font_scale", value.coerceIn(.85f, 1.30f)).apply()
    fun setFontFamily(value: String) = prefs.edit().putString("quiz_font_family", value).apply()
    fun setHeaderMm(value: Float) = prefs.edit().putFloat("quiz_header_mm", value.coerceIn(7f, 13f)).apply()
}
