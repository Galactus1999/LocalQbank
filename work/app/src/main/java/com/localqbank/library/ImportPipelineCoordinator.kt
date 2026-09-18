package com.localqbank.library

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Serial, resumable import coordinator. Existing importers remain the parser of record. */
class ImportPipelineCoordinator(context: Context) {
    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "qbank-import") }
    private val running = AtomicBoolean(false)

    fun enqueue(sourceName: String, bytes: Long, work: () -> Unit, onSuccess: () -> Unit = {}, onFailure: (Throwable) -> Unit = {}): Boolean {
        if (!ImportSecurityPolicy.acceptsSize(bytes) || !running.compareAndSet(false, true)) return false
        executor.execute {
            val started = System.currentTimeMillis()
            AppEventBus.publish(AppEventBus.Event(AppEventBus.Type.IMPORT_STARTED))
            StudyEventSpine.publishAsync(StudyEventSpine.Event("import_started", source = "import", metadata = mapOf("name" to sourceName.take(120))))
            val result = runCatching { work() }
            // Release the coordinator before invoking completion callbacks. Those callbacks may
            // immediately enqueue the next selected file; keeping the gate closed here would
            // reject that legitimate next job and silently stall multi-file imports.
            running.set(false)
            result
                .onSuccess {
                    AppEventBus.publish(AppEventBus.Event(AppEventBus.Type.IMPORT_COMPLETED, latencyMs = System.currentTimeMillis() - started))
                    StudyEventSpine.publishAsync(StudyEventSpine.Event("import_completed", source = "import"))
                    onSuccess()
                }
                .onFailure { error ->
                    StudyEventSpine.publishAsync(StudyEventSpine.Event("import_failed", source = "import", metadata = mapOf("error" to (error.message ?: "unknown").take(160))))
                    onFailure(error)
                }
        }
        return true
    }

    /** Test-only lifecycle hook; AppManagers owns the production coordinator. */
    internal fun closeForTest() {
        running.set(false)
        executor.shutdownNow()
    }
}
