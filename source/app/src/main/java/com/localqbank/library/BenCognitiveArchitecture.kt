package com.localqbank.library

import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ben's lightweight cognitive architecture.
 *
 * This is deliberately model-independent: retrieval, routing, memory, planning and
 * verification continue to work with the neural backend disabled. A future local model
 * is an accelerator behind BenInferenceBackend, never the owner of study state.
 */
class BenCognitiveArchitecture(context: Context, private val deterministic: RenCognitiveEngine = RenCognitiveEngine(context.applicationContext)) {
    private val app = context.applicationContext
    private val knowledge = BenKnowledgeGraph(app)
    private val learner = BenLearnerModel(app)
    private val planner = BenPlanner(knowledge, learner)
    private val verifier = BenAnswerVerifier()
    private val control = BenCognitiveControl(app)
    private val frankenstein = BenFrankensteinEngine(app, deterministic)
    private val experience = BenExperienceMemory(app)
    private val knowledgeIndex = BenLocalKnowledgeIndex(app)

    data class Plan(
        val query: String,
        val intent: String?,
        val concepts: List<String>,
        val domains: Set<String>,
        val tools: List<String>,
        val confidence: Int,
        val specialist: String = "clinical_retrieval",
        val retrievedQuestionCount: Int = 0,
        val indexedEvidenceCount: Int = 0,
        val knowledgeGraphEdges: Int = 0,
        val visualIntent: Boolean = false,
        val examTerms: List<String> = emptyList()
    )

    data class Result(
        val answer: String,
        val plan: Plan,
        val verified: Boolean,
        val confidence: Int,
        val modelUsed: Boolean
    )

    fun answer(
        query: String,
        currentQuestion: Question? = null,
        baseAnswer: String? = null,
        modelUsed: Boolean = false,
        evidence: List<BenAnswerVerifier.EvidenceItem> = emptyList()
    ): Result {
        val clean = BenResearchInputPolicy.normalize(query)
            ?: return Result("Give Ben a clinical term, topic, question, or study task.", Plan("", null, emptyList(), emptySet(), emptyList(), 0), true, 0, false)
        if (!control.cognitiveCoreEnabled) {
            val fallback = baseAnswer?.takeIf { it.isNotBlank() } ?: deterministic.answer(clean, currentQuestion).body
            return Result(BenResponsePolicy.normalize(fallback).orEmpty(), Plan(clean, null, emptyList(), emptySet(), listOf("legacy_local_fallback"), 35), true, 35, false)
        }
        val basePlan = if (control.plannerEnabled) planner.plan(clean, control.knowledgeGraphEnabled) else BenCognitiveArchitecture.Plan(clean, null, emptyList(), emptySet(), emptyList(), 30)
        val retrieval = if (control.retrievalEnabled) frankenstein.retrieve(clean, 12) else BenFrankensteinEngine.Retrieval(LongArray(0), emptyList(), listOf(clean), emptySet())
        val indexed = if (control.knowledgeIndexEnabled) runCatching { knowledgeIndex.retrieve(clean, retrieval.concepts, 8) }.getOrDefault(BenLocalKnowledgeIndex.Retrieval(emptyList(), 0)) else BenLocalKnowledgeIndex.Retrieval(emptyList(), 0)
        if (control.knowledgeIndexEnabled) runCatching { knowledgeIndex.observe(clean, retrieval.concepts, retrieval.domains) }
        val specialist = if (control.specialistRoutingEnabled) frankenstein.specialist(clean) else BenFrankensteinEngine.Specialist("clinical_retrieval", "Clinical retrieval", "Specialist routing disabled")
        val plan = basePlan.copy(
            concepts = if (basePlan.concepts.isNotEmpty()) basePlan.concepts else retrieval.concepts,
            domains = if (basePlan.domains.isNotEmpty()) basePlan.domains else retrieval.domains,
            tools = (basePlan.tools + specialist.id + if (retrieval.questionIds.isNotEmpty()) "local_rag" else "").filter { it.isNotBlank() }.distinct(),
            specialist = specialist.id,
            retrievedQuestionCount = retrieval.questionIds.size,
            indexedEvidenceCount = indexed.evidence.size,
            knowledgeGraphEdges = indexed.edgeCount,
            visualIntent = runCatching { ClinicalKnowledgeLayer(app).understand(clean).visualIntent }.getOrDefault(false),
            examTerms = runCatching { BenExamTerminology(app).understand(clean).matched.map { it.term }.take(8) }.getOrDefault(emptyList()),
            confidence = (basePlan.confidence + (if (retrieval.questionIds.isNotEmpty()) 8 else 0) + indexed.evidence.size.coerceAtMost(4) + (if (indexed.edgeCount > 0) 3 else 0)).coerceIn(0, 95)
        )
        if (control.learnerMemoryEnabled) {
            learner.observeQuery(plan)
            if (currentQuestion != null) learner.observeQuestion(currentQuestion)
        }
        val candidate = baseAnswer?.takeIf { it.isNotBlank() }
            ?: deterministic.answer(clean, currentQuestion).body
        val verification = if (control.verifierEnabled || (modelUsed && evidence.isNotEmpty())) {
            verifier.verify(clean, candidate, plan, evidence)
        } else {
            BenAnswerVerifier.Verification(true, plan.confidence, emptyList(), true)
        }
        // A rejected neural draft is never repaired into a student-facing answer.
        // Repairing an unsupported medical claim would turn a failed safety gate into a
        // false-positive verification. Callers that supplied a neural draft must discard it.
        BenNeuralTelemetry.verifier(verification)
        val rejectedNeural = modelUsed && !verification.acceptable
        val finalResult = if (rejectedNeural) {
            // A failed neural grounding gate is terminal for that draft. Never repair or return
            // the rejected medical prose. Re-enter the deterministic Ren path instead.
            val fallback = runCatching { deterministic.answer(clean, currentQuestion) }.getOrNull()?.body
                ?: "Ben could not safely verify the neural draft; deterministic retrieval is unavailable."
            fallback to false
        } else {
            candidate to modelUsed
        }
        val final = finalResult.first
        val finalModelUsed = finalResult.second
        val confidence = if (rejectedNeural) {
            plan.confidence.coerceIn(0, 100)
        } else {
            ((plan.confidence * 0.7f) + (verification.score * 0.3f)).roundToInt().coerceIn(0, 100)
        }
        val result = Result(BenResponsePolicy.normalize(final).orEmpty(), plan, !rejectedNeural && verification.acceptable || rejectedNeural, confidence, finalModelUsed)
        if (control.experienceMemoryEnabled) {
            runCatching { frankenstein.observe(clean, plan, result) }
            runCatching { experience.record(clean, plan, result) }
        }
        return result
    }

    fun control(): BenCognitiveControl = control

    fun capabilitySummary(): List<String> = listOf(
        "Intent routing", "Clinical concept graph", "Local retrieval planning", "INI-CET/NEET-PG terminology", "Visual-question retrieval", "Learner memory",
        "Adaptive tool selection", "Answer verification", "Offline-first reasoning", "Optional neural accelerator", "Local knowledge index and concept-edge graph"
    )

    fun toolRegistry(): BenToolRegistry = BenToolRegistry(app)
    fun frankenstein(): BenFrankensteinEngine = frankenstein
    fun experience(): BenExperienceMemory = experience
    fun knowledgeIndex(): BenLocalKnowledgeIndex = knowledgeIndex
}

/** Compact concept graph built from the existing clinical lexicon plus learned usage edges. */
class BenKnowledgeGraph(context: Context) {
    private val app = context.applicationContext
    private val clinical = ClinicalKnowledgeLayer(app)

    data class Concept(
        val id: String,
        val label: String,
        val domains: Set<String>,
        val related: List<String>
    )

    fun resolve(query: String, max: Int = 8): List<Concept> = clinical.understand(query).concepts.take(max).map {
        Concept(it.id, it.canonical, it.domains, it.related)
    }

    fun expansions(query: String, max: Int = 32): List<String> = clinical.understand(query).expansions.take(max)

    fun understand(query: String): ClinicalKnowledgeLayer.Understanding = clinical.understand(query)

    fun relationCount(concepts: List<Concept>): Int = concepts.sumOf { it.related.size }.coerceAtMost(128)
}

/**
 * Persistent, bounded learner memory. It stores compact concept-level experience rather than
 * model weights. This makes Ben improve offline without adding RAM-heavy training.
 */
class BenLearnerModel(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ben_learner", Context.MODE_PRIVATE)
    private val clinical = ClinicalKnowledgeLayer(app)

    data class ConceptStats(val seen: Int, val wrong: Int, val correct: Int) {
        val mastery: Int
            get() = if (seen == 0) 0 else ((correct * 100.0) / seen).roundToInt().coerceIn(0, 100)
    }

    fun observeQuery(plan: BenCognitiveArchitecture.Plan) {
        plan.concepts.take(8).forEach { touch(it) }
    }

    fun observeQuestion(question: Question) {
        val understanding = clinical.understand(question.text)
        val progress = runCatching { PerformanceManager.progress(app) }.getOrNull()
        val record = progress?.record(question.stableKey)
        understanding.concepts.take(8).forEach { concept ->
            when (record?.status) {
                "wrong" -> update(concept.canonical, 1, 1, 0)
                "correct" -> update(concept.canonical, 1, 0, 1)
                else -> touch(concept.canonical)
            }
        }
    }

    fun stats(concept: String): ConceptStats {
        val key = key(concept)
        return ConceptStats(prefs.getInt("$key.seen", 0), prefs.getInt("$key.wrong", 0), prefs.getInt("$key.correct", 0))
    }

    fun weakest(concepts: List<String>): String? = concepts.minByOrNull { stats(it).mastery }

    private fun touch(concept: String) = update(concept, 1, 0, 0)

    private fun update(concept: String, seen: Int, wrong: Int, correct: Int) {
        val k = key(concept)
        val old = stats(concept)
        prefs.edit()
            .putInt("$k.seen", (old.seen + seen).coerceAtMost(100_000))
            .putInt("$k.wrong", (old.wrong + wrong).coerceAtMost(100_000))
            .putInt("$k.correct", (old.correct + correct).coerceAtMost(100_000))
            .apply()
    }

    private fun key(value: String): String = "c_${value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(80)}"
}

/** Fast deterministic router: chooses tools before any optional neural inference. */
class BenPlanner(
    private val knowledge: BenKnowledgeGraph,
    private val learner: BenLearnerModel
) {
    fun plan(query: String, knowledgeGraphEnabled: Boolean = true): BenCognitiveArchitecture.Plan {
        val understanding = if (knowledgeGraphEnabled) knowledgeUnderstanding(query) else
            ClinicalKnowledgeLayer.Understanding(query, query, emptyList(), null, emptySet(), emptyList(), false, emptySet(), emptyList())
        val concepts = understanding.concepts.map { it.canonical }.take(8)
        val tools = linkedSetOf<String>()
        when (understanding.intent) {
            "management" -> tools += "management_reasoner"
            "diagnosis_investigation" -> tools += "diagnostic_reasoner"
            "mechanism" -> tools += "mechanism_reasoner"
            "pathology" -> tools += "pathology_reasoner"
            "imaging" -> tools += "imaging_reasoner"
            "genetics" -> tools += "genetics_reasoner"
            "complication_adverse_effect" -> tools += "safety_reasoner"
            "epidemiology" -> tools += "epidemiology_reasoner"
            "association" -> tools += "association_reasoner"
            else -> tools += "clinical_retrieval"
        }
        if (query.contains("wrong", true) || query.contains("mistake", true) || query.contains("weak", true)) tools += "learner_weakness"
        tools += "qbank_retrieval"
        val weakest = learner.weakest(concepts)
        if (weakest != null) {
            tools += "weakness_context"
            if (learner.stats(weakest).wrong >= 2) tools += "misconception_check"
        }
        if (query.contains("trap", true) || query.contains("except", true) || query.contains("incorrect", true) || query.contains("not true", true)) tools += "exam_trap_detector"
        if (query.contains("guideline", true) || query.contains("dose", true) || query.contains("cutoff", true) || query.contains("criteria", true)) tools += "evidence_caution"
        if (understanding.visualIntent) tools += "visual_question_retrieval"
        if (understanding.examTerms.isNotEmpty()) tools += "exam_terminology"
        val relationBoost = (knowledge.relationCount(knowledge.resolve(query)) * 2).coerceAtMost(20)
        val weaknessBoost = weakest?.let { (100 - learner.stats(it).mastery) / 5 } ?: 0
        val confidence = (45 + concepts.size * 6 + relationBoost + weaknessBoost.coerceAtMost(12)).coerceIn(0, 95)
        return BenCognitiveArchitecture.Plan(query, understanding.intent, concepts, understanding.domains, tools.toList(), confidence)
    }

    private fun knowledgeUnderstanding(query: String): ClinicalKnowledgeLayer.Understanding = knowledge.understand(query)
}

/** Compact local episodic memory: recent Ben decisions, not raw answers or source QBank data. */
class BenExperienceMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ben_experience_memory", Context.MODE_PRIVATE)
    private val maxEntries = 32

    data class Entry(val intent: String, val specialist: String, val confidence: Int, val verified: Boolean, val timestamp: Long)

    fun record(query: String, plan: BenCognitiveArchitecture.Plan, result: BenCognitiveArchitecture.Result) {
        val compact = listOf(System.currentTimeMillis(), plan.intent ?: "general", plan.specialist, result.confidence, if (result.verified) 1 else 0, query.take(120).replace('\n', ' ')).joinToString("\t")
        val rows = recentRaw().toMutableList()
        rows += compact
        prefs.edit().putString("rows", rows.takeLast(maxEntries).joinToString("\n")).apply()
    }

    fun recent(limit: Int = 8): List<Entry> = recentRaw().takeLast(limit.coerceIn(1, maxEntries)).mapNotNull { row ->
        val p = row.split('\t')
        if (p.size < 5) null else Entry(p[1], p[2], p[3].toIntOrNull()?.coerceIn(0,100) ?: 0, p[4] == "1", p[0].toLongOrNull() ?: 0L)
    }

    fun summary(): String {
        val rows = recent()
        if (rows.isEmpty()) return "No episodic experience yet."
        val verified = rows.count { it.verified }
        val specialists = rows.groupingBy { it.specialist }.eachCount().entries.sortedByDescending { it.value }.take(3)
        return "Recent ${rows.size} decisions • ${verified}/${rows.size} verified • dominant routes: ${specialists.joinToString { it.key + "×" + it.value }}"
    }

    fun reset() = prefs.edit().clear().apply()
    private fun recentRaw(): List<String> = prefs.getString("rows", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
}

class BenAnswerVerifier {
    /** Stable citation identity carried from the QBank hit through generation and verification. */
    data class EvidenceItem(val index: Int, val hitId: Long? = null, val text: String)

    enum class ClaimStatus { SUPPORTED, UNSUPPORTED_CRITICAL, NOT_APPLICABLE }

    data class ClaimCheck(
        val value: String,
        val context: String,
        val status: ClaimStatus,
        val evidenceIndex: Int? = null
    )

    data class Verification(
        val acceptable: Boolean,
        val score: Int,
        val criticalClaims: List<ClaimCheck> = emptyList(),
        val citationValid: Boolean = true
    )

    private data class NumberToken(
        val raw: String,
        val value: Double,
        val unit: String?,
        val start: Int,
        val end: Int
    )

    private data class NormalizedQuantity(val value: Double, val unit: String?)

    /**
     * Conservative evidence-grounded v1. This is deliberately NOT clinical NLU: it only
     * gates exam-critical numeric claims and critical recommendation phrases against the
     * retrieved QBank evidence. Unknown semantic relationships fail closed.
     */
    fun verify(
        query: String,
        answer: String,
        plan: BenCognitiveArchitecture.Plan,
        evidence: List<EvidenceItem> = emptyList()
    ): Verification {
        if (answer.isBlank()) return Verification(false, 0)
        var score = 45
        val lower = answer.lowercase(Locale.US)
        val conceptMatch = plan.concepts.any { lower.contains(it.lowercase(Locale.US).take(40)) }
        if (plan.concepts.isEmpty() || conceptMatch) score += 20
        if (plan.intent != null) score += 10
        if (conceptMatch) score += 10
        if (answer.length >= 40) score += 5
        if (answer.contains("could not", true) || answer.contains("try again", true)) score -= 20

        // Deterministic/local callers without grounded evidence retain the legacy lightweight gate.
        if (evidence.isEmpty()) return Verification(score >= 50, score.coerceIn(0, 100))

        val validEvidence = evidence.filter { it.index > 0 }.associateBy { it.index }
        val citations = extractCitations(answer)
        val citationValid = citations.all { it in validEvidence.keys }
        if (!citationValid) score -= 35

        val criticalClaims = extractCriticalNumericClaims(answer, validEvidence)
        val unsupported = criticalClaims.filter { it.status == ClaimStatus.UNSUPPORTED_CRITICAL }
        val criticalPhraseWithoutCitation = hasCriticalPhraseWithoutNearbyCitation(answer, validEvidence.keys)
        val unsupportedVisualFinding = plan.visualIntent && hasVisualFindingWithoutNearbyCitation(answer, validEvidence.keys)

        // Fail closed: a critical number that cannot be grounded, an invented citation, or a
        // critical recommendation without a nearby valid citation discards the neural draft.
        var acceptable = score >= 50 && citationValid && unsupported.isEmpty() && !criticalPhraseWithoutCitation && !unsupportedVisualFinding
        if (unsupported.isNotEmpty()) score -= 45
        if (criticalPhraseWithoutCitation) score -= 25
        if (unsupportedVisualFinding) score -= 25
        if (!citationValid) acceptable = false

        return Verification(acceptable, score.coerceIn(0, 100), criticalClaims, citationValid)
    }

    private fun extractCitations(answer: String): Set<Int> =
        Regex("\\[(\\d{1,2})\\]").findAll(answer).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()

    private fun extractCriticalNumericClaims(
        answer: String,
        evidence: Map<Int, EvidenceItem>
    ): List<ClaimCheck> {
        val out = mutableListOf<ClaimCheck>()
        val tokenRegex = Regex("(?<![A-Za-z\\[])(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:\\s*(?:%|mg/kg|g/kg|mcg/kg|µg/kg|mg|mcg|µg|μg|g|kg|ml|mL|l|L|d|day|days|wk|week|weeks|mo|month|months|yr|year|years))?(?![A-Za-z])")
        val criticalRegex = Regex(
            "\\b(age|aged|years? old|dose|dosage|mg|mcg|µg|microgram|milligram|gram|g/kg|mg/kg|ml|%|percent|cut[- ]?off|threshold|criterion|criteria|score|staging|stage|duration|days?|weeks?|months?|years?|interval|screening|first[- ]line|drug of choice|contraindicated|contraindication|mortality|sensitivity|specificity|pregnan(?:cy|t)|trimester|lab(?:oratory)?|hemoglobin|hb|wbc|platelet|creatinine|bilirubin|inr|glucose|pressure)\\b",
            RegexOption.IGNORE_CASE
        )

        for (m in tokenRegex.findAll(answer)) {
            val raw = m.value.replace(Regex("\\s+"), "").trim()
            val numeric = Regex("(?:\\d+(?:\\.\\d+)?|\\.\\d+)").find(raw)?.value?.toDoubleOrNull() ?: continue
            val unit = raw.dropWhile { c -> c.isDigit() || c == '.' }.lowercase(Locale.US).ifBlank { null }
            val token = NumberToken(raw, numeric, unit, m.range.first, m.range.last + 1)
            val contextStart = (token.start - 90).coerceAtLeast(0)
            val contextEnd = (token.end + 90).coerceAtMost(answer.length)
            val context = answer.substring(contextStart, contextEnd).replace(Regex("\\s+"), " ").trim()
            val immediateStart = (token.start - 45).coerceAtLeast(0)
            val immediateEnd = (token.end + 45).coerceAtMost(answer.length)
            val immediateContext = answer.substring(immediateStart, immediateEnd).replace(Regex("\\s+"), " ").trim()
            val isCriticalByUnit = token.unit in setOf("%", "mg", "mcg", "µg", "g", "mg/kg", "g/kg", "mcg/kg", "µg/kg", "kg", "ml", "l", "d", "day", "days", "wk", "week", "weeks", "mo", "month", "months", "yr", "year", "years")
            val isCriticalByCue = criticalRegex.findAll(immediateContext).any { cue ->
                val distance = kotlin.math.min(kotlin.math.abs(token.start - cue.range.first), kotlin.math.abs(token.end - cue.range.last - 1))
                distance <= 45
            }
            if (!isCriticalByUnit && !isCriticalByCue) continue

            val nearbyCitations = extractNearbyCitations(answer, token.start, token.end)
            val candidateEvidence = nearbyCitations.mapNotNull { evidence[it] }
            val searchEvidence = if (candidateEvidence.isNotEmpty()) candidateEvidence else emptyList()
            val matched = searchEvidence.firstOrNull { quantitySupportedNear(token, immediateContext, it.text) }
            if (matched != null) {
                out += ClaimCheck(token.raw, context, ClaimStatus.SUPPORTED, matched.index)
            } else {
                // No citation or no high-confidence numeric+context match: unsupported critical.
                out += ClaimCheck(token.raw, context, ClaimStatus.UNSUPPORTED_CRITICAL, null)
            }
        }

        // Explicit small equivalences: 1/2, 1/4, 3/4 and the words half/quarter.
        // These are only accepted when the evidence contains the same fraction/word or a
        // directly equivalent percentage (50/25/75). No general fraction semantics.
        val fractionRegex = Regex("\\b(1/2|1/4|3/4|half|quarter|three[- ]quarters?)\\b", RegexOption.IGNORE_CASE)
        for (m in fractionRegex.findAll(answer)) {
            val contextStart = (m.range.first - 110).coerceAtLeast(0)
            val contextEnd = (m.range.last + 111).coerceAtMost(answer.length)
            val context = answer.substring(contextStart, contextEnd).replace(Regex("\\s+"), " ").trim()
            if (!criticalRegex.containsMatchIn(context)) continue
            val nearby = extractNearbyCitations(answer, m.range.first, m.range.last + 1)
            val match = nearby.mapNotNull { evidence[it] }.firstOrNull { ev ->
                val e = ev.text.lowercase(Locale.US)
                when (m.value.lowercase(Locale.US)) {
                    "1/2", "half" -> e.contains("1/2") || e.contains("50%") || Regex("\\bhalf\\b").containsMatchIn(e)
                    "1/4", "quarter" -> e.contains("1/4") || e.contains("25%") || Regex("\\bquarter\\b").containsMatchIn(e)
                    else -> e.contains("3/4") || e.contains("75%") || Regex("three[- ]quarters?").containsMatchIn(e)
                }
            }
            out += ClaimCheck(m.value, context, if (match != null) ClaimStatus.SUPPORTED else ClaimStatus.UNSUPPORTED_CRITICAL, match?.index)
        }
        return out
    }

    private fun extractNearbyCitations(answer: String, start: Int, end: Int): Set<Int> {
        val from = (start - 100).coerceAtLeast(0)
        val to = (end + 100).coerceAtMost(answer.length)
        return extractCitations(answer.substring(from, to))
    }

    private fun hasVisualFindingWithoutNearbyCitation(answer: String, validEvidence: Set<Int>): Boolean {
        val visualRegex = Regex(
            "\\b(image|mammogram|mammography|scan|ct|mri|x[- ]?ray|ultrasound|slide|micrograph|histology|pathology)\\b.{0,45}\\b(shows?|demonstrates?|reveals?|consistent with|suggests?|evidence of)\\b",
            RegexOption.IGNORE_CASE
        )
        for (m in visualRegex.findAll(answer)) {
            val from = (m.range.first - 100).coerceAtLeast(0)
            val to = (m.range.last + 101).coerceAtMost(answer.length)
            val nearby = extractCitations(answer.substring(from, to))
            if (nearby.none { it in validEvidence }) return true
        }
        return false
    }

    private fun hasCriticalPhraseWithoutNearbyCitation(answer: String, validEvidence: Set<Int>): Boolean {
        val phraseRegex = Regex(
            "\\b(drug of choice|first[- ]line|contraindicated|diagnostic criteria|diagnostic criterion|cut[- ]off|threshold|screening (?:age|interval|recommendation)|recommended (?:at|from)|stage [ivx]+|mortality|sensitivity|specificity)\\b",
            RegexOption.IGNORE_CASE
        )
        for (m in phraseRegex.findAll(answer)) {
            val from = (m.range.first - 100).coerceAtLeast(0)
            val to = (m.range.last + 101).coerceAtMost(answer.length)
            val nearby = extractCitations(answer.substring(from, to))
            if (nearby.none { it in validEvidence }) return true
        }
        return false
    }

    /**
     * High-precision quantity matching only. A value must have the same/explicitly convertible
     * unit AND a nearby critical-context token must also occur near that value in the evidence.
     */
    private fun quantitySupportedNear(token: NumberToken, answerContext: String, evidenceText: String): Boolean {
        val target = normalizeQuantity(token.value, token.unit) ?: return false
        val contextTerms = criticalContextTerms(answerContext)
        val tokenRegex = Regex("(?<![A-Za-z\\[])(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:\\s*(?:%|mg/kg|g/kg|mcg/kg|µg/kg|mg|mcg|µg|μg|g|kg|ml|mL|l|L|d|day|days|wk|week|weeks|mo|month|months|yr|year|years))?(?![A-Za-z])")
        return tokenRegex.findAll(evidenceText).any { match ->
            val raw = match.value.replace(Regex("\\s+"), "").trim()
            val number = Regex("(?:\\d+(?:\\.\\d+)?|\\.\\d+)").find(raw)?.value?.toDoubleOrNull() ?: return@any false
            val unit = raw.dropWhile { c -> c.isDigit() || c == '.' }.lowercase(Locale.US).ifBlank { null }
            val candidate = normalizeQuantity(number, unit) ?: return@any false
            val direct = compatible(candidate, target)
            val ageUnitlessEquivalent = target.unit == "years" && candidate.unit == null &&
                contextTerms.any { it == "age" || it == "aged" || it == "year" } && nearlyEqual(candidate.value, target.value)
            val percentFractionEquivalent = target.unit == "%" && candidate.unit == null &&
                ((nearlyEqual(candidate.value, 0.5) && nearlyEqual(target.value, 50.0)) ||
                    (nearlyEqual(candidate.value, 0.25) && nearlyEqual(target.value, 25.0)) ||
                    (nearlyEqual(candidate.value, 0.75) && nearlyEqual(target.value, 75.0)))
            if (!direct && !ageUnitlessEquivalent && !percentFractionEquivalent) return@any false
            if (contextTerms.isEmpty()) return@any false
            val eFrom = (match.range.first - 110).coerceAtLeast(0)
            val eTo = (match.range.last + 111).coerceAtMost(evidenceText.length)
            val eContext = evidenceText.substring(eFrom, eTo).lowercase(Locale.US)
            contextTerms.any { term -> eContext.contains(term) }
        }
    }

    private fun criticalContextTerms(context: String): List<String> {
        val keywords = listOf(
            "age", "aged", "year", "dose", "dosage", "cut-off", "cutoff", "threshold", "criterion",
            "criteria", "score", "stage", "staging", "duration", "day", "week", "month", "interval",
            "screening", "first-line", "first line", "drug of choice", "contraindicated", "mortality",
            "sensitivity", "specificity", "pregnancy", "pregnant", "trimester", "hemoglobin", "wbc",
            "platelet", "creatinine", "bilirubin", "inr", "glucose", "pressure"
        )
        val lower = context.lowercase(Locale.US)
        return keywords.filter { lower.contains(it) }.distinct().take(4)
    }

    /** Explicitly small conversion table; deliberately no open-ended clinical equivalence. */
    private fun normalizeQuantity(value: Double, unit: String?): NormalizedQuantity? {
        val u = unit?.lowercase(Locale.US)?.replace("μ", "µ")
        return when (u) {
            null -> NormalizedQuantity(value, null)
            "%" -> NormalizedQuantity(value, "%")
            "mg" -> NormalizedQuantity(value, "mg")
            "g" -> NormalizedQuantity(value * 1000.0, "mg")
            "mcg", "µg" -> NormalizedQuantity(value / 1000.0, "mg")
            "mg/kg" -> NormalizedQuantity(value, "mg/kg")
            "g/kg" -> NormalizedQuantity(value * 1000.0, "mg/kg")
            "mcg/kg", "µg/kg" -> NormalizedQuantity(value / 1000.0, "mg/kg")
            "kg" -> NormalizedQuantity(value, "kg")
            "ml" -> NormalizedQuantity(value, "ml")
            "l" -> NormalizedQuantity(value * 1000.0, "ml")
            "d", "day", "days" -> NormalizedQuantity(value, "days")
            "wk", "week", "weeks" -> NormalizedQuantity(value * 7.0, "days")
            "mo", "month", "months" -> NormalizedQuantity(value * 30.0, "days")
            "yr", "year", "years" -> NormalizedQuantity(value, "years")
            else -> null
        }
    }

    private fun compatible(a: NormalizedQuantity, b: NormalizedQuantity): Boolean {
        if (a.unit == null || b.unit == null) return a.unit == b.unit && nearlyEqual(a.value, b.value)
        return a.unit == b.unit && nearlyEqual(a.value, b.value)
    }

    private fun nearlyEqual(a: Double, b: Double): Boolean =
        kotlin.math.abs(a - b) <= 0.0005 * kotlin.math.max(1.0, kotlin.math.max(kotlin.math.abs(a), kotlin.math.abs(b)))

    /** Legacy helper retained for deterministic callers; never used to rescue rejected neural drafts. */
    fun repair(query: String, candidate: String, plan: BenCognitiveArchitecture.Plan): String = buildString {
        append(candidate.take(12000).trim())
        if (plan.concepts.isNotEmpty()) {
            append("\n\nBen checked this against local concepts: ")
            append(plan.concepts.take(4).joinToString(", "))
            append(".")
        }
        append("\n\nExam safety: verify the original QBank explanation when the question depends on a specific guideline, dose, cutoff, or image.")
    }
}


/** Deterministic tools exposed to Ben's planner. Tools never perform network I/O. */
class BenToolRegistry(private val context: Context) {
    data class Tool(val id: String, val description: String, val safeOffline: Boolean = true)

    fun tools(): List<Tool> = listOf(
        Tool("qbank_retrieval", "Search imported QBank question and explanation content"),
        Tool("clinical_retrieval", "Normalize medical concepts and expand related terminology"),
        Tool("learner_weakness", "Inspect local weak/wrong concept signals"),
        Tool("management_reasoner", "Prioritize treatment/management intent"),
        Tool("diagnostic_reasoner", "Prioritize diagnostic/investigation intent"),
        Tool("mechanism_reasoner", "Prioritize mechanism/pathophysiology intent"),
        Tool("study_planner", "Create a local adaptive study action"),
        Tool("flashcard_context", "Use existing flashcard/SRS context without auto-creating cards"),
        Tool("misconception_check", "Flag repeated local wrong-answer concept signals"),
        Tool("exam_trap_detector", "Detect common MCQ wording traps and negative stems"),
        Tool("evidence_caution", "Require source verification for dose, cutoff and guideline-sensitive claims"),
        Tool("association_reasoner", "Prioritize association/risk-factor reasoning"),
        Tool("epidemiology_reasoner", "Prioritize frequency, prevalence and commonest-pattern reasoning"),
        Tool("visual_question_retrieval", "Hard-filter QBank questions that contain actual imported images, then rank by subject/concept"),
        Tool("exam_terminology", "Interpret INI-CET/NEET-PG student shorthand, image-bank language and MCQ wording")
    )

    fun execute(toolId: String, query: String, limit: Int = 50): String? = runCatching {
        when (toolId) {
            "qbank_retrieval" -> {
                val ids = RenCognitiveEngine(context).searchQuestionIds(query, limit)
                "Matched ${ids.size} local question(s)."
            }
            "learner_weakness" -> AppManagers.adaptive.studyStrategy()
            "study_planner" -> AppManagers.adaptive.studyStrategy()
            else -> null
        }
    }.getOrNull()
}
