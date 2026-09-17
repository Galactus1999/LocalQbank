package com.localqbank.library

/** Explicit quiz-to-Knowledge-Vault actions. */
class QuizKnowledgeUseCase {
    fun captureQuestion(question: Question, reason: String, onDone: (KnowledgeEngineManager.Result) -> Unit) {
        AppManagers.knowledge.captureQuestionAsync(question, reason, onDone)
    }

    fun saveImage(raw: String, questionId: Long, onDone: (Boolean) -> Unit) {
        AppManagers.knowledge.saveImageAsync(raw, questionId, onDone)
    }

    fun saveImageWithNote(raw: String, questionId: Long, note: String, onDone: (Boolean) -> Unit) {
        AppManagers.knowledge.saveImageWithNoteAsync(raw, questionId, note, onDone)
    }
}
