package com.localqbank.library.learning

/** Read-only analytical contract for the future Learning Observatory UI. */
data class LearningSnapshot(
    val questionsAttempted: Int,
    val questionsSolved: Int,
    val accuracyPercent: Int,
    val dueFlashcards: Int,
    val weakAreas: List<WeakArea>,
    val streakDays: Int,
    val recentMinutes: Int
)

data class WeakArea(val label: String, val masteryPercent: Int, val attempts: Int)

interface LearningObservatoryRepository {
    fun snapshot(): LearningSnapshot
}

class EmptyLearningObservatoryRepository : LearningObservatoryRepository {
    override fun snapshot(): LearningSnapshot = LearningSnapshot(0, 0, 0, 0, emptyList(), 0, 0)
}

/** Current local implementation. It is read-only and delegates storage ownership to existing managers/repositories. */
class LocalLearningObservatoryRepository(private val context: android.content.Context) : LearningObservatoryRepository {
    override fun snapshot(): LearningSnapshot {
        val app = context.applicationContext
        val refs = runCatching { com.localqbank.library.PerformanceManager.lightRefs(app) }.getOrDefault(emptyList())
        val progress = runCatching { com.localqbank.library.PerformanceManager.progress(app) }.getOrNull()
        if (progress == null) return LearningSnapshot(refs.size, 0, 0, 0, emptyList(), 0, 0)
        var solved = 0
        var correct = 0
        var minutes = 0L
        val weak = linkedMapOf<String, MutableList<Int>>()
        refs.forEach { ref ->
            val row = progress.record(ref.stableKey) ?: return@forEach
            if (row.status == "correct" || row.status == "wrong") {
                solved++
                if (row.status == "correct") correct++
            }
            minutes += row.timeMs
            if (row.status != null) weak.getOrPut(ref.category.ifBlank { "Uncategorised" }) { mutableListOf() }
                .add(if (row.status == "correct") 1 else 0)
        }
        val due = runCatching {
            com.localqbank.library.SqliteFlashcardReviewRepository(app).use { it.dueStatsAll().due }
        }.getOrDefault(0)
        val weakAreas = weak.map { (label, values) ->
            WeakArea(label, if (values.isEmpty()) 0 else values.sum() * 100 / values.size, values.size)
        }.sortedBy { it.masteryPercent }.take(8)
        return LearningSnapshot(
            questionsAttempted = solved,
            questionsSolved = solved,
            accuracyPercent = if (solved == 0) 0 else correct * 100 / solved,
            dueFlashcards = due,
            weakAreas = weakAreas,
            streakDays = 0,
            recentMinutes = (minutes / 60_000L).toInt()
        )
    }
}
