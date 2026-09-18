package com.localqbank.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Screen-level state seam for Settings. It deliberately delegates application behavior to the
 * existing authoritative managers; it is not a replacement for any engine/manager.
 */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(repository.snapshot())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() { _uiState.value = repository.snapshot() }
    fun setTheme(theme: String) { repository.setTheme(theme); refresh() }
    fun quizFont(): String = repository.quizFont()
    fun flowColorOne(): Int = repository.flowColorOne()
    fun flowColorTwo(): Int = repository.flowColorTwo()
    fun flowGreetingColor(): Int = repository.flowGreetingColor()
    fun flowTextEnabled(): Boolean = repository.flowTextEnabled()
    fun setQuizFont(font: String) { repository.setQuizFont(font); refresh() }
    fun resetColourFlow() { repository.resetColourFlow(); refresh() }
    fun saveColourFlow(c1: Int, c2: Int, enabled: Boolean, greeting: Int) {
        repository.saveColourFlow(c1, c2, enabled, greeting); refresh()
    }
    fun setAdaptiveAutonomy(enabled: Boolean) { AppManagers.adaptive.setAutonomyEnabled(enabled); refresh() }
    fun resetAdaptiveLearning() { AppManagers.adaptive.resetLearning(); refresh() }
}

data class SettingsUiState(
    val theme: String,
    val quizFont: String,
    val adaptiveAutonomy: Boolean,
    val adaptiveSafeMode: Boolean,
    val benEnabled: Boolean,
    val benCircuitOpen: Boolean
)

class SettingsRepository(context: Context) {
    private val app = context.applicationContext
    private val ui = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun snapshot(): SettingsUiState {
        val policy = BenAiRuntimePolicy(app)
        return SettingsUiState(
            theme = ThemeManager.get(app),
            quizFont = quizFont(),
            adaptiveAutonomy = AppManagers.isReady() && AppManagers.adaptive.isAutonomyEnabled(),
            adaptiveSafeMode = AppManagers.isReady() && AppManagers.adaptive.state().safeMode,
            benEnabled = policy.enabled,
            benCircuitOpen = policy.circuitOpen
        )
    }
    fun setTheme(theme: String) = ThemeManager.set(app, theme)
    fun quizFont(): String = ui.getString("quiz_font_family", "sans-serif") ?: "sans-serif"
    fun flowColorOne(): Int = ui.getInt("flow_text_color_1", RovexColorFlowTextView.colorOne(app))
    fun flowColorTwo(): Int = ui.getInt("flow_text_color_2", RovexColorFlowTextView.colorTwo(app))
    fun flowGreetingColor(): Int = ui.getInt("greeting_panel_color", 0)
    fun flowTextEnabled(): Boolean = ui.getBoolean("flow_text_enabled", true)
    fun setQuizFont(font: String) { ui.edit().putString("quiz_font_family", font).apply() }
    fun resetColourFlow() { ui.edit().remove("flow_text_color_1").remove("flow_text_color_2").remove("flow_text_enabled").remove("greeting_panel_color").apply() }
    fun saveColourFlow(c1: Int, c2: Int, enabled: Boolean, greeting: Int) {
        ui.edit().putInt("flow_text_color_1", c1).putInt("flow_text_color_2", c2).putBoolean("flow_text_enabled", enabled).putInt("greeting_panel_color", greeting).apply()
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return SettingsViewModel(repository) as T
    }
}
