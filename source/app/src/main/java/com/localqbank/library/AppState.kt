package com.localqbank.library

/** Lightweight in-process invalidation bus. Screens refresh after committed state mutations.
 * Adaptive learning is deliberately asynchronous so telemetry can never add answer latency. */
object AppState {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun register(listener: () -> Unit) { listeners.add(listener) }
    fun unregister(listener: () -> Unit) { listeners.remove(listener) }
    fun changed(reason: String = "state") {
        PerformanceManager.invalidate()
        if (AppManagers.isReady()) AppManagers.analytics.invalidate()
        val eventType = when (reason) {
            "question_opened" -> AppEventBus.Type.QUESTION_OPENED
            "navigation" -> AppEventBus.Type.NAVIGATION
            "database_slow" -> AppEventBus.Type.DATABASE_SLOW
            "dashboard_opened" -> AppEventBus.Type.DASHBOARD_OPENED
            "refresh" -> AppEventBus.Type.ANALYTICS_REQUESTED
            "backup_started" -> AppEventBus.Type.BACKUP_STARTED
            "backup_completed" -> AppEventBus.Type.BACKUP_COMPLETED
            "import_started" -> AppEventBus.Type.IMPORT_STARTED
            "import_completed" -> AppEventBus.Type.IMPORT_COMPLETED
            else -> AppEventBus.Type.PROGRESS_CHANGED
        }
        AppEventBus.publish(AppEventBus.Event(eventType))
        StudyEventSpine.publishAsync(StudyEventSpine.Event(
            name = reason, source = "app_state", metadata = mapOf("event" to eventType.name)
        ))
        if (AppManagers.isReady()) AppManagers.adaptive.onEvent(reason)
        listeners.forEach { listener -> runCatching { listener() } }
    }
}
