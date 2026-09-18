package com.localqbank.library

import android.content.Context
import kotlin.math.max

/**
 * Study Intelligence layer: deterministic, local and explainable.
 * It converts a user's natural subject/topic text into a ranked question pool.
 * No network service or opaque remote model is required.
 */
class StudyIntelligenceManager(context: Context, private val subjectFocus: SubjectFocusSearchEngine) {
    private val app = context.applicationContext

    data class SubjectMatch(val ids: LongArray, val matched: Int, val subject: String)

    fun matchSubject(subject: String, limit: Int = Int.MAX_VALUE): SubjectMatch {
        val q = subject.trim()
        if (q.isBlank()) return SubjectMatch(longArrayOf(), 0, q)
        val safeLimit = limit.coerceIn(1, 500)
        val result = subjectFocus.searchSync(q, safeLimit)
        return SubjectMatch(result.ids, result.matched, q)
    }


    fun smartMix(limit: Int = 100): LongArray {
        val refs = PerformanceManager.refs(app)
        val progress = PerformanceManager.progress(app)
        val now = System.currentTimeMillis()
        val model = runCatching { AppManagers.adaptive.state().userModel }.getOrDefault("cold")
        return refs.map { ref ->
            val r = progress.record(ref.stableKey)
            var score = 0
            when { r == null -> score += if (model == "cold") 40 else 20
                r.status == "wrong" -> score += if (model == "weak") 55 else 35
                r.nextDue == 0L || now >= r.nextDue -> score += 45
                r.status == "correct" -> score += if (model == "strong") 20 else 5 }
            score += (ref.category.length.coerceAtMost(20) / 10)
            ref.id to score
        }.sortedByDescending { it.second }.take(limit.coerceAtLeast(1)).map { it.first }.toLongArray()
    }

    fun explain(subject: String, count: Int): String =
        if (count == 0) "No strong matches found. Try a broader subject name, e.g. Pharmacology or Cardiology."
        else "Matched $count questions for “$subject”. Due, wrong and unseen questions are prioritised."
}
