package com.localqbank.library

import android.content.Context
import kotlin.math.min

/**
 * Offline-first cognitive layer for Ren.
 *
 * Ren is intentionally split into clinical-language understanding and QBank retrieval.
 * The clinical layer normalizes medical language, detects exam intent, expands related
 * concepts and supplies specialty signals. Retrieval remains grounded in the local QBank.
 */
class RenCognitiveEngine(context: Context) {
    private val app = context.applicationContext
    private val clinical = ClinicalKnowledgeLayer(app)

    data class Insight(val title: String, val body: String, val actions: List<String> = emptyList())

    fun answer(command: String, currentQuestion: Question? = null): Insight {
        val text = command.trim()
        if (text.isBlank()) return Insight("Dr. Frankenstein", "Tell me what you want to study, save, summarise or practise.")
        val lower = text.lowercase()
        return when {
            currentQuestion != null && ("summar" in lower || "explain" in lower || "key point" in lower || "high yield" in lower) -> summarise(currentQuestion)
            "flashcard" in lower || "flash card" in lower ->
                Insight("Flashcards", "I will only create cards when you explicitly ask. Each card keeps the MCQ on the front and reveals only the correct answer option.", listOf("Create flashcards"))
            "note" in lower || "summary" in lower ->
                Insight("Knowledge", "I can turn the current question into a structured note, extracting high-yield points instead of copying the whole explanation.", listOf("Create auto-note"))
            "wrong" in lower || "mistake" in lower -> {
                val ids = AppManagers.studyIntelligence.smartMix(30)
                Insight("Weakness review", "I prepared a local adaptive pool of ${ids.size} questions, prioritising due and weak items.", listOf("Start adaptive mix"))
            }
            "what should i study" in lower || "what should i revise" in lower || "study plan" in lower ->
                Insight("Study plan", AppManagers.adaptive.studyStrategy(), listOf("Open Study Tools"))
            else -> searchQBank(text)
        }
    }

    private fun searchQBank(command: String): Insight {
        val query = cleanSearchQuery(command)
        if (query.length < 2) return Insight("Dr. Frankenstein", "I need a little more detail. Try a subject, topic, or exact clinical term.")
        val understanding = clinical.understand(query)
        val ordered = searchQuestionRefs(understanding, 100)
        if (ordered.isNotEmpty()) {
            val concepts = understanding.concepts.take(4).joinToString(", ") { it.canonical }
            val intent = understanding.intent?.replace('_', ' ')
            val interpretation = buildString {
                if (concepts.isNotBlank()) append("Clinical concepts: $concepts. ")
                if (!intent.isNullOrBlank()) append("Question intent: $intent. ")
                if (understanding.visualIntent) append("Visual filter: actual imported question/explanation image required; image-related wording in the stem is not required. ")
                if (understanding.domains.isNotEmpty()) append("Dr. Frankenstein prioritised clinical subject/context before QBank provenance labels.")
            }.trim()
            val examples = ordered.take(5).map { "Q${it.position + 1} • ${it.testTitle}" }
            return Insight(
                "Dr. Frankenstein clinical search",
                "Found ${ordered.size} relevant local question${if (ordered.size == 1) "" else "s"}.\n\n$interpretation\n\n" + examples.joinToString("\n") + "\n\nSearch is grounded in the actual local question/explanation content; unrelated subject or QBank labels are deliberately down-weighted.",
                listOf("Open matching questions")
            )
        }
        val hint = when {
            understanding.concepts.isNotEmpty() -> "I recognised ${understanding.concepts.first().canonical}, but the local QBank did not contain a strong content match."
            else -> "I could not confidently map that phrase to a clinical concept in Dr. Frankenstein's local terminology layer."
        }
        return Insight("No strong QBank match", "$hint Try a broader clinical term, abbreviation, eponym, investigation, drug, disease or exam-intent phrase.")
    }

    fun searchQuestionIds(command: String, limit: Int = 100): LongArray {
        val q = cleanSearchQuery(command)
        return searchQuestionRefs(clinical.understand(q), limit).map { it.id }.toLongArray()
    }

    /** Public diagnostic hook for future Ren UI/debug screens without exposing the ontology itself. */
    fun understand(command: String): ClinicalKnowledgeLayer.Understanding = clinical.understand(cleanSearchQuery(command))

    private fun cleanSearchQuery(command: String): String = command
        .replace(Regex("(?i)\\b(find|search|show|give|open|questions?|qbank|topic|subject|about|please|ren)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun searchQuestionRefs(understanding: ClinicalKnowledgeLayer.Understanding, limit: Int): List<QuestionRef> {
        val query = understanding.normalized
        if (query.length < 2) return emptyList()
        if (understanding.visualIntent) {
            return searchVisualQuestionRefs(understanding, limit)
        }
        val variants = understanding.expansions.take(24)
        val ranked = linkedMapOf<Long, Int>()
        val strictPrimary = linkedSetOf<Long>()
        val hitById = linkedMapOf<Long, SearchHit>()
        val db = QBankDb(app)
        try {
            db.ensureSearchIndex()
            variants.forEachIndexed { variantIndex, variant ->
                db.search(variant, 500).forEach { hit ->
                    hitById.putIfAbsent(hit.id, hit)
                    val qText = hit.text.lowercase()
                    val explanationAware = "${hit.text} ${hit.testTitle} ${hit.path.orEmpty()} ${hit.sourceName}".lowercase()
                    val normalizedVariant = ClinicalKnowledgeLayer.normalize(variant)
                    val words = normalizedVariant.split(' ', '+', '/')
                        .filter { it.length >= 2 }.distinct()
                    val contentHits = words.count { qText.contains(it) }
                    val exactContent = normalizedVariant.length >= 3 && qText.contains(normalizedVariant)
                    val anyTextHit = explanationAware.contains(normalizedVariant)
                    val primaryTokens = understanding.normalized.split(' ', '+', '/')
                        .filter { it.length >= 2 }.distinct()
                    val primaryCoverage = primaryTokens.count { qText.contains(it) }
                    val primaryPhrase = primaryTokens.size >= 2 && qText.contains(understanding.normalized)

                    var score = 24 - variantIndex
                    score += contentHits * 70
                    if (exactContent) score += 420
                    else if (anyTextHit) score += 20
                    if (contentHits == 0) score -= 80

                    // For multi-word requests, the actual stem must carry the requested
                    // concept before a result can outrank a true content match. This prevents
                    // queries such as "cervical cancer" from returning every unrelated cancer
                    // merely because the word "cancer" appears in an explanation/metadata.
                    if (primaryTokens.size >= 2) {
                        if (primaryPhrase) score += 1500
                        else if (primaryCoverage == primaryTokens.size) score += 900
                        else if (primaryCoverage == 0) score -= 900
                        else score -= 500
                        if (primaryCoverage == primaryTokens.size) strictPrimary += hit.id
                    }

                    // Concept-aware scoring: a recognized clinical phrase in the actual stem
                    // is worth much more than a generic word in a source/test title.
                    understanding.concepts.forEach { concept ->
                        val canonical = concept.canonical
                        if (qText.contains(canonical)) score += 260
                        concept.aliases.take(12).forEach { alias -> if (alias.length >= 3 && qText.contains(alias)) score += 115 }
                    }

                    // Exam-intent terms are useful only when they occur in the actual content.
                    understanding.intent?.replace('_', ' ')?.let { intent ->
                        if (qText.contains(intent)) score += 100
                    }

                    // Prevent generic metadata from turning an unrelated subject into the top
                    // result. A domain match is a modest boost; a clear unrelated domain is a
                    // penalty, never a hard exclusion because QBanks can be cross-disciplinary.
                    if (understanding.domains.isNotEmpty()) {
                        val meta = ClinicalKnowledgeLayer.normalize("${hit.testTitle} ${hit.path.orEmpty()} ${hit.sourceName}")
                        val matched = understanding.domains.count { meta.contains(it) }
                        val knownOther = setOf("radiology", "pediatrics", "pediatric", "obstetrics", "gynecology", "surgery", "medicine", "pharmacology", "pathology", "microbiology", "anatomy", "physiology", "biochemistry", "psychiatry", "neurology", "cardiology", "nephrology", "dermatology", "ent", "ophthalmology", "orthopedics", "community medicine", "anesthesia", "oncology", "endocrinology", "gastroenterology")
                        val other = knownOther.count { meta.contains(it) && !understanding.domains.contains(it) }
                        score += matched * 18
                        score -= min(42, other * 7)
                    }
                    ranked[hit.id] = maxOf(ranked[hit.id] ?: Int.MIN_VALUE, score)
                }
            }
        } finally { db.close() }
        val ordered = ranked.entries.sortedByDescending { it.value }
        val primaryTokenCount = understanding.normalized.split(' ', '+', '/').count { it.length >= 2 }
        // Multi-word clinical searches are strict by design: if the actual question stem
        // does not contain every requested primary token, do not surface it merely because
        // a generic token (e.g. "cancer") matched. This is especially important for
        // queries such as "cervical cancer".
        val source = if (primaryTokenCount >= 2) {
            if (strictPrimary.isNotEmpty()) ordered.filter { strictPrimary.contains(it.key) } else emptyList()
        } else ordered
        return source.mapNotNull { hitById[it.key]?.let { hit ->
            QuestionRef(hit.id, hit.stableKey, hit.testId, hit.position, hit.text, hit.testTitle, 0L, hit.sourceName, hit.path.orEmpty())
        } }.take(limit.coerceIn(1, 200))
    }

    /** Hard visual retrieval path: image ownership is a database constraint, not a keyword. */
    private fun searchVisualQuestionRefs(understanding: ClinicalKnowledgeLayer.Understanding, limit: Int): List<QuestionRef> {
        val db = QBankDb(app)
        val ranked = linkedMapOf<Long, Int>()
        val hitById = linkedMapOf<Long, SearchHit>()
        val conceptTerms = linkedSetOf<String>()
        understanding.concepts.take(8).forEach { concept -> conceptTerms += concept.canonical; conceptTerms += concept.aliases.take(4) }
        understanding.domains.filter { it.length >= 3 }.forEach { conceptTerms += it }
        val stripped = understanding.normalized
            .replace(Regex("(?i)\\b(images?|image[- ]based|visual|related|questions?|question|slide|slides|spotter|spotters|micrograph|microsection|clinical photo(graph)?|gross specimen|show|find|search|give|all|of|for|the|about|please)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (stripped.length >= 2) conceptTerms += stripped
        val queries = conceptTerms.filter { it.length >= 2 }.take(16)
        try {
            db.ensureSearchIndex()
            queries.forEachIndexed { qi, term ->
                db.searchImageQuestions(term, 500).forEach { hit ->
                    hitById.putIfAbsent(hit.id, hit)
                    val content = hit.text.lowercase()
                    val meta = "${hit.testTitle} ${hit.path.orEmpty()} ${hit.sourceName}".lowercase()
                    val t = ClinicalKnowledgeLayer.normalize(term)
                    var score = 120 - qi
                    if (t.length >= 3 && content.contains(t)) score += 420
                    if (t.length >= 3 && meta.contains(t)) score += 260
                    understanding.concepts.forEach { concept ->
                        val canonical = concept.canonical
                        if (content.contains(canonical)) score += 220
                        if (meta.contains(canonical)) score += 320
                        concept.aliases.take(10).forEach { alias ->
                            if (alias.length >= 3 && content.contains(alias)) score += 80
                            if (alias.length >= 3 && meta.contains(alias)) score += 110
                        }
                    }
                    if (understanding.visualKinds.contains("slide") &&
                        (meta.contains("pathology") || meta.contains("histology") || meta.contains("histopath"))) score += 180
                    ranked[hit.id] = maxOf(ranked[hit.id] ?: Int.MIN_VALUE, score)
                }
            }
        } finally { db.close() }
        val ordered = ranked.entries.sortedByDescending { it.value }
        return ordered.mapNotNull { hitById[it.key]?.let { hit ->
            QuestionRef(hit.id, hit.stableKey, hit.testId, hit.position, hit.text, hit.testTitle, 0L, hit.sourceName, hit.path.orEmpty())
        } }.take(limit.coerceIn(1, 500))
    }

    fun summarise(q: Question): Insight {
        val answer = plain(q.correctAnswer ?: q.options.firstOrNull { it.correct }?.label.orEmpty())
        val exp = plain(q.explanation.orEmpty())
        val points = extractKeyPoints(exp)
        val body = buildString {
            if (answer.isNotBlank()) append("Answer: ").append(answer.take(260)).append("\n\n")
            append("Key points\n")
            if (points.isEmpty()) append("Review the original explanation for the full context.")
            else points.forEach { append("• ").append(it).append('\n') }
        }.trim()
        return Insight("Dr. Frankenstein's local summary", body, listOf("Save as note", "Create flashcard"))
    }

    fun extractKeyPoints(text: String): List<String> {
        val clean = plain(text)
        if (clean.isBlank()) return emptyList()
        val sentences = clean.split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim() }.filter { it.length >= 35 }
        val markers = listOf("most common", "important", "first line", "gold standard", "diagnosis", "treatment", "management", "contraindication", "adverse", "associated", "classically", "hallmark", "preferred", "mcq", "exam")
        return sentences.sortedWith(compareByDescending<String> { s -> markers.count { s.contains(it, true) } }.thenByDescending { s -> min(s.length, 220) }).distinct().take(7).map { it.take(220) }
    }

    fun capabilities(): List<String> = clinical.capabilities() + listOf(
        "Question summarisation", "Key-point extraction", "Adaptive study planning", "Bounded engine actions"
    )

    private fun plain(html: String): String = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
}
