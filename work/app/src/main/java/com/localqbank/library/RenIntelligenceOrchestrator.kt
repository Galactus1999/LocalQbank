package com.localqbank.library

import android.content.Context
import android.view.View
import java.util.concurrent.ConcurrentHashMap

/**
 * Ren Intelligence Orchestrator (BIO).
 *
 * Control-plane only: it routes bounded, registered capabilities to specialist engines.
 * It never edits executable code or the source QBank at runtime. New capabilities are
 * introduced through versioned contracts, then exposed to BIO through this registry.
 */
class RenIntelligenceOrchestrator(context: Context, private val cognitive: RenCognitiveEngine = RenCognitiveEngine(context.applicationContext)) {
    private val app = context.applicationContext
    private val enabled = ConcurrentHashMap<String, Boolean>()

    data class Plan(
        val command: String,
        val capability: CapabilityId?,
        val questionIds: LongArray = longArrayOf(),
        val explanation: String = "",
        val accepted: Boolean = false
    )

    init { CapabilityRegistry.all.forEach { enabled.putIfAbsent(it.id.key, true) } }

    fun cognitiveAnswer(command: String, currentQuestion: Question? = null): RenCognitiveEngine.Insight = cognitive.answer(command, currentQuestion)

    fun cognitiveCapabilities(): List<String> = cognitive.capabilities()

    fun isComplex(command: String): Boolean {
        val t = command.lowercase()
        val verbs = listOf("create", "make", "build", "organise", "organize", "summarise", "summarize", "then", "and then", "from my")
        return verbs.count { t.contains(it) } >= 2 || t.contains(" and ") && (t.contains("wrong") || t.contains("flashcard") || t.contains("note"))
    }

    fun capabilities(): List<CapabilityDescriptor> = CapabilityRegistry.all.filter { enabled[it.id.key] != false }.map { it.descriptor }

    fun setCapabilityEnabled(id: CapabilityId, value: Boolean) { enabled[id.key] = value }

    /** Simple actions can bypass orchestration; BIO is reserved for multi-step plans. */
    fun shouldOrchestrate(command: String): Boolean = isComplex(command)
    fun isCapabilityEnabled(id: CapabilityId): Boolean = enabled[id.key] != false

    fun plan(command: String, limit: Int = 50): Plan {
        val text = command.trim()
        if (text.isBlank()) return Plan(text, null, explanation = "Tell Dr. Frankenstein what you want to study or save.")
        val lower = text.lowercase()
        val cap = when {
            "screenshot" in lower && ("explanation" in lower || "answer" in lower) -> CapabilityId.CAPTURE_EXPLANATION
            "flashcard" in lower || "flash cards" in lower -> CapabilityId.GENERATE_FLASHCARDS
            "note" in lower || "summary" in lower || "table" in lower || "image" in lower -> CapabilityId.CAPTURE_KNOWLEDGE
            "wrong" in lower || "mistake" in lower -> CapabilityId.PRIORITIZE_WRONG
            "subject" in lower || "topic" in lower -> CapabilityId.SUBJECT_FOCUS
            else -> CapabilityId.STUDY_MIX
        }
        if (!isCapabilityEnabled(cap)) return Plan(text, cap, explanation = "That capability is currently disabled.")
        return when (cap) {
            CapabilityId.PRIORITIZE_WRONG -> {
                val ids = AppManagers.studyIntelligence.smartMix(limit).filter { id ->
                    val ref = PerformanceManager.refs(app).firstOrNull { it.id == id } ?: return@filter false
                    PerformanceManager.progress(app).record(ref.stableKey)?.status == "wrong"
                }.take(limit.coerceIn(1, 100)).toLongArray()
                Plan(text, cap, ids, "Prioritised your wrong questions.", true)
            }
            CapabilityId.SUBJECT_FOCUS -> {
                val match = AppManagers.studyIntelligence.matchSubject(text, limit)
                Plan(text, cap, match.ids, AppManagers.studyIntelligence.explain(text, match.matched), true)
            }
            else -> Plan(text, cap, explanation = "Capability selected: ${cap.key}", accepted = true)
        }
    }

    fun execute(plan: Plan, question: Question? = null, explanationView: View? = null): String {
        if (!plan.accepted || plan.capability == null) return plan.explanation
        if (!AppManagers.guardian.allow(plan.capability)) return "Action paused by the safety guardian."
        return runCatching {
            when (plan.capability) {
                CapabilityId.GENERATE_FLASHCARDS -> {
                    val ids = if (plan.questionIds.isNotEmpty()) plan.questionIds else AppManagers.studyIntelligence.smartMix(25)
                    val r = AppManagers.flashcardIntelligence.createFromQuestions(ids, "Dr. Frankenstein")
                    "Created ${r.created} flashcards; skipped ${r.skipped}."
                }
                CapabilityId.CAPTURE_KNOWLEDGE -> {
                    val q = question ?: return@runCatching "Open a question before creating a knowledge capture."
                    val r = AppManagers.knowledge.captureQuestion(q, "Dr. Frankenstein")
                    "Saved auto-note • ${r.tablesSaved} tables • ${r.imagesSaved} images."
                }
                CapabilityId.CAPTURE_EXPLANATION -> {
                    val q = question ?: return@runCatching "Open a question before capturing its explanation."
                    val view = explanationView ?: return@runCatching "The explanation is not currently visible."
                    val file = AppManagers.knowledge.captureExplanationView(view, q.id)
                    if (file != null) "Explanation screenshot saved." else "Could not capture the explanation safely."
                }
                CapabilityId.PRIORITIZE_WRONG, CapabilityId.SUBJECT_FOCUS, CapabilityId.STUDY_MIX -> plan.explanation
            }
        }.getOrElse { "Action failed safely: ${it.javaClass.simpleName}." }
    }
}

enum class CapabilityId(val key: String) {
    STUDY_MIX("study_mix"), SUBJECT_FOCUS("subject_focus"), PRIORITIZE_WRONG("prioritize_wrong"),
    GENERATE_FLASHCARDS("generate_flashcards"), CAPTURE_KNOWLEDGE("capture_knowledge"),
    CAPTURE_EXPLANATION("capture_explanation")
}

data class CapabilityDescriptor(val id: CapabilityId, val name: String, val owner: String, val mutates: String)
data class CapabilityRegistration(val id: CapabilityId, val descriptor: CapabilityDescriptor)

object CapabilityRegistry {
    val all: List<CapabilityRegistration> = listOf(
        CapabilityRegistration(CapabilityId.STUDY_MIX, CapabilityDescriptor(CapabilityId.STUDY_MIX, "Adaptive Smart Mix", "Study Intelligence", "session selection only")),
        CapabilityRegistration(CapabilityId.SUBJECT_FOCUS, CapabilityDescriptor(CapabilityId.SUBJECT_FOCUS, "Subject Focus", "Study Intelligence", "session selection only")),
        CapabilityRegistration(CapabilityId.PRIORITIZE_WRONG, CapabilityDescriptor(CapabilityId.PRIORITIZE_WRONG, "Wrong Question Prioritisation", "Study Intelligence", "session selection only")),
        CapabilityRegistration(CapabilityId.GENERATE_FLASHCARDS, CapabilityDescriptor(CapabilityId.GENERATE_FLASHCARDS, "Generate Flashcards", "Flashcard Intelligence", "derived flashcard data")),
        CapabilityRegistration(CapabilityId.CAPTURE_KNOWLEDGE, CapabilityDescriptor(CapabilityId.CAPTURE_KNOWLEDGE, "Auto Knowledge Capture", "Knowledge Engine", "derived notes/tables/images")),
        CapabilityRegistration(CapabilityId.CAPTURE_EXPLANATION, CapabilityDescriptor(CapabilityId.CAPTURE_EXPLANATION, "Explanation Screenshot", "Knowledge Engine", "derived image only"))
    )
}

/** Policy gate between BIO and execution engines. */
class IntelligenceGuardian(context: Context) {
    private val app = context.applicationContext
    fun allow(id: CapabilityId): Boolean {
        if (PerformanceManager.isSafeMode() && id == CapabilityId.GENERATE_FLASHCARDS) return false
        return try {
            val available = AppManagers.isReady()
            available && PerformanceManager.healthScore() >= 35
        } catch (_: Exception) { false }
    }
    fun snapshot(): String = "health=${PerformanceManager.healthScore()} safeMode=${PerformanceManager.isSafeMode()}"
}
