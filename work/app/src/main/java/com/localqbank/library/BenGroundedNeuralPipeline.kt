package com.localqbank.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import com.localqbank.library.ai.context.ContextPackBuilder
import com.localqbank.library.ai.context.EvidencePack
import com.localqbank.library.ai.context.toEvidencePack

/**
 * Controlled bridge between Ben's deterministic cognition and the optional neural accelerator.
 * The model never becomes the source of truth: local QBank evidence is supplied to the model,
 * then the draft is passed back through Ben's existing planner/verifier before it can be shown.
 */
class BenGroundedNeuralPipeline(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val inference = BenInferenceProcessClient(app)
    private val cognitive = BenCognitiveArchitecture(app)
    private val clinical = ClinicalKnowledgeLayer(app)
    private val examTerms = BenExamTerminology(app)
    private val contextPacks = ContextPackBuilder(app)

    data class Result(
        val answer: String,
        val modelUsed: Boolean,
        val verified: Boolean,
        val confidence: Int,
        val evidenceCount: Int,
        val elapsedMs: Long
    )

    suspend fun run(query: String): Result = withContext(Dispatchers.IO) {
        BenNeuralTelemetry.begin("Grounded Ben query")
        val clean = BenResearchInputPolicy.normalize(query)
            ?: return@withContext Result("Give Ben a clinical term, topic, question, or study task.", false, true, 0, 0, 0)

        // Do not pin a SQLite handle for the lifetime of the neural pipeline. Ben may live for
        // the entire application process, while imports/restores can replace or rebuild the DB.
        // A short-lived handle per grounded request avoids stale connections and is bounded by
        // the existing background execution context.
        val qbank = QBankDb(app)
        try {
            val understanding = clinical.understand(clean)
            val examTermsContext = examTerms.understand(clean)
                val contextPack = contextPacks.forQuery(clean)
            val examContext = contextPack.toPromptBlock() + "\n" + examTermsContext.promptContext
            BenNeuralTelemetry.stage(BenNeuralTelemetry.Stage.FTS_RECALL, if (understanding.visualIntent) "Retrieving image-bearing QBank candidates" else "Retrieving local QBank candidates")
            val visualBase = linkedSetOf<String>().apply {
                understanding.concepts.take(6).forEach { add(it.canonical) }
                understanding.domains.filter { it.length >= 3 }.forEach { add(it) }
            }.joinToString(" ").ifBlank { clean }
            val hits = runCatching {
                if (understanding.visualIntent) qbank.searchImageQuestions(visualBase, 12) else qbank.search(clean, 12)
            }.getOrElse { emptyList() }
        BenNeuralTelemetry.stage(BenNeuralTelemetry.Stage.SEMANTIC_RERANK, if (understanding.visualIntent) "Isolated semantic reranking ${hits.size} actual image-bearing candidates" else "Isolated semantic reranking ${hits.size} candidates", "EmbeddingGemma 300M", "IPC / NPU")
        val semanticQuery = (clean + " " + examTermsContext.promptContext).trim().take(1800)
        val semanticRemote = inference.rerankSuspend(semanticQuery, hits.take(8).map { it.text }, 8)
        val semanticElapsed = semanticRemote?.elapsedMs ?: 0L
        val semanticUsed = semanticRemote?.usedModel == true
        BenNeuralTelemetry.semantic(semanticElapsed, semanticUsed)
        // Reciprocal Rank Fusion keeps lexical QBank recall authoritative while allowing
        // semantic embeddings to contribute without inventing a fragile score-calibration scheme.
        val rankScores = mutableMapOf<Long, Double>()
        val rankedHits = if (semanticRemote != null && semanticRemote.usedModel && semanticRemote.indices.isNotEmpty()) {
            val lexicalRank = hits.take(8).mapIndexed { index, hit -> hit.id to index + 1 }.toMap()
            val semanticRank = semanticRemote.indices.mapIndexed { index, candidateIndex ->
                hits.getOrNull(candidateIndex)?.id?.let { it to index + 1 }
            }.filterNotNull().toMap()
            hits.take(8).sortedByDescending { hit ->
                val score = 1.0 / (60.0 + (lexicalRank[hit.id] ?: 100)) +
                    1.0 / (60.0 + (semanticRank[hit.id] ?: 100))
                rankScores[hit.id] = score
                score
            }
        } else {
            hits.take(8).also { list ->
                list.forEachIndexed { index, hit -> rankScores[hit.id] = 1.0 / (60.0 + index + 1) }
            }
        }
        val contrastive = BenContrastiveEvidencePlanner.plan(
            clean,
            rankedHits.map { BenContrastiveEvidencePlanner.Candidate(it.id, it.text, rankScores[it.id] ?: 0.0) },
            maxAnswer = 4,
            maxDistractor = 4
        )
        // Only ANSWER_EVIDENCE becomes citation-authoritative verifier evidence. Distractors
        // remain contrastive context for the neural model and cannot silently become sources.
        val evidencePack = contrastive.toEvidencePack()
        val evidence = evidencePack.authoritativeAnswerEvidence().mapIndexed { index, item ->
            BenAnswerVerifier.EvidenceItem(index + 1, item.sourceId, item.text)
        }
        val prompt = buildPrompt(contextPack, evidencePack, evidence, examContext, understanding.visualIntent, understanding.visualKinds)
        BenNeuralTelemetry.stage(BenNeuralTelemetry.Stage.GENERATION, "Generating grounded draft", "Gemma 3 270M", "Generation")
        val generationStarted = System.currentTimeMillis()
        var generationProcessDied = false
        val generatedText = buildString {
            inference.generateStream(prompt, 160).collect { event ->
                when (event) {
                    is BenInferenceProcessClient.GenerationEvent.Token -> append(event.text)
                    is BenInferenceProcessClient.GenerationEvent.Complete -> {
                        if (isEmpty()) append(event.text)
                    }
                    is BenInferenceProcessClient.GenerationEvent.Failed -> Unit
                    BenInferenceProcessClient.GenerationEvent.Cancelled -> Unit
                    BenInferenceProcessClient.GenerationEvent.ProcessDied -> generationProcessDied = true
                }
            }
        }.trim().takeIf { it.isNotBlank() }
        val generationElapsed = (System.currentTimeMillis() - generationStarted).coerceAtLeast(0L)
        val draft = generatedText?.let { BenLiteRtLmGenerator.Result(it, generationElapsed, BenNeuralModelRegistry.gemma3_270m.estimatedModelMb) }
        BenNeuralTelemetry.generation(generationElapsed, draft != null)
        if (draft == null) {
            BenNeuralTelemetry.stage(
                BenNeuralTelemetry.Stage.FALLBACK,
                if (generationProcessDied) "Inference process died; deterministic Ben answering" else "Neural generation unavailable; deterministic Ben answering"
            )
            val fallback = cognitive.answer(clean)
            val elapsed = System.currentTimeMillis() - (BenNeuralTelemetry.snapshot().startedAtMs)
            val neuralUsed = semanticUsed
            BenNeuralTelemetry.complete(evidence.size, fallback.verified, fallback.confidence, elapsed.coerceAtLeast(0L), neuralUsed)
            return@withContext Result(fallback.answer, neuralUsed, fallback.verified, fallback.confidence, evidence.size, elapsed.coerceAtLeast(0L))
        }

        BenNeuralTelemetry.stage(BenNeuralTelemetry.Stage.VERIFICATION, "Verifying neural draft with Ben")
        val verified = runCatching {
            cognitive.answer(
                clean,
                baseAnswer = draft.text,
                modelUsed = true,
                evidence = evidence
            )
        }.getOrNull()

        if (verified == null || verified.answer.isBlank() || !verified.verified) {
            val reason = when {
                verified == null -> "Neural verification unavailable"
                verified.answer.isBlank() -> "Neural verification returned blank"
                else -> "Neural draft failed evidence gate"
            }
            BenNeuralTelemetry.stage(BenNeuralTelemetry.Stage.FALLBACK, "$reason; deterministic fallback")
            val fallback = cognitive.answer(clean)
            BenNeuralTelemetry.complete(evidence.size, fallback.verified, fallback.confidence, draft.elapsedMs, false)
            return@withContext Result(fallback.answer, false, fallback.verified, fallback.confidence, evidence.size, draft.elapsedMs)
        }
        BenNeuralTelemetry.complete(evidence.size, verified.verified, verified.confidence, draft.elapsedMs, true)
            Result(
                answer = verified.answer,
                modelUsed = true,
                verified = verified.verified,
                confidence = verified.confidence,
                evidenceCount = evidence.size,
                elapsedMs = draft.elapsedMs
            )
        } finally {
            qbank.close()
        }
    }

    fun trim() { inference.trim() }
    override fun close() { inference.close() }

    private fun buildPrompt(
        contextPack: com.localqbank.library.ai.context.ContextPack,
        evidencePack: EvidencePack,
        evidence: List<BenAnswerVerifier.EvidenceItem>,
        examContext: String,
        visualIntent: Boolean,
        visualKinds: Set<String>
    ): String {
        val evidenceLines = evidence.map { "[${it.index}] ${it.text}" }
        val window = BenContextWindowPolicy.build(contextPack.question, examContext, evidenceLines)
        return buildString {
            append(window.text)
            append("\nUnified evidence roles (advisory retrieval metadata):\n")
            append("QUESTION_EVIDENCE: ").append(evidencePack.questionEvidence.joinToString(" | ") { it.text }).append("\n")
            append("ANSWER_EVIDENCE: ").append(evidencePack.answerEvidence.joinToString(" | ") { it.text }).append("\n")
            append("DISTRACTOR_EVIDENCE: ").append(evidencePack.distractorEvidence.joinToString(" | ") { it.text }).append("\n")
            append("Treat distractor evidence as contrastive context only; never cite or promote it as authoritative unless the supplied verifier evidence supports the claim.\n")
            if (visualIntent) {
                append("\nVisual retrieval requirement: the user is asking for image-bearing questions. Never equate the words 'image', 'slide' or 'pathology' in a stem with an actual image. Ben's retrieval layer has already hard-filtered for imported question/explanation images. Visual kinds detected: ")
                append(visualKinds.joinToString(", ")).append(".\n")
            }
            if (window.truncated) append("\nContext window was bounded; use only the supplied evidence.\n")
            append("\nDraft a concise medical-study answer. Do not invent facts, doses, guidelines, patient-specific treatment, or visual findings you have not been given.")
            append("\nEvidence citation rule: every factual claim derived from local QBank evidence must be followed by [n], where n is one of the supplied evidence numbers. Never invent a citation number. Keep critical numbers, ages, doses, thresholds, durations, percentages, scores, staging or recommendation claims tied to the evidence that supports them. If evidence is insufficient, say so rather than guessing.")
            append("\nThis is a draft for Ben's deterministic evidence verifier. A failed verification is discarded and replaced by deterministic Ben; do not try to bypass the evidence gate with disclaimers.")
        }
    }
}
