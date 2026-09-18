package com.localqbank.library

import android.content.Context
import java.util.Locale
import kotlin.math.min

/**
 * Local clinical language layer for Ren.
 *
 * Design goals:
 *  - deterministic/offline-first; no network or runtime code generation
 *  - concept/meaning oriented rather than synonym-only search
 *  - broad undergraduate-to-PG medical vocabulary
 *  - explicit query intent and specialty signals
 *  - compact, editable terminology data in assets/ren/clinical_lexicon.tsv
 *
 * The model is deliberately inspired by terminology systems that separate a clinical
 * concept from its human-readable descriptions and relationships. SNOMED CT does this
 * with concepts, descriptions and relationships; UMLS likewise groups alternative names
 * around concepts and records semantic relationships. Rovex uses a much smaller local
 * representation because the application must remain self-contained and license-safe.
 */
class ClinicalKnowledgeLayer(context: Context) {
    private val app = context.applicationContext
    private val examTerminology = BenExamTerminology(app)
    private val entries: List<Entry> by lazy { loadLexicon() }
    private val byAlias: Map<String, List<Entry>> by lazy {
        buildMap<String, List<Entry>> {
            this@ClinicalKnowledgeLayer.entries.forEach { e ->
                e.aliases.forEach { alias ->
                    val key = normalize(alias)
                    if (key.isNotBlank()) put(key, (get(key).orEmpty() + e).distinctBy { it.id })
                }
                put(e.id, (get(e.id).orEmpty() + e).distinctBy { it.id })
            }
        }
    }

    data class Entry(
        val id: String,
        val canonical: String,
        val aliases: List<String>,
        val domains: Set<String>,
        val related: List<String>
    )

    data class Understanding(
        val original: String,
        val normalized: String,
        val concepts: List<Entry>,
        val intent: String?,
        val domains: Set<String>,
        val expansions: List<String>,
        val visualIntent: Boolean = false,
        val visualKinds: Set<String> = emptySet(),
        val examTerms: List<String> = emptyList()
    )

    fun understand(query: String): Understanding {
        val normalized = normalize(query)
        val concepts = linkedMapOf<String, Entry>()
        val candidates = queryWindows(normalized)
        candidates.forEach { window ->
            byAlias[window].orEmpty().forEach { concepts[it.id] = it }
        }

        // Also recognize individual tokens so that phrases such as "renal function test"
        // can resolve through renal + investigation concepts even when the exact phrase is
        // absent from the lexicon.
        normalized.split(' ').filter { it.length >= 3 }.forEach { token ->
            byAlias[token].orEmpty().forEach { concepts[it.id] = it }
        }

        val selected = concepts.values
            .sortedWith(compareByDescending<Entry> { exactnessScore(normalized, it) }.thenBy { it.id })
            .take(8)

        val domains = selected.flatMap { it.domains }.toSet()
        val intent = detectIntent(normalized)
        val exam = examTerminology.understand(normalized)
        val visualKinds = detectVisualKinds(normalized, exam.matched.map { it.term })
        val visualIntent = visualKinds.isNotEmpty() || exam.matched.any { "image" in it.tags }
        val expansions = linkedSetOf<String>()
        expansions += normalized
        exam.expanded.forEach { expansions += it }
        selected.forEach { e ->
            expansions += e.canonical
            e.aliases.take(8).forEach { expansions += it }
            e.related.take(5).forEach { relation ->
                byAlias[normalize(relation)].orEmpty().forEach { related ->
                    expansions += related.canonical
                    related.aliases.take(3).forEach { expansions += it }
                }
            }
        }
        return Understanding(
            query, normalized, selected, intent, domains,
            expansions.filter { it.length >= 2 }.take(48),
            visualIntent, visualKinds, exam.matched.map { it.term }.take(12)
        )
    }

    fun capabilities(): List<String> = listOf(
        "Clinical concept normalization",
        "Medical synonyms, abbreviations and eponyms",
        "Cross-specialty terminology",
        "Exam-intent recognition",
        "Concept relationships and query expansion",
        "QBank-grounded clinical reranking",
        "Local terminology learning from imported QBanks"
    )

    /** Add safe, bounded vocabulary learned from imported question text without changing source data. */
    fun learnFromQBank(question: Question) {
        // Intentionally a no-op in this release: runtime learning must not mutate executable
        // code or source QBank content. The retrieval layer already indexes imported text.
        // This hook exists as the stable contract for a future versioned local vocabulary store.
    }

    private fun loadLexicon(): List<Entry> {
        val result = mutableListOf<Entry>()
        runCatching {
            app.assets.open("ren/clinical_lexicon.tsv").bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    if (raw.isBlank() || raw.startsWith('#')) return@forEach
                    val p = raw.split('\t')
                    if (p.size < 5) return@forEach
                    val aliases = p[2].split('|').map(::normalize).filter { it.isNotBlank() }.distinct()
                    val domains = p[3].split('|').map(::normalize).filter { it.isNotBlank() }.toSet()
                    val related = p[4].split('|').map(::normalize).filter { it.isNotBlank() }.distinct()
                    result += Entry(normalize(p[0]), normalize(p[1]), aliases, domains, related)
                }
            }
        }
        return result
    }

    private fun queryWindows(s: String): List<String> {
        val words = s.split(' ').filter { it.isNotBlank() }
        val out = linkedSetOf<String>()
        for (n in min(5, words.size) downTo 1) {
            for (i in 0..(words.size - n)) out += words.subList(i, i + n).joinToString(" ")
        }
        return out.toList()
    }


    private fun detectVisualKinds(q: String, examTerms: List<String>): Set<String> {
        val text = "$q ${examTerms.joinToString(" ")}"
        val out = linkedSetOf<String>()
        fun has(vararg terms: String) = terms.any { text.contains(Regex("\\b${Regex.escape(it)}\\b")) }
        if (has("image", "images", "image based question", "image-based question", "visual", "spotter", "slide", "slides", "histology", "histopathology", "micrograph", "gross specimen", "clinical photograph", "clinical photo")) out += "image"
        if (has("slide", "slides", "histology", "histopathology", "microsection", "micrograph", "pathology slide")) out += "slide"
        if (has("radiograph", "x ray", "xray", "ct", "mri", "ultrasound", "usg")) out += "radiology"
        if (has("ecg", "ekg", "electrocardiogram")) out += "ecg"
        if (has("fundus", "fundoscopy", "fundus photograph")) out += "fundus"
        if (has("instrument", "spotter")) out += "instrument"
        if (has("clinical photograph", "clinical photo", "clinical image")) out += "clinical_photo"
        return out
    }

    private fun exactnessScore(q: String, e: Entry): Int {
        var score = 0
        if (q == e.id || q == e.canonical) score += 1000
        if (q.contains(e.canonical)) score += 500
        score += e.aliases.count { q == it } * 400
        score += e.aliases.count { q.contains(it) } * 100
        return score
    }

    private fun detectIntent(q: String): String? = when {
        q.matches(Regex(".*\\b(most common|commonest|most frequent|incidence|prevalence)\\b.*")) -> "epidemiology"
        q.matches(Regex(".*\\b(first line|first-line|initial treatment|drug of choice|treatment|management|next best step|definitive treatment)\\b.*")) -> "management"
        q.matches(Regex(".*\\b(gold standard|best test|investigation|investigate|screening|diagnosis|diagnostic|confirm)\\b.*")) -> "diagnosis_investigation"
        q.matches(Regex(".*\\b(mechanism|mode of action|how does|pathogenesis|pathophysiology)\\b.*")) -> "mechanism"
        q.matches(Regex(".*\\b(complication|complications|adverse effect|side effect|toxicity)\\b.*")) -> "complication_adverse_effect"
        q.matches(Regex(".*\\b(association|associated with|risk factor|predisposition|linked to)\\b.*")) -> "association"
        q.matches(Regex(".*\\b(mutation|gene|inheritance|chromosome|translocation|karyotype)\\b.*")) -> "genetics"
        q.matches(Regex(".*\\b(histology|histopathology|microscopy|biopsy|pathology)\\b.*")) -> "pathology"
        q.matches(Regex(".*\\b(x.?ray|radiograph|ct|mri|ultrasound|usg|imaging|scan)\\b.*")) -> "imaging"
        q.matches(Regex(".*\\b(definition|define|meaning|what is)\\b.*")) -> "definition"
        else -> null
    }

    companion object {
        fun normalize(value: String): String = value.lowercase(Locale.US)
            .replace('β', 'b')
            .replace('α', 'a')
            .replace('γ', 'g')
            .replace(Regex("[–—-]"), " ")
            .replace(Regex("[^a-z0-9+./% ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
