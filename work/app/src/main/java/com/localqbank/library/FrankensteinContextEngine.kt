package com.localqbank.library

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * Builds the bounded "Frankenstein Context" envelope used by Ben and selected accelerators.
 *
 * It is retrieval-only: it never changes QBank truth, progress, SRS or notes. The QBank remains
 * authoritative; this layer assembles useful context around the current question so the cloud
 * model does not have to rediscover the student's local corpus from scratch.
 */
class FrankensteinContextEngine(context: Context) {
    private val app = context.applicationContext
    private val learner = BenLearnerModel(app)
    private val questionAiPolicy = BenQuestionAiContextPolicy()

    data class RelatedQuestion(
        val id: Long,
        val exam: String,
        val source: String,
        val text: String,
        val answer: String,
        val similarity: Double? = null,
        val category: String = ""
    )

    data class ContextBundle(
        val question: Question,
        val concepts: List<String>,
        val weakestConcept: String?,
        val weakestMastery: Int?,
        val relatedQuestions: List<RelatedQuestion>,
        val relatedSources: List<String>,
        val personalNote: String?,
        val localEvidenceCount: Int,
        val summary: String,
        val examPromptContext: String = "",
        val pdfEvidence: List<BenPdfResearchStore.PageHit> = emptyList()
    ) {
        fun promptBlock(): String = buildString {
            append("FRANKENSTEIN CONTEXT — LOCAL EVIDENCE ONLY\n")
            append(examPromptContext).append("\n")
            append("This envelope was assembled from Rovex's local QBank, learner memory and notes.\n")
            append("Never claim that a row is a true PYQ or that an exam repeated a question unless its local provenance explicitly establishes that.\n\n")
            append("CONCEPTS: ").append(concepts.joinToString(", ").ifBlank { "not confidently mapped" }).append('\n')
            weakestConcept?.let { append("LEARNER WEAKNESS: $it (${weakestMastery ?: 0}% local mastery estimate)\n") }
            personalNote?.let { append("STUDENT NOTE: ").append(it.take(1200)).append('\n') }
            append("RELATED LOCAL QUESTIONS: ").append(relatedQuestions.size).append('\n')
            relatedQuestions.forEachIndexed { i, row ->
                append("[LOCAL-${i + 1}] EXAM/SOURCE: ${row.exam.ifBlank { "Unknown" }} | ${row.source.ifBlank { "Unknown source" }}\n")
                append("QUESTION: ${row.text.take(900)}\nKEY: ${row.answer.take(260)}")
                row.similarity?.let { append(" • semantic=${"%.3f".format(it)}") }
                append("\n")
            }
            if (relatedSources.isNotEmpty()) append("LOCAL SOURCE CLUSTERS: ${relatedSources.joinToString(" • ")}\n")
        }
    }

    /**
     * Enhanced context path: deterministic retrieval is always first. If EmbeddingGemma is already
     * warm, it cheaply reranks the bounded candidate set; it never cold-starts the native runtime
     * merely because a user opened a context panel.
     */
    suspend fun buildEnhanced(question: Question): ContextBundle {
        val base = build(question)
        if (base.relatedQuestions.size < 2) return base
        val embedding = BenEmbeddingGemmaEngine(app)
        if (!embedding.isWarm()) return base
        val reranked = try {
            embedding.rerank(
                query = question.text,
                candidates = base.relatedQuestions,
                textOf = { it.text + "\n" + it.answer },
                limit = minOf(8, base.relatedQuestions.size)
            )
        } catch (t: CancellationException) {
            throw t
        } catch (_: Exception) {
            null
        } ?: return base
        if (!reranked.usedModel || reranked.hits.isEmpty()) return base
        val ranked = reranked.hits.map {
            it.item.copy(similarity = it.similarity)
        }
        return base.copy(
            relatedQuestions = ranked,
            relatedSources = ranked.map { "${it.exam} / ${it.source}" }.distinct().take(8),
            summary = "${ranked.size} semantically reranked local questions • ${base.concepts.take(3).joinToString(", ").ifBlank { "no confident concepts" }}"
        )
    }

    fun questionAiEvidence(question: Question, mode: BenQuestionAiMode): String =
        questionAiPolicy.evidence(question, build(question), mode)

    fun savedQuestionAiEvidence(questionId: Long, mode: BenQuestionAiMode): String = runCatching {
        val db = QBankDb(app)
        try { db.questionById(questionId)?.let { questionAiEvidence(it, mode) }.orEmpty() } finally { db.close() }
    }.getOrDefault("")

    fun build(question: Question): ContextBundle {
        val understanding = runCatching { ClinicalKnowledgeLayer(app).understand(question.text) }.getOrNull()
        val concepts = understanding?.concepts?.map { it.canonical }?.distinct()?.take(8).orEmpty()
        val weakest = concepts.minByOrNull { learner.stats(it).mastery }
        val weakestMastery = weakest?.let { learner.stats(it).mastery }
        val note = runCatching {
            val db = QBankDb(app)
            try { db.notes().firstOrNull { it.first == question.id }?.second?.trim()?.takeIf { it.isNotBlank() } }
            finally { db.close() }
        }.getOrNull()

        val tokens = Regex("[A-Za-z][A-Za-z-]{3,}")
            .findAll(question.text)
            .map { it.value.lowercase(Locale.US) }
            .distinct()
            .take(8)
            .toList()
        val refs = runCatching { PerformanceManager.refs(app) }.getOrDefault(emptyList())
        val byId = refs.associateBy { it.id }
        val ids = linkedSetOf<Long>()
        tokens.forEach { token ->
            runCatching { AppManagers.renCognitive.searchQuestionIds(token, 20) }
                .getOrDefault(LongArray(0))
                .forEach { id -> if (id != question.id) ids += id }
            if (ids.size >= 20) return@forEach
        }
        val related = runCatching {
            val db = QBankDb(app)
            try {
                ids.take(16).mapNotNull { id ->
                    val ref = byId[id] ?: return@mapNotNull null
                    val q = db.questionById(id) ?: return@mapNotNull null
                    RelatedQuestion(id, ref.testTitle, ref.sourceName, q.text, q.correctAnswer.orEmpty(), null, ref.category)
                }
            } finally { db.close() }
        }.getOrDefault(emptyList())
        val sources = related.map { "${it.exam} / ${it.source}" }.distinct().take(8)
        val pdfEvidence = runCatching { BenPdfResearchStore(app).search(question.text, 6) }.getOrDefault(emptyList())
        val summary = buildString {
            append(related.size).append(" related local questions")
            if (concepts.isNotEmpty()) append(" • ").append(concepts.take(3).joinToString(", "))
            weakest?.let { append(" • weakness: ").append(it).append(" ").append(weakestMastery).append('%') }
            if (note != null) append(" • personal note available")
        }
        return ContextBundle(question, concepts, weakest, weakestMastery, related, sources, note, related.size + concepts.size + if (note != null) 1 else 0, summary, benExamPromptContext(app), pdfEvidence)
    }
}
