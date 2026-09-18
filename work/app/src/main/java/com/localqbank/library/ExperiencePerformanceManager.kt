package com.localqbank.library

import android.content.Context
import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground-experience coordinator. Heavy derived work is isolated from the main thread
 * and stale work is prevented from publishing after a newer screen state exists.
 * It deliberately does not own study/database business logic.
 */
class ExperiencePerformanceManager(context: Context) {
    private val generation = AtomicLong(0)
    private val executor: ThreadPoolExecutor = ThreadPoolExecutor(
        1, 2, 15L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()
    ).apply {
        threadFactory = java.util.concurrent.ThreadFactory { r ->
            Thread(r, "rovex-experience").apply { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        }
    }

    private fun adaptBudget() {
        val workers = if (AppManagers.isReady()) AppManagers.runtime.recommendedBackgroundWorkers() else 1
        executor.corePoolSize = workers.coerceIn(1, 2)
    }

    fun newGeneration(): Long = generation.incrementAndGet()
    fun currentGeneration(): Long = generation.get()

    fun submitHome(task: () -> Unit): Future<*> {
        adaptBudget()
        return executor.submit {
            Process.setThreadPriority(if (AppManagers.runtime.framePressure() >= 2) Process.THREAD_PRIORITY_BACKGROUND + 1 else Process.THREAD_PRIORITY_BACKGROUND)
            task()
        }
    }
    fun submitFrankenstein(task: () -> Unit): Future<*> {
        adaptBudget()
        return executor.submit {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task()
        }
    }

    /** Suspend-aware lane for LiteRT/MediaPipe neural work without blocking a worker on a synchronous coroutine bridge. */
    private val coroutineScope by lazy { CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher()) }

    fun submitFrankensteinSuspend(task: suspend () -> Unit): Job {
        adaptBudget()
        return coroutineScope.launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task()
        }
    }

    fun shutdown() { coroutineScope.cancel(); executor.shutdownNow() }
}

/**
 * Supporting engine for Dr. Frankenstein. It moves cognition/search off the UI thread,
 * coalesces identical commands and keeps a very small result cache.
 */
class FrankensteinSupportEngine(context: Context) {
    private val app = context.applicationContext
    private data class Cached(val insight: RenCognitiveEngine.Insight, val ids: LongArray, val at: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val ttlMs = 20_000L
    private val neuralPipeline by lazy { BenGroundedNeuralPipeline(app) }
    private val neuralPolicy by lazy { BenAiRuntimePolicy(app) }
    private val neuralModels by lazy { BenNeuralModelManager(app) }

    fun respond(command: String, currentQuestion: Question? = null, onResult: (RenCognitiveEngine.Insight, LongArray) -> Unit) {
        val key = command.trim().lowercase() + "\u0000" + (currentQuestion?.stableKey.orEmpty())
        val now = System.currentTimeMillis()
        cache[key]?.let { cached ->
            if (now - cached.at < ttlMs) {
                AppManagers.experience.submitFrankenstein { onResult(cached.insight, cached.ids.copyOf()) }
                return
            }
        }
        if (inFlight.containsKey(key)) return

        val job = AppManagers.experience.submitFrankensteinSuspend {
            try {
                val actionCommand = isActionCommand(command)
                val hasNeuralArtifact = neuralModels.installed(BenNeuralModelRegistry.embeddingGemma300m) != null ||
                    neuralModels.installed(BenNeuralModelRegistry.gemma3_270m) != null
                val useNeural = !actionCommand && neuralPolicy.enabled && !neuralPolicy.circuitOpen && hasNeuralArtifact

                if (useNeural) {
                    val result = runCatching { neuralPipeline.run(command) }.getOrNull()
                    if (result != null && result.answer.isNotBlank()) {
                        val visual = runCatching { AppManagers.renCognitive.understand(command).visualIntent }.getOrDefault(false)
                        val ids = runCatching {
                            AppManagers.renCognitive.searchQuestionIds(command, if (visual) 500 else 100)
                        }.getOrDefault(longArrayOf())
                        val actions = if (ids.isNotEmpty()) listOf("Open matching questions") else emptyList()
                        val insight = RenCognitiveEngine.Insight(
                            if (result.modelUsed) "Dr. Frankenstein • grounded neural response" else "Dr. Frankenstein • deterministic fallback",
                            result.answer.trim(),
                            actions
                        )
                        cache[key] = Cached(insight, ids.copyOf(), System.currentTimeMillis())
                        trimCache()
                        onResult(insight, ids)
                        return@submitFrankensteinSuspend
                    }
                }

                // Action-oriented commands stay on the deterministic command router.
                val cognitive = AppManagers.renCognitive
                val insight = runCatching { cognitive.answer(command, currentQuestion) }
                    .getOrElse { RenCognitiveEngine.Insight("Dr. Frankenstein", "I could not complete that request safely. Try a simpler command.") }
                val ids = if (insight.actions.contains("Open matching questions")) {
                    val visual = runCatching { cognitive.understand(command).visualIntent }.getOrDefault(false)
                    runCatching { cognitive.searchQuestionIds(command, if (visual) 500 else 100) }.getOrDefault(longArrayOf())
                } else longArrayOf()
                cache[key] = Cached(insight, ids.copyOf(), System.currentTimeMillis())
                trimCache()
                onResult(insight, ids)
            } finally {
                inFlight.remove(key)
            }
        }
        inFlight[key] = job
    }

    fun invalidate() { cache.clear() }
    fun trimNeuralResources() { runCatching { neuralPipeline.trim() } }
    fun shutdown() { cache.clear(); inFlight.values.forEach { it.cancel() }; inFlight.clear(); runCatching { neuralPipeline.close() } }

    private fun isActionCommand(command: String): Boolean {
        val q = command.lowercase()
        return listOf("flashcard", "flash card", "create note", "save note", "what should i study", "study plan", "wrong questions", "make a note", "start quiz", "open study tools").any { q.contains(it) }
    }

    private fun trimCache() {
        if (cache.size <= 24) return
        val oldest = cache.entries.minByOrNull { it.value.at }?.key ?: return
        cache.remove(oldest)
    }
}

