package com.localqbank.library

/** User-intent operations for question notes. Storage remains owned by QuizSessionRepository/QBankDb. */
class QuizNotesUseCase(private val repository: QuizSessionRepository) {
    fun get(questionId: Long): String? = repository.note(questionId)
    fun save(questionId: Long, note: String) = repository.saveNote(questionId, note.trim())
    fun append(questionId: Long, text: String) {
        val selected = text.trim()
        if (selected.isBlank()) return
        val existing = get(questionId).orEmpty().trim()
        save(questionId, if (existing.isBlank()) selected else "$existing\n\n$selected")
    }
}
