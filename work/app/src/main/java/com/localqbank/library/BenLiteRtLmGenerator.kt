package com.localqbank.library

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CPU-first Gemma adapter for normal Ben chat.
 *
 * Unlike the original one-shot implementation, the engine/conversation is reused for a short
 * interactive window. This avoids reinitialising LiteRT-LM for every user message. The session
 * is closed after a bounded idle period, when the model changes, or after a runtime failure.
 * It owns no Ben business logic or scheduling.
 */
class BenLiteRtLmGenerator(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val models = BenNeuralModelManager(app)
    private val profile = BenNeuralModelRegistry.gemma3_270m
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelPath: String? = null
    private var lastUsedAtMs: Long = 0L

    data class Result(val text: String, val elapsedMs: Long, val modelMb: Int)

    suspend fun generate(prompt: String, maxOutputTokens: Int = 192): Result? = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val chunks = mutableListOf<String>()
        generateStream(prompt, maxOutputTokens).collect { chunks += it }
        val text = chunks.joinToString("").trim()
        text.takeIf { it.isNotBlank() }?.let {
            Result(it.take(8_000), (System.nanoTime() - started) / 1_000_000L, profile.estimatedModelMb)
        }
    }

    /**
     * LiteRT-LM native streaming bridge. Cancellation is propagated to the native Conversation
     * through cancelProcess(); the caller's release barrier must wait for this Flow to terminate.
     */
    fun generateStream(
        prompt: String,
        maxOutputTokens: Int = 192,
        onCancel: () -> Boolean = { false },
        onNativeCancelReady: (((() -> Unit)) -> Unit)? = null,
    ): Flow<String> = kotlinx.coroutines.flow.flow {
        val clean = prompt.trim().take(6_000)
        if (clean.isBlank()) return@flow
        val installed = models.installed(profile) ?: run {
            BenNeuralTelemetry.blocked("Gemma 3 270M model is not installed")
            return@flow
        }
        val governor = BenAiResourceGovernor(app)
        val policy = BenAiRuntimePolicy(app)
        if (!governor.mayRun(policy, profile.estimatedRuntimeMb, foreground = true)) {
            BenNeuralTelemetry.blocked("Gemma generation blocked by policy/resource governor")
            return@flow
        }
        val governorSnapshot = governor.snapshot(foreground = true)
        val interactiveMaxTokens = if (governorSnapshot.powerSave) minOf(maxOutputTokens, 96) else maxOutputTokens
        mutex.withLock {
            val now = System.currentTimeMillis()
            try {
                if (engine == null || conversation == null || activeModelPath != installed.file.absolutePath || now - lastUsedAtMs > SESSION_IDLE_TIMEOUT_MS) {
                    initialize(installed.file)
                }
                val activeConversation = conversation ?: return@withLock
                onNativeCancelReady?.invoke { runCatching { activeConversation.cancelProcess() } }
                activeConversation.sendMessageAsync(
                    Contents.of(Content.Text(clean)),
                    maxOutputToken = interactiveMaxTokens.coerceIn(32, 192)
                ).collect { message ->
                    if (onCancel()) {
                        runCatching { activeConversation.cancelProcess() }
                        throw kotlinx.coroutines.CancellationException("Ben inference cancelled")
                    }
                    message.contents.contents.filterIsInstance<Content.Text>().forEach { emit(it.text) }
                }
                lastUsedAtMs = System.currentTimeMillis()
                policy.recordBackendSuccess()
            } catch (error: kotlinx.coroutines.CancellationException) {
                runCatching { conversation?.cancelProcess() }
                throw error
            } catch (error: Exception) {
                BenNeuralTelemetry.error("Gemma 3 270M: ${error.javaClass.simpleName}")
                policy.recordBackendFailure()
                closeLocked()
                throw error
            }
        }
    }.onStart { }.onCompletion { }

    private fun initialize(modelFile: File) {
        closeLocked()
        val newEngine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = 672,
                cacheDir = File(app.cacheDir, "ben_litertlm").apply { mkdirs() }.absolutePath,
            )
        )
        try {
            newEngine.initialize()
            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                    systemInstruction = Contents.of(
                        Content.Text(
                            "You are Gemma, the neural generation accelerator inside Ben, an offline medical-study assistant. " +
                                "Use only the supplied local context. Understand Indian postgraduate medical-exam shorthand such as INI-CET, NEET-PG, PYQ, IBQ, image-bank, next-best-step and high-yield. " +
                                "Do not invent medical facts, doses, guidelines, citations, patient-specific treatment, or visual findings that were not supplied. " +
                                "Be concise and let Ben's deterministic verifier remain the final authority."
                        )
                    )
                )
            )
            engine = newEngine
            conversation = newConversation
            activeModelPath = modelFile.absolutePath
            lastUsedAtMs = System.currentTimeMillis()
        } catch (error: Exception) {
            runCatching { newEngine.close() }
            throw error
        }
    }

    private fun closeLocked() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        activeModelPath = null
        lastUsedAtMs = 0L
    }

    override fun close() = runCatching { closeLocked() }.getOrDefault(Unit)

    companion object {
        private const val SESSION_IDLE_TIMEOUT_MS = 120_000L
    }
}
