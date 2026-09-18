package com.localqbank.library

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local safety layer. It never attempts to kill/restart a frozen process: Android
 * remains authoritative for ANRs/process lifecycle. We persist tiny recovery state,
 * detect stale UI heartbeats from a background thread, and enter conservative safe mode.
 */
object ResilienceManager {
    private const val PREFS = "resilience"
    private const val KEY_RUNNING = "process_running"
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_LAST_CRASH = "last_crash"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_ACTIVITY = "active_activity"
    private const val KEY_HEARTBEAT = "heartbeat_elapsed"
    private const val KEY_TEST = "quiz_test"
    private const val KEY_POS = "quiz_pos"
    private const val KEY_QKEY = "quiz_key"
    private const val KEY_SESSION = "quiz_session"
    private const val KEY_OFFER_RECOVERY = "offer_recovery"
    private const val KEY_STALLS = "suspected_stalls"

    private val installed = AtomicBoolean(false)
    private val monitor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "qbank-resilience") }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleEpoch = ActivityLifecycleEpoch()
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (activeActivity) {
                heartbeatHolder?.let { heartbeat(it) }
                mainHandler.postDelayed(this, 4000L)
            }
        }
    }
    @Volatile private var activeActivity = false
    @Volatile private var heartbeatHolder: Context? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousRunUnclean = p.getBoolean(KEY_RUNNING, false)
        if (previousRunUnclean) {
            val crashes = (p.getInt(KEY_CRASH_COUNT, 0) + 1).coerceAtMost(20)
            p.edit().putInt(KEY_CRASH_COUNT, crashes).putBoolean(KEY_RUNNING, false).putBoolean(KEY_OFFER_RECOVERY, true).apply()
            if (crashes >= 2) PerformanceManager.setSafeMode(true)
            AppEventBus.publish(AppEventBus.Event(AppEventBus.Type.SYSTEM_RECOVERED, successful = false))
        }
        p.edit().putBoolean(KEY_RUNNING, true).putLong(KEY_HEARTBEAT, SystemClock.elapsedRealtime()).apply()
        monitor.scheduleWithFixedDelay({
            if (!activeActivity) return@scheduleWithFixedDelay
            runCatching {
                val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val last = PrefsCompat.long(sp,KEY_HEARTBEAT,0L)
                if (last > 0L && SystemClock.elapsedRealtime() - last > 12000L) {
                    sp.edit().putInt(KEY_STALLS, sp.getInt(KEY_STALLS, 0) + 1).putBoolean(KEY_OFFER_RECOVERY, true).apply()
                    PerformanceManager.setSafeMode(true)
                }
            }
        }, 5, 5, TimeUnit.SECONDS)

        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_RUNNING, false)
                    .putLong(KEY_LAST_CRASH, System.currentTimeMillis())
                    .putString(KEY_LAST_ERROR, "${error.javaClass.simpleName}: ${error.message ?: ""}\n${sw.toString().take(6000)}")
                    .apply()
                PerformanceManager.setSafeMode(true)
            }
            previousHandler?.uncaughtException(thread, error)
        }
    }

    /** Persist resilience heartbeats away from the main looper. SharedPreferences.apply()
     * performs its disk write asynchronously, but the call still participates in lifecycle
     * persistence bookkeeping. A periodic watchdog must never become a source of UI stalls. */
    fun heartbeat(context: Context) {
        val app = context.applicationContext
        val elapsed = SystemClock.elapsedRealtime()
        monitor.execute {
            runCatching {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_HEARTBEAT, elapsed).apply()
            }
        }
    }

    fun activityStarted(context: Context, activity: Activity) {
        val epoch = lifecycleEpoch.advance()
        activeActivity = true
        heartbeatHolder = activity.applicationContext
        mainHandler.removeCallbacks(heartbeatRunnable)
        mainHandler.post(heartbeatRunnable)
        val app = context.applicationContext
        val activityName = activity.javaClass.simpleName
        val elapsed = SystemClock.elapsedRealtime()
        monitor.execute {
            runCatching {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_ACTIVITY, activityName).putLong(KEY_HEARTBEAT, elapsed).apply()
            }
        }
    }

    fun activityStopped(context: Context, activity: Activity) {
        val epoch = lifecycleEpoch.advance()
        activeActivity = false
        heartbeatHolder = null
        mainHandler.removeCallbacks(heartbeatRunnable)
        val app = context.applicationContext
        val activityName = activity.javaClass.simpleName
        val elapsed = SystemClock.elapsedRealtime()
        monitor.execute {
            runCatching {
                val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (p.getString(KEY_ACTIVITY, null) == activityName) {
                    p.edit().putLong(KEY_HEARTBEAT, elapsed).apply()
                }
                // Once the app has genuinely left the foreground, treat the process as a clean
                // background lifecycle so a later Android process recreation is not mislabelled as a crash.
                mainHandler.postDelayed({
                    if (!activeActivity && lifecycleEpoch.isCurrent(epoch)) {
                        monitor.execute {
                            runCatching {
                                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                    .edit().putBoolean(KEY_RUNNING, false).apply()
                            }
                        }
                    }
                }, 2500L)
            }
        }
    }

    /**
     * Quiz answers can arrive in bursts. Keep checkpoint persistence off the caller
     * thread and serialize it through the resilience executor so the UI never performs
     * preference bookkeeping during an answer transition. The checkpoint is deliberately
     * best-effort: recovery must never be allowed to block or crash the study flow.
     */
    fun checkpointQuiz(context: Context, testId: String, position: Int, stableKey: String?, session: String?) {
        if (testId.isBlank() && stableKey.isNullOrBlank()) return
        val app = context.applicationContext
        val safePosition = position.coerceAtLeast(0)
        val safeKey = stableKey ?: ""
        val safeSession = session ?: ""
        monitor.execute {
            runCatching {
                // This checkpoint is specifically for crash/process-death recovery. It is tiny,
                // so prefer commit() on the dedicated resilience executor: when this call returns,
                // the recovery cursor is on disk rather than merely queued behind apply().
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_TEST, testId).putInt(KEY_POS, safePosition)
                    .putString(KEY_QKEY, safeKey).putString(KEY_SESSION, safeSession)
                    .putLong(KEY_HEARTBEAT, SystemClock.elapsedRealtime()).commit()
            }
        }
    }

    fun recovery(context: Context): RecoveryState? = runCatching {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val test = p.getString(KEY_TEST, null).orEmpty()
        val key = p.getString(KEY_QKEY, null).orEmpty()
        if (test.isBlank() && key.isBlank()) null else RecoveryState(test, p.getInt(KEY_POS, 0), key, p.getString(KEY_SESSION, null).orEmpty())
    }.getOrNull()

    fun clearQuizRecovery(context: Context) {
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TEST).remove(KEY_POS).remove(KEY_QKEY).remove(KEY_SESSION).apply() }
    }

    fun shouldOfferRecovery(context: Context): Boolean = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFFER_RECOVERY, false) && recovery(context) != null
    fun dismissRecoveryOffer(context: Context) {
        val app = context.applicationContext
        monitor.execute {
            runCatching { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OFFER_RECOVERY, false).apply() }
        }
    }
    fun crashCount(context: Context): Int = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CRASH_COUNT, 0)
    fun suspectedStalls(context: Context): Int = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_STALLS, 0)

    fun markHealthy(context: Context) {
        val app = context.applicationContext
        monitor.execute {
            runCatching {
                val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                p.edit().putBoolean(KEY_RUNNING, true).putLong(KEY_HEARTBEAT, SystemClock.elapsedRealtime()).apply()
                if (PerformanceManager.healthScore() >= 85) PerformanceManager.setSafeMode(false)
                AppEventBus.publish(AppEventBus.Event(AppEventBus.Type.SYSTEM_RECOVERED))
            }
        }
    }

    data class RecoveryState(val testId: String, val position: Int, val stableKey: String, val session: String)
}

class LocalQBankApplication : android.app.Application() {
    private fun isInferenceProcess(): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.app.Application.getProcessName()
        } else {
            runCatching { java.io.File("/proc/${android.os.Process.myPid()}/cmdline").readText().trim() }.getOrDefault("")
        }
        return name.endsWith(":inference_process")
    }

    /** Presentation dependency graph; engines remain owned by AppManagers. */
    val appContainer: RovexAppContainer by lazy { RovexAppContainer(this) }
    /** Application-lifetime owner for the real EmbeddingGemma diagnostic; UI Activities are presentation only. */
    val isolatedEmbeddingDiagnosticCoordinator: BenIsolatedEmbeddingDiagnosticCoordinator by lazy {
        BenIsolatedEmbeddingDiagnosticCoordinator()
    }

    override fun onCreate() {
        super.onCreate()
        // Android creates Application independently in each process. Keep the inference process
        // deliberately lightweight: it must not initialize AppManagers, SQLite or UI state.
        if (isInferenceProcess()) return
        ProductionCrashReporter.initialize(this)
        RovexApplicationContextHolder.context = applicationContext
        // Debug-only guardrail: surface accidental disk/network/SQLite work on the UI
        // thread during development. Release builds are unaffected.
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedRegistrationObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        // Apply a staged full backup before any manager/database is initialized. This avoids replacing
        // SQLite files while live Activities still hold open connections.
        try { BackupManager(this, false).applyPendingFullRestore() } catch (_: Exception) { /* keep startup failure-safe; do not swallow fatal Errors */ }
        StartupCoordinator.initialize(this)
        ResilienceManager.install(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                ResilienceManager.activityStarted(this@LocalQBankApplication, activity)
                JankStatsMonitor.onResumed(activity)
                ProductionCrashReporter.screen(this@LocalQBankApplication, activity.javaClass.simpleName)
            }
            override fun onActivityPaused(activity: Activity) {
                JankStatsMonitor.onPaused(activity)
                ResilienceManager.activityStopped(this@LocalQBankApplication, activity)
            }
            override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {
                ProductionCrashReporter.breadcrumb(this@LocalQBankApplication, "ACTIVITY_CREATED:${a.javaClass.simpleName}")
            }
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: android.os.Bundle) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivityDestroyed(a: Activity) {
                JankStatsMonitor.onDestroyed(a)
                ProductionCrashReporter.breadcrumb(this@LocalQBankApplication, "ACTIVITY_DESTROYED:${a.javaClass.simpleName}")
            }
        })
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (isInferenceProcess()) return
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // Tighten the progress-backup freshness window when the UI disappears. This is
            // asynchronous and never blocks the lifecycle callback.
            runCatching { BackupManager(this).flushLocalSnapshot() }
            if (AppManagers.isReady()) runCatching { AppManagers.frankensteinSupport.trimNeuralResources() }
        }
    }

}
