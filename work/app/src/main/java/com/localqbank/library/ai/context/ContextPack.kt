package com.localqbank.library.ai.context

import com.localqbank.library.BenExamProfile
import com.localqbank.library.Question

/**
 * Shared, bounded question context contract for every AI surface.
 * It is transport-neutral: deterministic Ben, EmbeddingGemma retrieval, Gemma generation,
 * external providers and search can consume the same semantic packet without rebuilding it.
 */
data class ContextPack(
    val examProfile: BenExamProfile,
    val questionId: Long,
    val question: String,
    val options: List<OptionContext>,
    val selectedOption: String?,
    val keyedAnswer: String?,
    val qbankExplanation: String?,
    val learnerNote: String? = null,
    val task: String = "Explain the question and distinguish the options."
) {
    data class OptionContext(val label: String, val text: String)

    fun bounded(maxQuestionChars: Int = 6000, maxOptionChars: Int = 1800, maxExplanationChars: Int = 3500): ContextPack =
        copy(
            question = question.take(maxQuestionChars),
            options = options.take(8).map { it.copy(label = it.label.take(20), text = it.text.take(maxOptionChars)) },
            selectedOption = selectedOption?.take(40),
            keyedAnswer = keyedAnswer?.take(500),
            qbankExplanation = qbankExplanation?.take(maxExplanationChars),
            learnerNote = learnerNote?.take(1200)
        )

    fun toPromptBlock(): String = buildString {
        append("EXAM PROFILE: ").append(examProfile.label).append('\n')
        append(examProfile.promptHint).append("\n\nQUESTION:\n").append(question).append("\n\nOPTIONS:\n")
        options.forEach { append(it.label).append(". ").append(it.text).append('\n') }
        append("\nSTUDENT SELECTED OPTION: ").append(selectedOption ?: "Not answered").append('\n')
        append("KEYED ANSWER (authoritative QBank value): ").append(keyedAnswer.orEmpty()).append('\n')
        qbankExplanation?.let { append("\nQBANK EXPLANATION:\n").append(it).append('\n') }
        learnerNote?.let { append("\nSTUDENT NOTE:\n").append(it).append('\n') }
        append("\nTASK: ").append(task)
    }

    companion object {
        fun fromQuestion(question: Question, examProfile: BenExamProfile, selectedOption: String? = null, learnerNote: String? = null): ContextPack =
            ContextPack(
                examProfile = examProfile,
                questionId = question.id,
                question = question.text,
                options = question.options.map { OptionContext(it.label, it.text) },
                selectedOption = selectedOption,
                keyedAnswer = question.correctAnswer,
                qbankExplanation = question.explanation,
                learnerNote = learnerNote
            ).bounded()
    }
}
