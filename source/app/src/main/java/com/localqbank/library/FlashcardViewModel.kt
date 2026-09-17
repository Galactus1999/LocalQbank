package com.localqbank.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update

/** UI state owned by FlashcardViewModel; contains no Android Views or Context. */
data class FlashcardUiState(
    val decks: List<FlashcardDb.Deck> = emptyList(),
    val reviewCount: Int = 0,
    val bookmarkCount: Int = 0,
    val dueStats: FlashcardDb.DueStats = FlashcardDb.DueStats(0, 0, 0, 0, 0),
    val reviewedToday: Int = 0,
    val totalReviews: Int = 0,
    val allCardCount: Int = 0,
    val dailyNewLimit: Int = 30,
    val dailyReviewLimit: Int = 200,
    val settings: Map<String, String> = emptyMap(),
    val modeCounts: Map<String, Int> = emptyMap(),
    val deckDueStats: Map<Long, FlashcardDb.DueStats> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null
)

class FlashcardViewModel(private val repository: FlashcardReviewRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FlashcardUiState())
    val uiState: StateFlow<FlashcardUiState> = _uiState.asStateFlow()

    /**
     * The Activity can request refreshes from both onResume and lifecycle collectors.
     * Serialize repository operations so a single SQLite handle is never driven by overlapping
     * screen operations during rapid lifecycle transitions or repeated user actions.
     */
    private val repositoryMutex = Mutex()

    private suspend fun readState(): FlashcardUiState {
        val decks = repository.decks()
        return FlashcardUiState(
            decks = decks,
            reviewCount = repository.reviewCount(),
            bookmarkCount = repository.bookmarkCount(),
            dueStats = repository.dueStatsAll(),
            reviewedToday = repository.reviewedToday(),
            totalReviews = repository.totalReviews(),
            allCardCount = repository.modeCount("all"),
            dailyNewLimit = repository.settingInt("new_per_day", 30),
            dailyReviewLimit = repository.settingInt("max_reviews_per_day", 200),
            settings = repository.settingsSnapshot(),
            modeCounts = listOf("due", "all", "unseen", "hard", "again", "bookmarked", "marked")
                .associateWith { mode -> repository.modeCount(mode) },
            deckDueStats = decks.associate { deck -> deck.id to repository.dueStats(deck.id) },
            loading = false,
            error = null
        )
    }

    private fun setLoading() {
        _uiState.update { it.copy(loading = true, error = null) }
    }

    fun refresh() {
        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repositoryMutex.withLock { readState() }
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun saveSettings(values: Map<String, String>) {
        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repositoryMutex.withLock {
                    values.forEach { (key, value) -> repository.setSetting(key, value) }
                    readState()
                }
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun unsuspendDeck(deckId: Long, includeChildren: Boolean, callback: (Int) -> Unit) {
        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repositoryMutex.withLock {
                    val count = repository.unsuspendDeck(deckId, includeChildren)
                    count to readState()
                }
            }.onSuccess { (count, state) ->
                _uiState.value = state
                withContext(Dispatchers.Main.immediate) { callback(count) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun deleteDeck(deckId: Long, includeSubdecks: Boolean, callback: (Int) -> Unit) {
        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repositoryMutex.withLock {
                    val count = repository.deleteDeck(deckId, includeSubdecks)
                    count to readState()
                }
            }.onSuccess { (count, state) ->
                _uiState.value = state
                withContext(Dispatchers.Main.immediate) { callback(count) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun deleteDeckTree(name: String, callback: (Int) -> Unit) {
        setLoading()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repositoryMutex.withLock {
                    val count = repository.deleteDeckTree(name)
                    count to readState()
                }
            }.onSuccess { (count, state) ->
                _uiState.value = state
                withContext(Dispatchers.Main.immediate) { callback(count) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

class FlashcardViewModelFactory(private val repository: FlashcardReviewRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FlashcardViewModel::class.java))
        return FlashcardViewModel(repository) as T
    }
}
