package com.localqbank.library

/**
 * Phase-3 evidence container. Evidence roles are explicit so distractors can never silently
 * become authoritative answer evidence. This is retrieval metadata only; clinical truth remains
 * the canonical QBank content plus Ben's verifier.
 */
internal object BenContrastiveEvidence {
    enum class Role { QUESTION_EVIDENCE, DISTRACTOR_EVIDENCE, ANSWER_EVIDENCE }

    data class Item(
        val role: Role,
        val sourceId: Long,
        val text: String,
        val score: Double = 0.0,
    )

    data class Bundle(
        val question: List<Item> = emptyList(),
        val distractors: List<Item> = emptyList(),
        val answers: List<Item> = emptyList(),
    ) {
        fun bounded(maxQuestion: Int = 4, maxDistractors: Int = 4, maxAnswers: Int = 4): Bundle =
            Bundle(
                question = question.filter { it.role == Role.QUESTION_EVIDENCE }.take(maxQuestion.coerceAtLeast(0)),
                distractors = distractors.filter { it.role == Role.DISTRACTOR_EVIDENCE }.take(maxDistractors.coerceAtLeast(0)),
                answers = answers.filter { it.role == Role.ANSWER_EVIDENCE }.take(maxAnswers.coerceAtLeast(0)),
            )

        fun all(): List<Item> = question + distractors + answers

        fun totalChars(): Int = all().sumOf { it.text.length }
    }
}
