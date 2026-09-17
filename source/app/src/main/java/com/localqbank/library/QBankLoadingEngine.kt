package com.localqbank.library

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import android.os.Process

/**
 * Dedicated QBank loading layer. It keeps navigation metadata off the UI thread,
 * coalesces repeated section loads, and retains a small TTL cache so reopening a
 * heavy QBank feels immediate without retaining question HTML in memory.
 */
class QBankLoadingEngine(context: Context) {
    private val app = context.applicationContext
    private val executor = ThreadPoolExecutor(
        1, 2, 15L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()
    ).apply {
        threadFactory = java.util.concurrent.ThreadFactory { r ->
            Thread(r, "qbank-loader").apply { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        }
    }
    /** Fast Home-library source path. This must remain independent from question indexing so a
     * committed import becomes visible even if a heavier dashboard calculation is delayed. */
    fun loadSources(onLoaded: (List<Source>) -> Unit) {
        executor.execute {
            Process.setThreadPriority(
                if (AppManagers.isReady() && AppManagers.runtime.framePressure() >= 2)
                    Process.THREAD_PRIORITY_BACKGROUND + 1
                else Process.THREAD_PRIORITY_BACKGROUND
            )
            val sources = runCatching {
                QBankDb(app).let { db -> try { db.sources() } finally { db.close() } }
            }.getOrDefault(emptyList())
            onLoaded(sources)
        }
    }

    private fun adaptRuntimeBudget() {
        val workers = if (AppManagers.isReady()) AppManagers.runtime.recommendedBackgroundWorkers() else 1
        // Lowering the core size never interrupts active SQLite work; the extra worker simply
        // becomes eligible for retirement once it finishes. This is the runtime equivalent of
        // a game engine reducing its worker-job budget under frame pressure.
        executor.corePoolSize = workers.coerceIn(1, 2)
    }
    data class SectionBundle(val tests: List<Test>, val summaries: Map<String, ProgressSummary>, val source: Source?)
    private data class TestCache(val tests: List<Test>, val at: Long)
    private data class BundleCache(val bundle: SectionBundle, val at: Long)
    private val testCache = ConcurrentHashMap<Long, TestCache>()
    private val bundleCache = ConcurrentHashMap<Long, BundleCache>()
    private val inFlight = ConcurrentHashMap<Long, java.util.concurrent.Future<*>>()
    private val ttlMs = 30_000L

    fun loadSections(sourceId: Long, onLoaded: (List<Test>) -> Unit) {
        // Keep one in-flight path per source so a warm-up and the section screen never
        // open duplicate SQLite readers for the same heavy QBank.
        loadSectionBundle(sourceId) { onLoaded(it.tests) }
    }

    fun loadSectionBundle(sourceId: Long, onLoaded: (SectionBundle) -> Unit) {
        if (sourceId <= 0L) { onLoaded(SectionBundle(emptyList(), emptyMap(), null)); return }
        val now = System.currentTimeMillis()
        bundleCache[sourceId]?.let { cached ->
            if (now - cached.at < ttlMs) {
                executor.execute { onLoaded(cached.bundle) }
                return
            }
        }
        val key = sourceId * -1L
        if (inFlight.containsKey(key)) return
        adaptRuntimeBudget()
        val future = executor.submit {
            // The runtime governor protects foreground frames. Keep metadata work at background
            // priority and lower it one additional level while the UI is missing frames.
            Process.setThreadPriority(
                if (AppManagers.runtime.framePressure() >= 2) Process.THREAD_PRIORITY_BACKGROUND + 1
                else Process.THREAD_PRIORITY_BACKGROUND
            )
            try {
                val db = QBankDb(app)
                try {
                    val tests = db.tests(sourceId)
                    val progress = PerformanceManager.progress(app)
                    val summaries = db.testProgressSummaries(sourceId, progress)
                    val source = db.sources().firstOrNull { it.id == sourceId }
                    val bundle = SectionBundle(tests, summaries, source)
                    bundleCache[sourceId] = BundleCache(bundle, System.currentTimeMillis())
                    testCache[sourceId] = TestCache(tests, System.currentTimeMillis())
                    onLoaded(bundle)
                } finally { db.close() }
            } finally { inFlight.remove(key) }
        }
        inFlight[key] = future
    }

    fun warmSource(sourceId: Long) {
        if (sourceId <= 0L) return
        loadSections(sourceId) { }
    }

    fun invalidate(sourceId: Long? = null) {
        if (sourceId == null) { testCache.clear(); bundleCache.clear() }
        else { testCache.remove(sourceId); bundleCache.remove(sourceId) }
    }

    fun shutdown() { executor.shutdownNow() }
}
