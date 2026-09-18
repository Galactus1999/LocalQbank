package com.localqbank.library

import android.content.Context
import kotlin.math.roundToInt

/**
 * Ben's deterministic orchestration layer. It adds retrieval, specialist routing and bounded
 * experience memory without requiring a neural model. It never owns study scheduling or DB state.
 */
class BenFrankensteinEngine(context: Context, private val ren: RenCognitiveEngine = RenCognitiveEngine(context.applicationContext)) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ben_frankenstein", Context.MODE_PRIVATE)
    private val clinical = ClinicalKnowledgeLayer(app)

    data class Retrieval(
        val questionIds: LongArray,
        val concepts: List<String>,
        val expansions: List<String>,
        val domains: Set<String>
    )

    data class Specialist(
        val id: String,
        val label: String,
        val rationale: String
    )

    fun retrieve(query: String, limit: Int = 12): Retrieval {
        val understanding = runCatching { clinical.understand(query) }
            .getOrElse { ClinicalKnowledgeLayer.Understanding(query, query, emptyList(), null, emptySet(), listOf(query), false, emptySet(), emptyList()) }
        val expanded = understanding.expansions.take(12)
        val ids = linkedSetOf<Long>()
        expanded.take(6).forEach { term ->
            runCatching { ren.searchQuestionIds(term, limit.coerceIn(1, 20)) }
                .getOrNull()?.forEach { ids += it }
            if (ids.size >= limit) return@forEach
        }
        return Retrieval(
            ids.take(limit.coerceIn(1, 50)).toLongArray(),
            understanding.concepts.map { it.canonical }.take(8),
            expanded,
            understanding.domains
        )
    }

    fun specialist(query: String): Specialist {
        val understanding = runCatching { clinical.understand(query) }.getOrNull()
        val intent = understanding?.intent
        return when (intent) {
            "management" -> Specialist("management_reasoner", "Management specialist", "Treatment/next-best-step intent detected")
            "diagnosis_investigation" -> Specialist("diagnostic_reasoner", "Diagnostic specialist", "Diagnosis/investigation intent detected")
            "mechanism" -> Specialist("mechanism_reasoner", "Mechanism specialist", "Mechanism/pathophysiology intent detected")
            "pathology" -> Specialist("pathology_reasoner", "Pathology specialist", "Pathology/histology intent detected")
            "imaging" -> Specialist("imaging_reasoner", "Imaging specialist", "Imaging intent detected")
            "genetics" -> Specialist("genetics_reasoner", "Genetics specialist", "Genetic intent detected")
            "complication_adverse_effect" -> Specialist("safety_reasoner", "Complication/adverse-effect specialist", "Complication/toxicity intent detected")
            "epidemiology" -> Specialist("epidemiology_reasoner", "Epidemiology specialist", "Frequency/prevalence intent detected")
            else -> Specialist("clinical_retrieval", "Clinical retrieval specialist", "General clinical retrieval path")
        }
    }

    fun observe(query: String, plan: BenCognitiveArchitecture.Plan, result: BenCognitiveArchitecture.Result) {
        val now = System.currentTimeMillis()
        val old = prefs.getLong("observations", 0L)
        val success = prefs.getLong("verified", 0L)
        prefs.edit()
            .putLong("observations", (old + 1).coerceAtMost(100_000L))
            .putLong("verified", (success + if (result.verified) 1 else 0).coerceAtMost(100_000L))
            .putString("last_intent", plan.intent ?: "general")
            .putString("last_specialist", specialist(query).id)
            .putString("last_concepts", plan.concepts.take(6).joinToString(" • "))
            .putLong("last_observed_at", now)
            .apply()
    }

    fun stats(): Stats = Stats(
        observations = prefs.getLong("observations", 0L),
        verified = prefs.getLong("verified", 0L),
        lastIntent = prefs.getString("last_intent", "general") ?: "general",
        lastSpecialist = prefs.getString("last_specialist", "clinical_retrieval") ?: "clinical_retrieval",
        lastConcepts = prefs.getString("last_concepts", "") ?: ""
    )

    data class Stats(
        val observations: Long,
        val verified: Long,
        val lastIntent: String,
        val lastSpecialist: String,
        val lastConcepts: String
    ) {
        val verificationRate: Int
            get() = if (observations == 0L) 0 else ((verified * 100.0) / observations).roundToInt().coerceIn(0, 100)
    }

    fun reset() = prefs.edit().clear().apply()
}

/** Human-readable specialist registry shown in Adaptive Engine. */
object BenSpecialistRegistry {
    data class Entry(val id: String, val label: String, val scope: String)

    fun entries(): List<Entry> = listOf(
        Entry("clinical_retrieval", "Clinical retrieval", "Terminology, concepts and local QBank grounding"),
        Entry("management_reasoner", "Management", "Treatment, first-line therapy and next-best-step questions"),
        Entry("diagnostic_reasoner", "Diagnosis", "Investigations, screening and diagnostic reasoning"),
        Entry("mechanism_reasoner", "Mechanism", "Pathophysiology and pharmacologic mechanism"),
        Entry("pathology_reasoner", "Pathology", "Histology, pathology and morphology"),
        Entry("imaging_reasoner", "Imaging", "Radiology and imaging-oriented questions"),
        Entry("genetics_reasoner", "Genetics", "Genes, mutations, inheritance and cytogenetics"),
        Entry("safety_reasoner", "Complication / safety", "Adverse effects, toxicity and complications"),
        Entry("epidemiology_reasoner", "Epidemiology", "Incidence, prevalence and commonest patterns")
    )
}
