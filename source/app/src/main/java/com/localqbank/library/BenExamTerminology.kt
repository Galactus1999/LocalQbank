package com.localqbank.library

import android.content.Context
import java.util.Locale

/**
 * Exam-language layer for Indian PG entrance preparation. This is not a claim about
 * official terminology; it teaches Ben the shorthand students commonly use when querying
 * a QBank, including INI-CET/NEET-PG study vocabulary and common question-reading phrases.
 */
class BenExamTerminology(context: Context) {
    private val app: Context = context.applicationContext
    private val entries: List<Entry> by lazy { load() }
    private val aliasMap: Map<String, Entry> by lazy {
        buildMap<String, Entry> {
            this@BenExamTerminology.entries.forEach { e: Entry ->
                (listOf(e.term) + e.aliases).forEach { alias -> put(normalize(alias), e) }
            }
        }
    }

    data class Entry(
        val term: String,
        val aliases: List<String>,
        val meaning: String,
        val tags: Set<String>
    )

    data class ExamContext(
        val matched: List<Entry>,
        val expanded: List<String>,
        val promptContext: String
    )

    fun understand(query: String): ExamContext {
        val n = normalize(query)
        val found = linkedMapOf<String, Entry>()
        val windows = n.split(' ').filter { it.isNotBlank() }
        for (size in minOf(5, windows.size) downTo 1) {
            for (i in 0..windows.size - size) {
                aliasMap[windows.subList(i, i + size).joinToString(" ")]?.let { found[it.term] = it }
            }
        }
        val expanded = linkedSetOf<String>()
        found.values.forEach { e -> expanded += e.term; expanded += e.aliases.take(5) }
        val prompt = if (found.isEmpty()) "No special exam shorthand detected." else
            found.values.take(8).joinToString("; ") { "${it.term}: ${it.meaning}" }
        return ExamContext(found.values.toList().take(12), expanded.toList().take(32), prompt.take(1800))
    }

    fun glossary(): List<Entry> = entries

    private fun load(): List<Entry> = runCatching {
        app.assets.open("ren/ini_cet_terminology.tsv").bufferedReader().useLines { lines ->
            lines.mapNotNull { raw ->
                if (raw.isBlank() || raw.startsWith('#')) return@mapNotNull null
                val p = raw.split('\t')
                if (p.size < 4) return@mapNotNull null
                Entry(
                    normalize(p[0]),
                    p[1].split('|').map(::normalize).filter { it.isNotBlank() },
                    p[2].trim(),
                    p[3].split('|').map(::normalize).filter { it.isNotBlank() }.toSet()
                )
            }.toList()
        }
    }.getOrDefault(emptyList())

    companion object {
        fun normalize(v: String): String = v.lowercase(Locale.US)
            .replace(Regex("[–—-]"), " ")
            .replace(Regex("[^a-z0-9+/%. ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }
}
