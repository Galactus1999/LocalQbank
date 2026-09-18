package com.localqbank.library.ai.context

/** Explicit evidence contract. Only ANSWER_EVIDENCE may become citation-authoritative. */
data class EvidencePack(
    val questionEvidence: List<Item> = emptyList(),
    val answerEvidence: List<Item> = emptyList(),
    val distractorEvidence: List<Item> = emptyList()
) {
    data class Item(val sourceId: Long, val text: String, val score: Double = 0.0)

    fun bounded(maxEach: Int = 4, maxChars: Int = 5000): EvidencePack {
        var remaining = maxChars.coerceAtLeast(0)
        fun clip(items: List<Item>): List<Item> {
            val out = ArrayList<Item>(maxEach)
            for (item in items.take(maxEach)) {
                if (remaining <= 0) break
                val text = item.text.replace(Regex("\\s+"), " ").trim().take(remaining)
                if (text.isNotBlank()) {
                    out += item.copy(text = text)
                    remaining -= text.length
                }
            }
            return out
        }
        return EvidencePack(clip(questionEvidence), clip(answerEvidence), clip(distractorEvidence))
    }

    fun authoritativeAnswerEvidence(): List<Item> = answerEvidence
}

internal fun com.localqbank.library.BenContrastiveEvidence.Bundle.toEvidencePack(): EvidencePack = EvidencePack(
    questionEvidence = question.map { EvidencePack.Item(it.sourceId, it.text, it.score) },
    answerEvidence = answers.map { EvidencePack.Item(it.sourceId, it.text, it.score) },
    distractorEvidence = distractors.map { EvidencePack.Item(it.sourceId, it.text, it.score) }
).bounded()
