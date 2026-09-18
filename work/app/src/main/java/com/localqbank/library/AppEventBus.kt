package com.localqbank.library

import java.util.concurrent.CopyOnWriteArrayList

/** Typed in-process event bus. Managers communicate through events rather than hard coupling. */
object AppEventBus {
    enum class Type {
        QUESTION_OPENED, QUESTION_ANSWERED, NAVIGATION, PROGRESS_CHANGED,
        ANALYTICS_REQUESTED, DASHBOARD_OPENED, BACKUP_STARTED, BACKUP_COMPLETED,
        IMPORT_STARTED, IMPORT_COMPLETED, DATABASE_SLOW, SYSTEM_RECOVERED
    }

    data class Event(val type: Type, val latencyMs: Long = 0L, val successful: Boolean = true)

    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()

    fun subscribe(listener: (Event) -> Unit) { listeners.addIfAbsent(listener) }
    fun unsubscribe(listener: (Event) -> Unit) { listeners.remove(listener) }
    fun publish(event: Event) {
        listeners.forEach { listener -> runCatching { listener(event) } }
    }
}
