package com.localqbank.library

/**
 * Abstraction over per-question study progress (answers, bookmarks, spaced-repetition
 * schedule, timing). [ProgressStore] is the current SharedPreferences-backed implementation.
 *
 * This interface is a seam, not a rewrite: it changes nothing about how data is stored today.
 * It exists so that:
 *  - business logic (like StudyStateRepository) can depend on the contract instead of the
 *    concrete storage mechanism, and
 *  - tests can supply a fake/in-memory implementation instead of touching real SharedPreferences,
 *  - a future storage backend (e.g. Room) can be introduced later by adding a new
 *    implementation of this interface, without changing any calling code.
 */
interface ProgressRepository {
    fun selected(id: String): String?
    fun status(id: String): String?
    fun bookmark(id: String): String?
    fun review(id: String): Boolean
    fun position(testId: String): Int
    fun sourceResume(sourceId: Long): String?
    fun sourceResumeByName(sourceName: String): String?
    fun setPosition(testId: String, position: Int, sourceId: Long = 0L, sourceName: String? = null)

    // Spaced repetition (SM-2 style) - see SpacedRepetitionScheduler for the scheduling math.
    fun easeFactor(id: String): Float
    fun repetitions(id: String): Int
    fun intervalDays(id: String): Float
    fun nextDue(id: String): Long
    fun setAnswer(id: String, answer: String, status: String)
    fun isDue(id: String): Boolean
    fun isScheduledDue(id: String): Boolean

    fun attempts(id: String): Int
    fun lastAttempted(id: String): Long
    fun timeMs(id: String): Long
    fun mistakeType(id: String): String?
    fun setMistakeType(id: String, value: String?)
    fun addTime(id: String, elapsedMs: Long)
    fun allProgressKeys(): Set<String>
    fun setBookmark(id: String, value: String?)
    fun setReview(id: String, value: Boolean)
    fun clear(id: String)
    fun allEntries(): Map<String, *>
    fun restore(entries: Map<String, *>)
}
