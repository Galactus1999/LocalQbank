package com.localqbank.library

/**
 * Lifecycle-safe state owned by QuizViewModel rather than the Activity instance.
 *
 * The Activity may render and mutate this state through ViewModel commands during the
 * incremental migration, but the state survives configuration changes and is no longer
 * coupled to an Activity object.
 */
class QuizUiState {
    var testId: String = ""
    var title: String = "QBank"
    var questionCount: Int = 0
    var currentQuestion: Question? = null
    var sourceId: Long = 0L
    var position: Int = 0
    var sectionLabel: String? = null
    var practiceMode: Boolean = false
    var practiceAnsweredKey: String? = null
    var collectionMode: Boolean = false
    var collectionQuestionIds: LongArray = longArrayOf()
    var bankName: String = "QBank"
    var examMode: Boolean = false
    var examDurationMinutes: Int = 0
    var examRemainingMs: Long = 0L
    var sessionLabel: String? = null
    val examAnswers: LinkedHashMap<String, Boolean> = linkedMapOf()

    fun resetForNewSession() {
        currentQuestion = null
        practiceAnsweredKey = null
        examRemainingMs = 0L
        examAnswers.clear()
    }
}
