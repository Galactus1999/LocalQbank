package com.localqbank.library.core.observability

import android.content.Context
import android.os.SystemClock
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central, privacy-bounded observability seam.
 *
 * Rules:
 * - never accept question/answer/note/API-key payloads;
 * - only bounded stage/screen/event metadata is emitted;
 * - Crashlytics remains the production sink while local resilience remains authoritative;
 * - the in-memory breadcrumb ring is deliberately tiny and process-local.
 */
object RovexObservability {
    private const val MAX_BREADCRUMBS = 32
    private const val MAX_VALUE = 120
    private val installed = AtomicBoolean(false)
    private val lock = Any()
    private val breadcrumbs = ArrayDeque<String>(MAX_BREADCRUMBS)
    @Volatile private var currentScreen = "unknown"
    @Volatile private var currentStage = "startup"
    private var startElapsed = 0L

    fun initialize(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        startElapsed = SystemClock.elapsedRealtime()
        val app = context.applicationContext
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            c.setCustomKey("rovex_observability", true)
            c.setCustomKey("rovex_process", processName())
        }
        breadcrumb(app, "OBSERVABILITY_READY")
    }

    fun screen(context: Context, name: String) {
        currentScreen = sanitize(name)
        event(context, "SCREEN", currentScreen)
    }

    fun stage(context: Context, stage: String, detail: String = "") {
        currentStage = sanitize(stage)
        val suffix = detail.takeIf { it.isNotBlank() }?.let { ":${sanitize(it)}" }.orEmpty()
        breadcrumb(context, "STAGE:${currentStage}${suffix}")
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            c.setCustomKey("rovex_stage", currentStage)
            c.setCustomKey("rovex_screen", currentScreen)
            c.setCustomKey("rovex_uptime_ms", (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(0L))
        }
    }

    fun event(context: Context, name: String, detail: String = "") {
        breadcrumb(context, "EVENT:${sanitize(name)}${detail.takeIf { it.isNotBlank() }?.let { ":${sanitize(it)}" }.orEmpty()}")
    }

    fun failure(context: Context, stage: String, throwable: Throwable, detail: String = "") {
        stage(context, stage, detail)
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            c.setCustomKey("rovex_failure_stage", sanitize(stage))
            c.recordException(throwable)
        }
        breadcrumb(context, "FAILURE:${sanitize(stage)}:${throwable.javaClass.simpleName}")
    }

    fun breadcrumb(context: Context, value: String) {
        val safe = sanitize(value)
        synchronized(lock) {
            if (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.removeFirst()
            breadcrumbs.addLast(safe)
        }
        runCatching { FirebaseCrashlytics.getInstance().log(safe) }
    }

    fun snapshot(): List<String> = synchronized(lock) { breadcrumbs.toList() }

    fun currentScreen(): String = currentScreen
    fun currentStage(): String = currentStage

    private fun processName(): String = if (android.os.Build.VERSION.SDK_INT >= 28) {
        android.app.Application.getProcessName()
    } else {
        runCatching { java.io.File("/proc/${android.os.Process.myPid()}/cmdline").readText().trim() }.getOrDefault("")
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_VALUE)
}
