package com.localqbank.library

import android.content.Context

/**
 * Compatibility facade for the public intelligence entry point.
 *
 * Routing ownership intentionally lives in [RenIntelligenceOrchestrator] (BIO). This class
 * keeps the older API stable for callers while preventing a second copy of intent routing,
 * wrong-question selection, and capability policy from evolving independently.
 */
class IntelligenceOrchestrator(
    context: Context,
    private val router: RenIntelligenceOrchestrator = RenIntelligenceOrchestrator(context.applicationContext)
) {
    data class Plan(
        val command: String,
        val questionIds: LongArray,
        val explanation: String,
        val orchestrated: Boolean = false
    )

    fun plan(command: String, limit: Int = 50): Plan {
        val routed = router.plan(command, limit)
        return Plan(
            command = routed.command,
            questionIds = routed.questionIds,
            explanation = routed.explanation,
            orchestrated = routed.accepted || routed.capability != null
        )
    }

    fun respond(command: String, currentQuestion: Question? = null): RenCognitiveEngine.Insight =
        router.cognitiveAnswer(command, currentQuestion)

    fun createFlashcardsFromPlan(
        plan: Plan,
        reason: String = "Dr. Frankenstein plan"
    ): FlashcardIntelligenceManager.Result =
        AppManagers.flashcardIntelligence.createFromQuestions(plan.questionIds, reason)

    fun captureKnowledge(
        question: Question,
        reason: String = "Dr. Frankenstein capture"
    ): KnowledgeEngineManager.Result =
        AppManagers.knowledge.captureQuestion(question, reason)
}
