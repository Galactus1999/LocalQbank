package com.localqbank.library

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-process telemetry for Ben's optional neural accelerators.
 * No study data or prompt text is persisted here. The Adaptive Engine reads this
 * live snapshot so the user can see what Ben is doing without opening logcat.
 */
object BenNeuralTelemetry {
    enum class Stage { IDLE, GATE, FTS_RECALL, SEMANTIC_RERANK, GENERATION, VERIFICATION, COMPLETE, FALLBACK, BLOCKED, ERROR }

    data class Snapshot(
        val stage: Stage = Stage.IDLE,
        val stageDetail: String = "Waiting",
        val activeModel: String = "None",
        val modelKind: String = "None",
        val lastElapsedMs: Long = 0L,
        val lastSemanticMs: Long = 0L,
        val lastGenerationMs: Long = 0L,
        val lastEvidenceCount: Int = 0,
        val lastSemanticUsed: Boolean = false,
        val lastGeneratorUsed: Boolean = false,
        val lastVerified: Boolean = false,
        val lastConfidence: Int = 0,
        val lastCriticalClaimsChecked: Int = 0,
        val lastCriticalClaimsSupported: Int = 0,
        val lastCitationValid: Boolean = true,
        val lastNeuralAccepted: Boolean = false,
        val lastNeuralRejected: Boolean = false,
        val lastFallbackReason: String? = null,
        val totalRequests: Long = 0L,
        val neuralRequests: Long = 0L,
        val fallbackRequests: Long = 0L,
        val blockedRequests: Long = 0L,
        val semanticRuns: Long = 0L,
        val generationRuns: Long = 0L,
        val lastError: String? = null,
        val startedAtMs: Long = 0L,
        val lastDiagnosticReport: String? = null,
        val diagnosticUpdatedAtMs: Long = 0L
    )

    private val lock = Any()
    private var state = Snapshot()
    private val requestId = AtomicLong(0L)

    fun snapshot(): Snapshot = synchronized(lock) { state }

    fun begin(requestLabel: String) {
        val id = requestId.incrementAndGet()
        synchronized(lock) {
            state = state.copy(
                stage = Stage.GATE,
                stageDetail = "$requestLabel • request #$id",
                activeModel = "Checking governor",
                modelKind = "Policy",
                lastError = null,
                startedAtMs = System.currentTimeMillis(),
                totalRequests = state.totalRequests + 1L
            )
        }
    }

    fun stage(stage: Stage, detail: String, model: String = "None", kind: String = "None") {
        synchronized(lock) { state = state.copy(stage = stage, stageDetail = detail.take(160), activeModel = model, modelKind = kind) }
    }

    fun semantic(elapsedMs: Long, used: Boolean) {
        synchronized(lock) {
            state = state.copy(lastSemanticMs = elapsedMs, lastSemanticUsed = used, semanticRuns = state.semanticRuns + if (used) 1 else 0)
        }
    }

    fun generation(elapsedMs: Long, used: Boolean) {
        synchronized(lock) {
            state = state.copy(lastGenerationMs = elapsedMs, lastGeneratorUsed = used, generationRuns = state.generationRuns + if (used) 1 else 0)
        }
    }


    fun verifier(verification: BenAnswerVerifier.Verification) {
        synchronized(lock) {
            val checked = verification.criticalClaims.size
            val supported = verification.criticalClaims.count { it.status == BenAnswerVerifier.ClaimStatus.SUPPORTED }
            state = state.copy(
                lastCriticalClaimsChecked = checked,
                lastCriticalClaimsSupported = supported,
                lastCitationValid = verification.citationValid,
                lastNeuralAccepted = verification.acceptable,
                lastNeuralRejected = !verification.acceptable,
                lastFallbackReason = if (verification.acceptable) null else verification.criticalClaims.firstOrNull { it.status == BenAnswerVerifier.ClaimStatus.UNSUPPORTED_CRITICAL }?.let { "Unsupported critical value ${it.value}" }
                    ?: if (!verification.citationValid) "Invalid evidence citation" else if (!verification.acceptable) "Verifier score/gate failure" else null
            )
        }
    }
    fun complete(evidence: Int, verified: Boolean, confidence: Int, elapsedMs: Long, neural: Boolean) {
        synchronized(lock) {
            state = state.copy(
                stage = Stage.COMPLETE,
                stageDetail = if (neural) "Neural + deterministic verification complete" else "Deterministic fallback complete",
                activeModel = if (neural) "Gemma pipeline" else "Deterministic Ben",
                modelKind = if (neural) "Embedding + generation" else "Fallback",
                lastEvidenceCount = evidence,
                lastVerified = verified,
                lastConfidence = confidence.coerceIn(0, 100),
                lastElapsedMs = elapsedMs,
                lastNeuralAccepted = neural,
                lastNeuralRejected = !neural,
                neuralRequests = state.neuralRequests + if (neural) 1 else 0,
                fallbackRequests = state.fallbackRequests + if (!neural) 1 else 0
            )
        }
    }

    fun blocked(detail: String) {
        synchronized(lock) { state = state.copy(stage = Stage.BLOCKED, stageDetail = detail.take(160), activeModel = "None", modelKind = "Governor", blockedRequests = state.blockedRequests + 1L) }
    }

    fun setDiagnosticReport(report: String) {
        synchronized(lock) { state = state.copy(lastDiagnosticReport = report.take(12000), diagnosticUpdatedAtMs = System.currentTimeMillis()) }
    }

    fun error(detail: String) {
        synchronized(lock) { state = state.copy(stage = Stage.ERROR, stageDetail = detail.take(160), lastError = detail.take(200)) }
    }
}
