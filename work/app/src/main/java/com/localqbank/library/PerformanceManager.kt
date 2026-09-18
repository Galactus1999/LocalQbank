package com.localqbank.library

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import android.os.Process
import java.util.concurrent.atomic.AtomicLong

/**
 * Runtime performance coordinator.
 *
 * Rules:
 * 1. Never perform a full-QBank scan on the main thread.
 * 2. Read SharedPreferences once per snapshot, never once per question.
 * 3. Coalesce repeated refresh requests while a calculation is running.
 * 4. Keep derived state in memory and invalidate it only after a real mutation.
 */
object PerformanceManager {
    private const val DEFAULT_CACHE_TTL_MS = 5_000L
    @Volatile private var adaptiveCacheTtlMs = DEFAULT_CACHE_TTL_MS
    @Volatile private var adaptivePrefetchDepth = 2
    @Volatile private var healthScore = 100
    @Volatile private var safeMode = false
    @Volatile private var preSafePrefetch = 2
    @Volatile private var preSafeCacheTtlMs = DEFAULT_CACHE_TTL_MS
    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "qbank-worker").apply { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
    }
    private val generation = AtomicLong(0)
    private val refsLock = Any()
    private val lightRefsLock = Any()
    private val progressLock = Any()
    @Volatile private var refsCache: List<QuestionRef>? = null
    @Volatile private var refsAt = 0L
    @Volatile private var lightRefsCache: List<QuestionRef>? = null
    @Volatile private var lightRefsAt = 0L
    @Volatile private var progressCache: ProgressSnapshot? = null
    @Volatile private var progressAt = 0L

    fun invalidate() {
        generation.incrementAndGet()
        refsCache = null
        lightRefsCache = null
        progressCache = null
        refsAt = 0L
        lightRefsAt = 0L
        progressAt = 0L
    }

    /** Refresh progress-derived UI without discarding the expensive immutable question index. */
    fun invalidateProgress() {
        generation.incrementAndGet()
        progressCache = null
        progressAt = 0L
    }

    fun submit(task: () -> Unit): Future<*> = executor.submit(task)

    fun refs(context: Context): List<QuestionRef> {
        val now = System.currentTimeMillis()
        refsCache?.let { if (now - refsAt < adaptiveCacheTtlMs) return it }
        synchronized(refsLock) {
            val secondNow = System.currentTimeMillis()
            refsCache?.let { if (secondNow - refsAt < adaptiveCacheTtlMs) return it }
            val db = QBankDb(context.applicationContext)
            return try {
                val value = db.allQuestionRefs()
                refsCache = value
                refsAt = secondNow
                value
            } finally { db.close() }
        }
    }

    /** Lightweight index for home/dashboard work: no question HTML/text is loaded. */
    fun lightRefs(context: Context): List<QuestionRef> {
        val now = System.currentTimeMillis()
        lightRefsCache?.let { if (now - lightRefsAt < adaptiveCacheTtlMs) return it }
        synchronized(lightRefsLock) {
            val secondNow = System.currentTimeMillis()
            lightRefsCache?.let { if (secondNow - lightRefsAt < adaptiveCacheTtlMs) return it }
            val db = QBankDb(context.applicationContext)
            return try {
                val value = db.allQuestionRefs(lightweight = true)
                lightRefsCache = value
                lightRefsAt = secondNow
                value
            } finally { db.close() }
        }
    }

    fun progress(context: Context): ProgressSnapshot {
        val now = System.currentTimeMillis()
        progressCache?.let { if (now - progressAt < adaptiveCacheTtlMs) return it }
        synchronized(progressLock) {
            val secondNow = System.currentTimeMillis()
            progressCache?.let { if (secondNow - progressAt < adaptiveCacheTtlMs) return it }
            val store = ProgressStore(context.applicationContext)
            val p = try { store.allEntries() } finally { store.close() }
            val snapshot = ProgressSnapshot.from(p)
            progressCache = snapshot
            progressAt = secondNow
            return snapshot
        }
    }

    fun currentGeneration(): Long = generation.get()

    /** Runtime knobs exposed to the adaptive controller. Bounds are deliberately narrow. */
    fun setAdaptivePolicy(prefetchDepth: Int? = null, cacheTtlMs: Long? = null) {
        prefetchDepth?.let { adaptivePrefetchDepth = it.coerceIn(1, 3) }
        cacheTtlMs?.let { adaptiveCacheTtlMs = it.coerceIn(2_000L, 12_000L) }
    }

    fun adaptivePrefetchDepth(): Int = adaptivePrefetchDepth
    fun effectivePrefetchDepth(): Int = if (AppManagers.isReady()) minOf(adaptivePrefetchDepth, AppManagers.battery.recommendedPrefetchDepth()) else adaptivePrefetchDepth
    fun recommendedImageConcurrency(): Int = if (AppManagers.isReady()) AppManagers.battery.recommendedImageConcurrency() else 2
    fun adaptiveCacheTtlMs(): Long = adaptiveCacheTtlMs
    fun healthScore(): Int = healthScore
    fun setSafeMode(enabled: Boolean) {
        if (enabled && !safeMode) {
            preSafePrefetch = adaptivePrefetchDepth
            preSafeCacheTtlMs = adaptiveCacheTtlMs
            adaptivePrefetchDepth = 1
            adaptiveCacheTtlMs = 2_000L
        } else if (!enabled && safeMode) {
            adaptivePrefetchDepth = preSafePrefetch.coerceIn(1, 3)
            adaptiveCacheTtlMs = preSafeCacheTtlMs.coerceIn(2_000L, 12_000L)
        }
        safeMode = enabled
    }
    fun isSafeMode(): Boolean = safeMode

    /** Non-invasive health feedback; never throws and never touches user data. */
    fun reportLatency(latencyMs: Long) {
        healthScore = when {
            latencyMs <= 40L -> (healthScore + 2).coerceAtMost(100)
            latencyMs <= 120L -> healthScore
            latencyMs <= 300L -> (healthScore - 2).coerceAtLeast(0)
            else -> (healthScore - 6).coerceAtLeast(0)
        }
        if (healthScore < 55) {
            setSafeMode(true)
        } else if (safeMode && healthScore >= 82) {
            setSafeMode(false)
        }
    }
}

data class ProgressRecord(
    val status: String?,
    val bookmark: String?,
    val lastAttempted: Long,
    val nextDue: Long,
    val timeMs: Long,
    val attempts: Int
)

class ProgressSnapshot private constructor(private val records: Map<String, ProgressRecord>) {
    fun record(key: String): ProgressRecord? = records[key]
    fun all(): Map<String, ProgressRecord> = records

    companion object {
        fun from(values: Map<String, *>): ProgressSnapshot {
            val grouped = HashMap<String, MutableMap<String, Any?>>()
            values.forEach { (fullKey, value) ->
                val split = fullKey.lastIndexOf(':')
                if (split <= 0 || split >= fullKey.lastIndex) return@forEach
                val id = fullKey.substring(0, split)
                val field = fullKey.substring(split + 1)
                // Position/source metadata is not per-question progress.
                if (field !in FIELDS) return@forEach
                grouped.getOrPut(id) { HashMap() }[field] = value
            }
            val out = HashMap<String, ProgressRecord>(grouped.size)
            grouped.forEach { (id, m) ->
                out[id] = ProgressRecord(
                    status = m["status"] as? String,
                    bookmark = m["bookmark"] as? String,
                    lastAttempted = number(m["lastAttempted"]),
                    nextDue = number(m["nextDue"]),
                    timeMs = number(m["timeMs"]),
                    attempts = number(m["attempts"]).toInt()
                )
            }
            return ProgressSnapshot(out)
        }

        private fun number(v: Any?): Long = when (v) {
            is Long -> v
            is Int -> v.toLong()
            is Float -> v.toLong()
            is Double -> v.toLong()
            else -> 0L
        }

        private val FIELDS = setOf("status", "bookmark", "lastAttempted", "nextDue", "timeMs", "attempts")
    }
}
