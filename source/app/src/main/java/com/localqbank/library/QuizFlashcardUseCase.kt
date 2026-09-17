package com.localqbank.library

/** Explicit user-triggered flashcard action from the quiz presentation. */
class QuizFlashcardUseCase {
    fun createFromQuestion(questionId: Long, contextLabel: String, onResult: (FlashcardIntelligenceManager.Result) -> Unit) {
        AppManagers.flashcardIntelligence.createFromQuestionsAsync(longArrayOf(questionId), contextLabel, onResult)
    }
}
