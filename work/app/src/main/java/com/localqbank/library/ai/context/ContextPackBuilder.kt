package com.localqbank.library.ai.context

import android.content.Context
import com.localqbank.library.Question
import com.localqbank.library.BenQuestionAiMode
import com.localqbank.library.FrankensteinContextEngine
import com.localqbank.library.currentBenExamProfile

/** Single builder used by UI/AI adapters so context construction cannot silently diverge. */
class ContextPackBuilder(private val context: Context) {
    fun forQuestion(question: Question, selectedOption: String? = null, learnerNote: String? = null): ContextPack =
        ContextPack.fromQuestion(question, currentBenExamProfile(context), selectedOption, learnerNote)

    /** Builds the mode-specific evidence without making the UI a second context owner. */
    fun questionAiEvidence(question: Question, mode: BenQuestionAiMode): String = try {
        FrankensteinContextEngine(context).questionAiEvidence(question, mode)
    } catch (_: Exception) {
        "QUESTION AI MODE: ${mode.label}\n${mode.prompt}\n\nLOCAL EVIDENCE: unavailable; do not invent provenance."
    }

    fun forQuery(query: String, task: String = "Answer the study query using only supplied evidence."): ContextPack =
        ContextPack(
            examProfile = currentBenExamProfile(context),
            questionId = 0L,
            question = query.take(6000),
            options = emptyList(),
            selectedOption = null,
            keyedAnswer = null,
            qbankExplanation = null,
            task = task
        ).bounded()
}
