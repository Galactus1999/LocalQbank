package com.localqbank.library

import android.content.Context
import java.util.Locale

/**
 * Builds the bounded "Frankenstein Context" envelope used by Ben and Gemini.
 *
 * It is retrieval-only: it never changes QBank truth, progress, SRS or notes. The QBank remains
 * authoritative; this layer assembles useful context around the current question so the cloud
 * model does not have to rediscover the student's local corpus from scratch.
 */
class FrankensteinContextEngine(context: Context) {
    private val app = context.applicationContext
    private val learner = BenLearnerModel(app)

    data class RelatedQuestion(
        val id: Long,
        val exam: String,
        val source: String,
        val text: String,
        val answer: String
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
        val summary: String
    ) {
        fun promptBlock(): String = buildString {
            append("FRANKENSTEIN CONTEXT — LOCAL EVIDENCE ONLY\n")
            append("This envelope was assembled from Rovex's local QBank, learner memory and notes.\n")
            append("Never claim that a row is a true PYQ or that an exam repeated a question unless its local provenance explicitly establishes that.\n\n")
            append("CONCEPTS: ").append(concepts.joinToString(", ").ifBlank { "not confidently mapped" }).append('\n')
            weakestConcept?.let { append("LEARNER WEAKNESS: $it (${weakestMastery ?: 0}% local mastery estimate)\n") }
            personalNote?.let { append("STUDENT NOTE: ").append(it.take(1200)).append('\n') }
            append("RELATED LOCAL QUESTIONS: ").append(relatedQuestions.size).append('\n')
            relatedQuestions.forEachIndexed { i, row ->
                append("[LOCAL-${i + 1}] EXAM/SOURCE: ${row.exam.ifBlank { "Unknown" }} | ${row.source.ifBlank { "Unknown source" }}\n")
                append("QUESTION: ${row.text.take(900)}\nKEY: ${row.answer.take(260)}\n")
            }
            if (relatedSources.isNotEmpty()) append("LOCAL SOURCE CLUSTERS: ${relatedSources.joinToString(" • ")}\n")
        }
    }

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
                    RelatedQuestion(id, ref.testTitle, ref.sourceName, q.text, q.correctAnswer.orEmpty())
                }
            } finally { db.close() }
        }.getOrDefault(emptyList())
        val sources = related.map { "${it.exam} / ${it.source}" }.distinct().take(8)
        val summary = buildString {
            append(related.size).append(" related local questions")
            if (concepts.isNotEmpty()) append(" • ").append(concepts.take(3).joinToString(", "))
            weakest?.let { append(" • weakness: ").append(it).append(" ").append(weakestMastery).append('%') }
            if (note != null) append(" • personal note available")
        }
        return ContextBundle(question, concepts, weakest, weakestMastery, related, sources, note, related.size + concepts.size + if (note != null) 1 else 0, summary)
    }
}
