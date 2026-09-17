package com.localqbank.library

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight event-driven mesh. Engines share typed signals rather than direct calls.
 * It never performs heavy work on the event-bus thread; it only records coordination state
 * and invalidates dependent caches. Feature execution remains owned by the specialist engine.
 */
class EngineMeshCoordinator(context: Context) {
    private val app = context.applicationContext
    private val events = AtomicLong(0)
    @Volatile private var last: String = "idle"
    @Volatile private var recommendation: String = "Balanced study cycle"
    private val studyListener: (StudyEventSpine.Event) -> Unit = { event ->
        events.incrementAndGet()
        last = "study:${event.name}"
        if (event.name == "database_slow") PerformanceManager.setSafeMode(true)
    }
    private val listener: (AppEventBus.Event) -> Unit = { event ->
        events.incrementAndGet()
        last = event.type.name
        when (event.type) {
            AppEventBus.Type.QUESTION_ANSWERED, AppEventBus.Type.PROGRESS_CHANGED -> {
                PerformanceManager.invalidate()
                recommendation = if (event.successful) "Reinforce with spaced flashcards" else "Review explanation and create a flashcard"
            }
            AppEventBus.Type.IMPORT_COMPLETED -> PerformanceManager.invalidate()
            AppEventBus.Type.DATABASE_SLOW -> PerformanceManager.setSafeMode(true)
            else -> Unit
        }
    }
    init {
        AppEventBus.subscribe(listener)
        StudyEventSpine.subscribe(studyListener)
    }
    fun eventCount(): Long = events.get()
    fun lastEvent(): String = last
    fun recommendation(): String = recommendation
    fun status(): String = "mesh events=${events.get()} • last=$last • next=$recommendation"
}
