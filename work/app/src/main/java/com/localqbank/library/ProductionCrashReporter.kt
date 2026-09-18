package com.localqbank.library

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.localqbank.library.core.observability.RovexObservability
import java.util.concurrent.atomic.AtomicBoolean

/** Privacy-bounded production diagnostics. Never records question text, answers, notes, or API keys. */
object ProductionCrashReporter {
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        RovexApplicationContextHolder.context = app
        RovexObservability.initialize(app)
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            c.setCustomKey("rovex_version", info.versionName ?: "unknown")
            c.setCustomKey("rovex_version_code", info.longVersionCode)
            c.setCustomKey("sdk", android.os.Build.VERSION.SDK_INT)
            c.setCustomKey("device", android.os.Build.MODEL.take(80))
            c.setCustomKey("ben_inference_process", processName().endsWith(":inference_process"))
        }
    }

    private fun processName(): String = if (android.os.Build.VERSION.SDK_INT >= 28) {
        android.app.Application.getProcessName()
    } else {
        runCatching { java.io.File("/proc/${android.os.Process.myPid()}/cmdline").readText().trim() }.getOrDefault("")
    }

    fun screen(context: Context, activityName: String) = RovexObservability.screen(context, activityName)

    fun stage(context: Context, stage: String, detail: String = "") = RovexObservability.stage(context, stage, detail)

    fun breadcrumb(context: Context, value: String) = RovexObservability.breadcrumb(context, value)

    fun recordNonFatal(throwable: Throwable, stage: String, details: Map<String, String> = emptyMap()) {
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            c.setCustomKey("rovex_stage", stage.take(80))
            details.entries.take(12).forEach { (k, v) ->
                if (k.length <= 40) c.setCustomKey(k, v.take(120))
            }
            c.recordException(throwable)
        }
        RovexObservability.failure(RovexApplicationContextHolder.context, stage, throwable)
    }
}

/** Application context holder used only for non-fatal reporting APIs that do not receive Context. */
object RovexApplicationContextHolder {
    @Volatile lateinit var context: Context
}
