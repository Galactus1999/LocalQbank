package com.localqbank.library

/**
 * Canonical mode-specific evidence policy for the Question AI panel.
 * It never invents provenance: PYQ is restricted to rows explicitly labelled as PYQ.
 * PYT is broader because the local corpus may encode previous-year/topic/test information
 * in test/source metadata without a dedicated PYT flag.
 */
class BenQuestionAiContextPolicy {
    fun evidence(question: Question, context: FrankensteinContextEngine.ContextBundle, mode: BenQuestionAiMode): String =
        buildString {
            append("QUESTION AI MODE: ").append(mode.label).append('\n')
            append("MODE PROMPT: ").append(mode.prompt).append("\n\n")
            when (mode) {
                BenQuestionAiMode.PYQ_CONTEXT -> appendPyq(context)
                BenQuestionAiMode.PYT_CONTEXT -> appendPyt(context)
                BenQuestionAiMode.FUTURE_RELATED -> appendFuture(context)
                BenQuestionAiMode.OTHER_OPTIONS -> appendOtherOptions(question)
            }
        }.take(MAX_EVIDENCE_CHARS)

    private fun StringBuilder.appendPyq(context: FrankensteinContextEngine.ContextBundle) {
        val rows = context.relatedQuestions.filter { it.hasExplicitPyqProvenance() }
        append("CURRENT QUESTION + PREVIOUS RELATED PYQ EVIDENCE\n")
        append("EXPLICIT PYQ ROWS: ").append(rows.size).append("\n")
        if (rows.isEmpty()) {
            append("No locally retrieved question has explicit PYQ provenance. Do not invent PYQ status. If local evidence is absent, Free AI may take the lead and must clearly label any non-local claim.\n")
            return
        }
        val topicTokens = rows.flatMap { Regex("[A-Za-z][A-Za-z-]{4,}").findAll(it.text.lowercase()).map { m -> m.value }.toList() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10)
        append("REPEATED SPECIAL POINTS / WORDING THEMES: ").append(topicTokens.joinToString(" • ") { "${it.key} ×${it.value}" }.ifBlank { "none" }).append("\n")
        append("REPEAT COUNT: ").append(rows.size).append(" explicitly related PYQ rows retrieved; this is a local-corpus count, not an exam-frequency estimate.\n")
        rows.take(MAX_ROWS).forEachIndexed { i, row -> appendRow("PYQ-${i + 1}", row) }
    }

    private fun StringBuilder.appendPyt(context: FrankensteinContextEngine.ContextBundle) {
        val explicit = context.relatedQuestions.filter { it.hasExplicitPytProvenance() }
        val rows = (explicit + context.relatedQuestions.filterNot { it.hasExplicitPytProvenance() }).distinctBy { it.id }
        append("WHOLE-TOPIC PYT MAP • SAME CONCEPT, DIFFERENT QUESTION FORMS\n")
        append("RELATED PREVIOUS-YEAR/TOPIC/TEST ROWS: ").append(rows.size).append("\n")
        if (rows.isEmpty()) {
            append("No related local rows were retrieved. If local context is absent, let Free AI take the lead and clearly separate sourced facts from general teaching.\n")
            return
        }
        rows.take(MAX_ROWS).forEachIndexed { i, row -> appendRow("PYT-${i + 1}", row) }
        if (context.pdfEvidence.isNotEmpty()) {
            append("REFERENCE-PDF PAGES: ").append(context.pdfEvidence.size).append("\n")
            context.pdfEvidence.take(4).forEachIndexed { i, hit -> append("[PDF-${i + 1}] page ").append(hit.page).append(" • ").append(hit.text.take(1800)).append("\n") }
        }
        append("Teach the whole topic in structured sections; highlight lines that were explicitly tested in the retrieved corpus. Do not invent previous-year status.\n")
    }

    private fun StringBuilder.appendFuture(context: FrankensteinContextEngine.ContextBundle) {
        append("FUTURE-RELATED EVIDENCE BASE: ").append(context.relatedQuestions.size).append(" local rows\n")
        context.relatedQuestions.take(MAX_ROWS).forEachIndexed { i, row ->
            append("[RELATED-${i + 1}] ").append(row.exam.ifBlank { "Unknown test" })
                .append(" / ").append(row.source.ifBlank { "Unknown source" }).append('\n')
            append("STEM: ").append(row.text.take(700)).append('\n')
            append("KEY: ").append(row.answer.take(220)).append("\n")
        }
        append("For each plausible question, provide the answer and the exam trap. Add a separate RECENT UPDATES section only when supported by current reliable information; otherwise say that current updates require Free AI/Google Search verification. Never present possibilities as predictions.\n")
    }

    private fun StringBuilder.appendOtherOptions(question: Question) {
        val key = question.correctAnswer.orEmpty().trim()
        append("AUTHORITATIVE KEY: ").append(key.ifBlank { "not supplied" }).append('\n')
        append("ALL OPTIONS / DISTRACTOR ANALYSIS INPUT:\n")
        question.options.forEach { option ->
            val keyed = isKeyed(option, key)
            append("[").append(if (keyed) "KEYED" else "DISTRACTOR").append("] ")
                .append(option.label).append(". ").append(option.text.take(1800)).append('\n')
        }
        question.explanation?.takeIf { it.isNotBlank() }?.let {
            append("QBANK EXPLANATION:\n").append(it.take(3000)).append('\n')
        }
        append("For every distractor, show how the same option could be reframed into a valid question: altered stem/clue, correct answer, and the discriminating fact.\n")
    }

    private fun StringBuilder.appendRow(prefix: String, row: FrankensteinContextEngine.RelatedQuestion) {
        append('[').append(prefix).append("] ")
            .append(row.exam.ifBlank { "Unknown test" }).append(" / ")
            .append(row.source.ifBlank { "Unknown source" }).append('\n')
        append("QUESTION: ").append(row.text.take(850)).append('\n')
        append("KEY: ").append(row.answer.take(260)).append('\n')
    }

    private fun FrankensteinContextEngine.RelatedQuestion.hasExplicitPyqProvenance(): Boolean =
        metadataText().containsToken("pyq")

    private fun FrankensteinContextEngine.RelatedQuestion.hasExplicitPytProvenance(): Boolean =
        metadataText().containsToken("pyt") || metadataText().contains("previous year") || metadataText().contains("previous-year")

    private fun FrankensteinContextEngine.RelatedQuestion.metadataText(): String =
        "${exam.lowercase()} ${source.lowercase()} ${category.lowercase()}"

    private fun String.containsToken(token: String): Boolean =
        Regex("(^|[^a-z0-9])${Regex.escape(token)}([^a-z0-9]|$)").containsMatchIn(this)

    private fun isKeyed(option: Option, key: String): Boolean {
        if (key.isBlank()) return false
        val k = key.trim().lowercase()
        val label = option.label.trim().lowercase()
        val text = option.text.trim().lowercase()
        return k == label || k == "${label}." || k == text ||
            k.startsWith("$label ") || k.startsWith("$label.") || text == k.substringAfter(".", "").trim()
    }

    companion object {
        private const val MAX_ROWS = 8
        private const val MAX_EVIDENCE_CHARS = 12_000
    }
}
