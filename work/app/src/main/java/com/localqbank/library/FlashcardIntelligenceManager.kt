package com.localqbank.library

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Local flashcard intelligence. Cards are generated only on explicit user request.
 * A wrong answer never creates a flashcard automatically. Content is question-first, option-aware and answer-only
 * on reveal; the original QBank is never modified.
 *
 * The scheduling store supports four recall ratings and is kept separate from content
 * generation. This follows the modern FSRS-style separation of card content and review
 * scheduling while retaining the app's deterministic offline storage contract.
 */
class FlashcardIntelligenceManager(context: Context) {
    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "flashcard-intelligence") }

    data class Result(val created: Int, val skipped: Int, val deckName: String)
    data class EngineStats(val generatedDeckPrefix: String = "Manual", val available: Boolean = true)

    fun stats(): EngineStats = EngineStats()

    fun createFromQuestions(ids: LongArray, reason: String = "Manual"): Result {
        if (ids.isEmpty()) return Result(0, 0, "")
        return runCatching {
            val qdb = QBankDb(app)
            val fdb = FlashcardDb(app)
            try {
                val questions = ids.distinct().take(100).mapNotNull { qdb.questionById(it) }
                val deckName = fdb.normalizeDeckName("Rovex :: ${if (reason.isBlank()) "Manual" else reason.take(36)}")
                val deckId = fdb.upsertDeckForImport(deckName, "Rovex Manual")
                var created = 0
                var skipped = 0
                questions.forEach { q ->
                    val front = buildFront(q)
                    val answer = correctOption(q)
                    if (front.isBlank() || answer.isBlank()) { skipped++; return@forEach }
                    val back = answer
                    val tags = buildString {
                        append("manual")
                        if (reason.isNotBlank()) append(',').append(reason.take(32).replace(Regex("\\s+"), "-"))
                        if (q.options.size > 0) append(",mcq")
                    }
                    if (fdb.upsertGeneratedCard(deckId, front, back, tags)) created++ else skipped++
                }
                fdb.setDeckCount(deckId, fdb.cardCount(deckId))
                Result(created, skipped, deckName)
            } finally {
                qdb.close(); fdb.close()
            }
        }.getOrDefault(Result(0, ids.size, ""))
    }

    fun createFromQuestionsAsync(ids: LongArray, reason: String = "Manual", onDone: (Result) -> Unit) {
        executor.execute {
            val result = createFromQuestions(ids, reason)
            Handler(Looper.getMainLooper()).post { onDone(result) }
        }
    }

    private fun buildFront(q: Question): String {
        val stem = plain(q.text).trim()
        if (stem.isBlank()) return ""
        val options = q.options.mapNotNull { option ->
            val text = plain(option.text).trim()
            if (text.isBlank()) null else "${option.label}. $text"
        }
        return buildString {
            append(stem.take(1400))
            if (options.isNotEmpty()) {
                append("\n\n")
                options.forEach { append(it.take(500)).append('\n') }
            }
        }.trim()
    }

    private fun correctOption(q: Question): String {
        val flagged = q.options.firstOrNull { it.correct }
        if (flagged != null) return "${flagged.label}. ${plain(flagged.text).trim()}".trim()
        val answer = q.correctAnswer.orEmpty().trim()
        if (answer.isBlank()) return ""
        val label = answer.substringBefore('.').substringBefore(')').trim()
        val byLabel = q.options.firstOrNull { it.label.equals(label, true) }
        return if (byLabel != null) "${byLabel.label}. ${plain(byLabel.text).trim()}" else plain(answer).take(700)
    }

    private fun plain(html: String): String = android.text.Html
        .fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()
}
