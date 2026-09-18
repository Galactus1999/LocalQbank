package com.localqbank.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Home screen state holder. It coordinates existing authoritative managers but does not replace
 * DashboardManager, QBankLoadingEngine, or any other application-level owner.
 */
class MainViewModel(private val repository: MainRepository) : ViewModel() {
    private val study = MainStudyUseCase(repository)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refresh() {
        if (!AppManagers.isReady()) return
        _uiState.update { it.copy(loading = true, error = null) }
        val generation = PerformanceManager.currentGeneration()
        val experienceGeneration = AppManagers.experience.newGeneration()
        AppManagers.experience.submitHome {
            val model = runCatching { AppManagers.dashboard.build() }.getOrElse { error ->
                _uiState.update { it.copy(loading = false, error = error.message ?: "Dashboard refresh failed") }
                return@submitHome
            }
            if (generation != PerformanceManager.currentGeneration() || experienceGeneration != AppManagers.experience.currentGeneration()) return@submitHome
            val resumes = runCatching { repository.resumeTargets(model.sources) }.getOrDefault(emptyMap())
            _uiState.update {
                it.copy(
                    dashboard = model,
                    sources = model.sources,
                    sourceProgress = model.sourceProgress,
                    resumeBySource = resumes,
                    loading = false,
                    error = null
                )
            }
        }
    }

    fun prepareForDisplay() {
        if (AppManagers.isReady()) AppManagers.dashboard.prepareForDisplay()
    }

    fun flushBackup() {
        repository.flushBackup()
    }

    fun refreshSourcesFast() {
        if (!AppManagers.isReady()) return
        AppManagers.qbankLoading.loadSources { sources ->
            val resumes = runCatching { repository.resumeTargets(sources) }.getOrDefault(emptyMap())
            _uiState.update { it.copy(sources = sources, resumeBySource = resumes) }
        }
    }

    fun deleteSource(sourceId: Long) {
        repository.deleteSource(sourceId)
        AppManagers.qbankLoading.invalidate(sourceId)
        PerformanceManager.invalidate()
        refreshSourcesFast()
        refresh()
    }

    fun updateSourceMetadata(sourceId: Long, name: String, series: String): Boolean {
        val ok = repository.updateSourceMetadata(sourceId, name, series)
        if (ok) {
            AppManagers.qbankLoading.invalidate(sourceId)
            refreshSourcesFast()
            refresh()
        }
        return ok
    }


    fun currentQBankRefs(refs: List<QuestionRef>): List<QuestionRef> = study.currentQBankRefs(refs)
    fun wrongIds(refs: List<QuestionRef>): LongArray = study.wrongIds(refs)
    fun todaySolvedIds(refs: List<QuestionRef>): LongArray = study.todaySolvedIds(refs)
    fun todayRevisionIds(refs: List<QuestionRef>): LongArray = study.todayRevisionIds(refs)
    fun todayRevisionCount(refs: List<QuestionRef>): Int = study.todayRevisionCount(refs)

    fun questionExists(id: Long): Boolean = repository.questionExists(id)
    fun resumeTarget(source: Source): MainRepository.ResumeTarget? = repository.resumeTarget(source)
    fun testById(id: String): Test? = repository.testById(id)

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

class MainViewModelFactory(private val repository: MainRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) return MainViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
