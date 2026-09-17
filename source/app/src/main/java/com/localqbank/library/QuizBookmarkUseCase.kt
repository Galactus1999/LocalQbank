package com.localqbank.library

/** Bookmark intent boundary. StudyStateRepository/ProgressRepository remain authoritative. */
class QuizBookmarkUseCase(
    private val progress: ProgressRepository,
    private val adaptiveEvent: (() -> Unit)? = null
) {
    fun current(stableKey: String): String? = progress.bookmark(stableKey)
    fun set(stableKey: String, value: String?) {
        progress.setBookmark(stableKey, value)
        adaptiveEvent?.invoke()
    }

    fun mistakeType(stableKey: String): String? = progress.mistakeType(stableKey)
    fun setMistakeType(stableKey: String, value: String?) = progress.setMistakeType(stableKey, value)
}
