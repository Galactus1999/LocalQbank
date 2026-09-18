package com.localqbank.library

/**
 * Deterministic Phase-3 contrastive evidence planner.
 *
 * Retrieval metadata only. It does not decide clinical truth and never replaces
 * BenAnswerVerifier. The strongest retrieved candidates are separated from lower-ranked
 * distractor context so neural generation can reason contrastively without promoting every hit.
 */
internal object BenContrastiveEvidencePlanner {
    data class Candidate(val sourceId: Long, val text: String, val score: Double = 0.0)

    fun plan(
        query: String,
        candidates: List<Candidate>,
        maxAnswer: Int = 4,
        maxDistractor: Int = 4,
        maxTotalChars: Int = 5_000
    ): BenContrastiveEvidence.Bundle {
        val answerLimit = maxAnswer.coerceAtLeast(0)
        val distractorLimit = maxDistractor.coerceAtLeast(0)
        val charBudget = maxTotalChars.coerceAtLeast(0)
        val normalized = candidates.asSequence()
            .filter { it.text.isNotBlank() }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.sourceId })
            .distinctBy { it.sourceId }
            .toList()
        var remaining = charBudget
        val question = query.takeIf { it.isNotBlank() }?.let {
            val clipped = clean(it).take(remaining.coerceAtLeast(0))
            remaining -= clipped.length
            listOf(BenContrastiveEvidence.Item(BenContrastiveEvidence.Role.QUESTION_EVIDENCE, -1L, clipped, 1.0))
        }.orEmpty()
        fun takeRole(items: List<Candidate>, limit: Int, role: BenContrastiveEvidence.Role): List<BenContrastiveEvidence.Item> {
            if (limit <= 0 || remaining <= 0) return emptyList()
            val out = ArrayList<BenContrastiveEvidence.Item>(limit)
            for (candidate in items) {
                if (out.size >= limit || remaining <= 0) break
                val text = clean(candidate.text)
                if (text.isBlank()) continue
                val clipped = text.take(remaining)
                if (clipped.isBlank()) continue
                out += BenContrastiveEvidence.Item(role, candidate.sourceId, clipped, candidate.score)
                remaining -= clipped.length
            }
            return out
        }
        val answers = takeRole(normalized, answerLimit, BenContrastiveEvidence.Role.ANSWER_EVIDENCE)
        val usedIds = answers.map { it.sourceId }.toSet()
        val distractors = takeRole(normalized.filterNot { it.sourceId in usedIds }, distractorLimit, BenContrastiveEvidence.Role.DISTRACTOR_EVIDENCE)
        return BenContrastiveEvidence.Bundle(question, distractors, answers)
            .bounded(maxQuestion = 1, maxDistractors = distractorLimit, maxAnswers = answerLimit)
    }

    private fun clean(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(700)
}
