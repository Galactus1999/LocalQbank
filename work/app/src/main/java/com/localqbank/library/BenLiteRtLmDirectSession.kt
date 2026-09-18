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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Short-lived, Activity-owned direct Gemma session for Model Lab.
 * It keeps one Engine/Conversation alive while the lab is open, then closes both explicitly.
 * It owns no Ben business logic and never runs on the UI thread.
 */
class BenLiteRtLmDirectSession(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val models = BenNeuralModelManager(app)
    private val profile = BenNeuralModelRegistry.gemma3_270m
    private val policy = BenAiRuntimePolicy(app)
    private val governor = BenAiResourceGovernor(app)
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun send(prompt: String, maxOutputTokens: Int = 160): Result<String> = withContext(Dispatchers.IO) {
        val clean = prompt.trim().take(6000)
        if (clean.isBlank()) return@withContext Result.failure(IllegalArgumentException("Prompt is empty"))
        val installed = models.installed(profile)
            ?: return@withContext Result.failure(IllegalStateException("Gemma 3 270M model is not installed"))
        if (!governor.mayRun(policy, profile.estimatedRuntimeMb, foreground = true))
            return@withContext Result.failure(IllegalStateException("Gemma blocked by the Ben resource governor"))

        try {
            if (engine == null || conversation == null) initialize(installed.file)
            val started = System.nanoTime()
            val response = conversation!!.sendMessage(
                Contents.of(Content.Text(clean)),
                maxOutputToken = maxOutputTokens.coerceIn(32, 192)
            )
            val text = response.contents.contents.filterIsInstance<Content.Text>().joinToString(" ") { it.text }.trim()
            if (text.isBlank()) throw IllegalStateException("Gemma returned an empty response")
            BenNeuralTelemetry.generation((System.nanoTime() - started) / 1_000_000L, true)
            policy.recordBackendSuccess()
            Result.success(text.take(10_000))
        } catch (error: Exception) {
            BenNeuralTelemetry.error("Direct Gemma: ${error.javaClass.simpleName}")
            policy.recordBackendFailure()
            close()
            Result.failure(error)
        }
    }

    private fun initialize(modelFile: File) {
        close()
        val newEngine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = 672,
                cacheDir = File(app.cacheDir, "ben_litertlm_direct").apply { mkdirs() }.absolutePath,
            )
        )
        newEngine.initialize()
        val newConversation = newEngine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                systemInstruction = Contents.of(
                    Content.Text("You are Gemma running inside Ben's local Model Lab. Answer the user's study question clearly. Do not claim internet access or invent citations. Keep answers concise unless the user asks for detail.")
                )
            )
        )
        engine = newEngine
        conversation = newConversation
    }

    override fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
