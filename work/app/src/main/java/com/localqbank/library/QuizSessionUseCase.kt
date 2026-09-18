package com.localqbank.library

import android.content.Context

/** Coordinates quiz cursor persistence and crash-recovery checkpointing. */
class QuizSessionUseCase(context: Context) {
    private val app = context.applicationContext

    fun sessionKey(label: String?, title: String, testId: String, collectionMode: Boolean): String =
        (label ?: title.ifBlank { testId }).take(120) + ":" + (if (collectionMode) "collection" else "test")

    fun saveCursor(sessionKey: String, testId: String, position: Int, stableKey: String?) {
        if (AppManagers.isReady()) AppManagers.sessionStore.save(sessionKey, testId, position, stableKey)
    }

    fun checkpoint(testId: String, position: Int, stableKey: String?, sessionLabel: String?) {
        ResilienceManager.checkpointQuiz(app, testId, position, stableKey, sessionLabel)
        StudyEventSpine.publishAsync(
            StudyEventSpine.Event(
                "quiz_checkpoint", stableKey, "quiz",
                metadata = mapOf("position" to position.toString())
            )
        )
    }
}
