package com.localqbank.library


/**
 * Presentation-lifecycle bridge for QuizActivity.
 *
 * It owns no quiz/business state. It only sequences the existing ViewModel persistence
 * seams when the Android Activity enters/leaves the foreground.
 */
class QuizSessionLifecycleController(
    private val viewModel: QuizViewModel,
    private val activity: android.app.Activity,
    private val recordCurrentTime: () -> Unit
) {
    fun onPause() {
        recordCurrentTime()
        viewModel.persistPosition()
        runCatching { viewModel.persistSessionCursor() }
        viewModel.checkpointSession()
    }

    fun onStop() {
        viewModel.checkpointSession()
        ResilienceManager.activityStopped(activity, activity)
        BackupManager(activity).flushCloudBackup()
    }

    fun onResume() {
        ResilienceManager.activityStarted(activity, activity)
        ResilienceManager.heartbeat(activity)
    }
}
