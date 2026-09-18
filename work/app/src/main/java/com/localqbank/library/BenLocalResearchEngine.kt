package com.localqbank.library

import android.content.Context

/**
 * Offline-only research gateway for Ben.
 *
 * This is deliberately an adapter boundary, not an AI runtime. It never opens a URL,
 * performs network I/O, or downloads a model. Until a validated LiteRT-LM backend is
 * installed, requests fall back to Rovex's existing local clinical/QBank intelligence.
 * This keeps the stability build deterministic while leaving a safe insertion point for
 * an on-device Hugging Face/LiteRT model later.
 */
class BenLocalResearchEngine(
    context: Context,
    private val inferenceBackend: BenInferenceBackend = UnavailableBenInferenceBackend
) {
    private val app = context.applicationContext
    private val runtimePolicy = BenAiRuntimePolicy(app)
    private val resourceGovernor = BenAiResourceGovernor(app)

    data class Capability(
        val offlineOnly: Boolean = true,
        val modelBackendAvailable: Boolean = false,
        val groundedInLocalQBank: Boolean = true
    )

    data class Result(
        val answer: String,
        val source: String,
        val modelUsed: Boolean
    )

    fun capability(): Capability = Capability(
        modelBackendAvailable = inferenceBackend.isAvailable && resourceGovernor.mayRun(runtimePolicy, inferenceBackend.estimatedModelMb, foreground = true)
    )

    fun research(query: String): Result {
        val clean = BenResearchInputPolicy.normalize(query)
        if (clean == null) {
            return Result("Give Ben a clinical term, topic, question, or study task.", "local-safety", false)
        }
        val modelAnswer = if (inferenceBackend.isAvailable && resourceGovernor.mayRun(runtimePolicy, inferenceBackend.estimatedModelMb, foreground = true)) {
            runCatching { BenResponsePolicy.normalize(inferenceBackend.answer(clean)) }
                .onFailure { runtimePolicy.recordBackendFailure() }
                .getOrNull()
        } else null
        if (!modelAnswer.isNullOrBlank()) {
            val verified = runCatching {
                if (AppManagers.isReady()) AppManagers.benBrain.answer(clean, baseAnswer = modelAnswer, modelUsed = true)
                else BenCognitiveArchitecture(app).answer(clean, baseAnswer = modelAnswer, modelUsed = true)
            }.getOrNull()
            if (verified != null && verified.answer.isNotBlank()) {
                runtimePolicy.recordBackendSuccess()
                return Result(verified.answer, "Ben local model + cognitive verifier", true)
            }
        }
        val cognitive = runCatching {
            if (AppManagers.isReady()) {
                val base = AppManagers.renCognitive.answer(clean)
                AppManagers.benBrain.answer(clean, baseAnswer = base.body, modelUsed = false)
            } else {
                val base = RenCognitiveEngine(app).answer(clean)
                BenCognitiveArchitecture(app).answer(clean, baseAnswer = base.body, modelUsed = false)
            }
        }.getOrElse {
            runCatching {
                val base = RenCognitiveEngine(app).answer(clean)
                BenCognitiveArchitecture(app).answer(clean, baseAnswer = base.body, modelUsed = false)
            }.getOrNull()
        }
        if (cognitive != null && cognitive.answer.isNotBlank()) {
            return Result(cognitive.answer, "Ben offline cognitive core", cognitive.modelUsed)
        }
        // Ben must never turn a local-engine failure into an Activity crash.
        return Result(
            "Ben could not complete that local request right now. The study app remains available; try the request again.",
            "local-safety",
            false
        )
    }
}
