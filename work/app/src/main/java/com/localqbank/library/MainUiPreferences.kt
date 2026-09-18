package com.localqbank.library

import android.content.Context

/** Small presentation-settings seam for Home UI customization. */
class MainUiPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun greetingPanelColor(): Int = prefs.getInt("greeting_panel_color", 0)
    fun quizFontFamily(): String = prefs.getString("quiz_font_family", "sans-serif") ?: "sans-serif"

    fun performanceLabThemeBackground(): Boolean = prefs.getBoolean("performance_lab_theme_background", true)
    fun setPerformanceLabThemeBackground(value: Boolean) { prefs.edit().putBoolean("performance_lab_theme_background", value).apply() }

    fun setQuizFontFamily(value: String) {
        val allowed = setOf("sans-serif", "serif", "sans-serif-condensed", "sans-serif-light")
        prefs.edit().putString("quiz_font_family", value.takeIf { it in allowed } ?: "sans-serif").apply()
    }
}
