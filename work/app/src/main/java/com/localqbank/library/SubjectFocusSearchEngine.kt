package com.localqbank.library

import android.content.Context
import android.os.Process
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/**
 * Fast local retrieval layer for Subject Focus.
 *
 * The expensive operation is SQLite/FTS retrieval, not the small ranking pass.  This engine
 * deliberately retrieves candidates from the database instead of materialising every
 * QuestionRef (and especially every question body) into the UI process for each search.
 * It also coalesces identical requests and cancels obsolete work when a newer query wins.
 */
class SubjectFocusSearchEngine(context: Context) {
    private val app = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "subject-focus-search").apply { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
    }
    private val generation = AtomicLong(0)
    private val inFlight = ConcurrentHashMap<String, Future<*>>()
    private val activeFuture = java.util.concurrent.atomic.AtomicReference<Future<*>?>(null)
    private data class Cached(val result: Result, val at: Long)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val ttlMs = 30_000L

    data class Result(val ids: LongArray, val matched: Int, val query: String)

    fun search(query: String, limit: Int = 500, onResult: (Result) -> Unit) {
        val clean = normalize(query)
        if (clean.isBlank()) {
            onResult(Result(longArrayOf(), 0, clean))
            return
        }
        val key = clean + "\u0000" + limit.coerceIn(1, 500)
        val now = System.currentTimeMillis()
        cache[key]?.let { cached ->
            if (now - cached.at < ttlMs) {
                AppManagers.experience.submitHome { onResult(cached.result.copy(ids = cached.result.ids.copyOf())) }
                return
            }
        }
        inFlight[key]?.let { return }
        val ticket = generation.incrementAndGet()
        activeFuture.getAndSet(null)?.cancel(true)
        val future = executor.submit {
            try {
                val result = runCatching { compute(clean, limit.coerceIn(1, 500)) }
                    .getOrElse { Result(longArrayOf(), 0, clean) }
                if (ticket == generation.get()) {
                    cache[key] = Cached(result, System.currentTimeMillis())
                    trimCache()
                    onResult(result.copy(ids = result.ids.copyOf()))
                }
            } finally {
                inFlight.remove(key)
            }
        }
        inFlight[key] = future
        activeFuture.set(future)
    }

    /** Synchronous bridge for existing background intelligence callers. Never call from UI. */
    fun searchSync(query: String, limit: Int = 500): Result = compute(normalize(query), limit.coerceIn(1, 500))

    fun invalidate() {
        generation.incrementAndGet()
        cache.clear()
        activeFuture.getAndSet(null)?.cancel(true)
        inFlight.values.forEach { it.cancel(true) }
        inFlight.clear()
    }

    fun shutdown() {
        invalidate()
        executor.shutdownNow()
    }

    private fun compute(clean: String, limit: Int): Result {
        if (clean.isBlank()) return Result(longArrayOf(), 0, clean)
        val db = QBankDb(app)
        return try {
            // FTS4 is the primary path; QBankDb safely falls back to bounded SQL matching.
            val hits = db.search(clean, (limit * 2).coerceIn(400, 1200))
            val progress = PerformanceManager.progress(app)
            val now = System.currentTimeMillis()
            val tokens = clean.split(Regex("[^a-z0-9]+" )).filter { it.length >= 2 }.distinct()
            val ranked = hits.asSequence().distinctBy { it.id }.map { hit ->
                val text = hit.text.lowercase()
                val meta = "${hit.testTitle} ${hit.path.orEmpty()} ${hit.sourceName}".lowercase()
                var score = 0
                tokens.forEach { token ->
                    if (text.contains(token)) score += 100
                    if (meta.contains(token)) score += 65
                }
                if (text.contains(clean)) score += 180
                if (meta.contains(clean)) score += 90
                val record = progress.record(hit.stableKey)
                when {
                    record == null -> score += 20
                    record.status == "wrong" -> score += 30
                    record.nextDue == 0L || now >= record.nextDue -> score += 18
                    record.status == "correct" -> score -= 4
                }
                hit to score
            }.sortedByDescending { it.second }.toList()
            Result(ranked.take(limit).map { it.first.id }.toLongArray(), ranked.size, clean)
        } finally {
            db.close()
        }
    }

    private fun normalize(query: String): String = query.trim().lowercase()
        .replace(Regex("\\s+"), " ")
        .take(160)

    private fun trimCache() {
        if (cache.size <= 32) return
        cache.entries.sortedBy { it.value.at }.take(cache.size - 32).forEach { cache.remove(it.key) }
    }
}
