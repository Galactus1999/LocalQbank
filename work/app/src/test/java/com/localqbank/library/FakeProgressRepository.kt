package com.localqbank.library

/**
 * Simple in-memory [ProgressRepository] for unit tests. Lets logic that depends on the
 * abstraction (e.g. StudyStateRepository) be tested without a real Context or SharedPreferences.
 * Test-only: lives in src/test so it is never compiled into the shipped app.
 */
class FakeProgressRepository : ProgressRepository {
    private data class Entry(
        var answer: String? = null,
        var status: String? = null,
        var bookmark: String? = null,
        var review: Boolean = false,
        var attempts: Int = 0,
        var lastAttempted: Long = 0L,
        var timeMs: Long = 0L,
        var mistake: String? = null,
        var easeFactor: Float = 2.5f,
        var repetitions: Int = 0,
        var intervalDays: Float = 0f,
        var nextDue: Long = 0L
    )

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    private val positions = java.util.concurrent.ConcurrentHashMap<String, Int>()
    var now: Long = 0L

    private fun entry(id: String) = entries.getOrPut(id) { Entry() }

    override fun selected(id: String) = entries[id]?.answer
    override fun status(id: String) = entries[id]?.status
    override fun bookmark(id: String) = entries[id]?.bookmark
    override fun review(id: String) = entries[id]?.review ?: false
    override fun position(testId: String) = positions[testId] ?: 0
    override fun sourceResume(sourceId: Long): String? = null
    override fun sourceResumeByName(sourceName: String): String? = null
    override fun setPosition(testId: String, position: Int, sourceId: Long, sourceName: String?) {
        positions[testId] = position
    }

    override fun easeFactor(id: String) = entries[id]?.easeFactor ?: 2.5f
    override fun repetitions(id: String) = entries[id]?.repetitions ?: 0
    override fun intervalDays(id: String) = entries[id]?.intervalDays ?: 0f
    override fun nextDue(id: String) = entries[id]?.nextDue ?: 0L

    override fun setAnswer(id: String, answer: String, status: String) {
        val e = entry(id)
        val result = SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(
                correct = status == "correct",
                easeFactor = e.easeFactor,
                repetitions = e.repetitions,
                previousIntervalDays = e.intervalDays
            ),
            now = now
        )
        e.answer = answer
        e.status = status
        e.attempts += 1
        e.lastAttempted = now
        e.easeFactor = result.easeFactor
        e.repetitions = result.repetitions
        e.intervalDays = result.intervalDays
        e.nextDue = result.nextDueAt
    }

    override fun isDue(id: String): Boolean {
        val e = entries[id] ?: return true
        if (e.lastAttempted == 0L) return true
        return now >= e.nextDue
    }

    override fun isScheduledDue(id: String): Boolean {
        val e = entries[id] ?: return false
        if (e.status.isNullOrBlank()) return false
        return now >= e.nextDue
    }

    override fun attempts(id: String) = entries[id]?.attempts ?: 0
    override fun lastAttempted(id: String) = entries[id]?.lastAttempted ?: 0L
    override fun timeMs(id: String) = entries[id]?.timeMs ?: 0L
    override fun mistakeType(id: String) = entries[id]?.mistake
    override fun setMistakeType(id: String, value: String?) { entry(id).mistake = value }
    override fun addTime(id: String, elapsedMs: Long) { entry(id).timeMs += elapsedMs }
    override fun allProgressKeys(): Set<String> = entries.keys.toSet()
    override fun setBookmark(id: String, value: String?) { entry(id).bookmark = value }
    override fun setReview(id: String, value: Boolean) { entry(id).review = value }
    override fun clear(id: String) { entries.remove(id) }
    override fun allEntries(): Map<String, *> = entries
    override fun restore(entries: Map<String, *>) { /* not needed for current tests */ }
}
