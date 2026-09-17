package com.localqbank.library

/**
 * Application-level answer decision/commit seam for QuizActivity.
 * StudyStateRepository remains the authoritative study-state writer; this class only coordinates
 * the answer interaction so the Activity does not own persistence/business decisions.
 */
class QuizAnswerUseCase(
    private val progress: ProgressRepository,
    private val fallbackStudyState: StudyStateRepository
) {
    data class Result(
        val accepted: Boolean,
        val correct: Boolean,
        val stableKey: String
    )

    private fun studyState(): StudyStateRepository =
        if (AppManagers.isReady()) AppManagers.studyState else fallbackStudyState

    fun answer(question: Question, label: String, practiceMode: Boolean, examMode: Boolean): Result {
        val key = question.stableKey
        if (!practiceMode && !examMode && progress.status(key) != null) {
            return Result(accepted = false, correct = false, stableKey = key)
        }
        val selected = question.options.firstOrNull { it.label.equals(label, true) }
        val correct = selected?.correct == true ||
            question.correctAnswer?.trim()?.equals(label.trim(), true) == true ||
            question.correctAnswer?.trim()?.equals(label.trim() + ".", true) == true
        if (!examMode) {
            studyState().commitAnswer(key, label, if (correct) "correct" else "wrong")
            if (AppManagers.isReady()) AppManagers.adaptive.recordAnswer(correct)
        }
        return Result(accepted = true, correct = correct, stableKey = key)
    }
}
