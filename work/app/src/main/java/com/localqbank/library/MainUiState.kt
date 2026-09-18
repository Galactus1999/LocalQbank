package com.localqbank.library

/** Single observable presentation state for the Home screen. */
data class MainUiState(
    val dashboard: DashboardManager.DashboardModel? = null,
    val sources: List<Source> = emptyList(),
    val sourceProgress: Map<Long, ProgressSummary> = emptyMap(),
    val resumeBySource: Map<Long, MainRepository.ResumeTarget> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
)
