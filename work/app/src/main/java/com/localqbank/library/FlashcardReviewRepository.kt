package com.localqbank.library

import android.content.Context

/**
 * Screen-facing repository contract. It exposes persistence operations without making the
 * Activity or ViewModel depend on SQLite details. FlashcardDb remains authoritative for SRS rules.
 */
interface FlashcardReviewRepository : AutoCloseable {
    fun decks(): List<FlashcardDb.Deck>
    fun reviewCount(): Int
    fun bookmarkCount(): Int
    fun dueStatsAll(): FlashcardDb.DueStats
    fun dueStats(deckId: Long): FlashcardDb.DueStats
    fun modeCount(mode: String): Int
    fun reviewedToday(): Int
    fun totalReviews(): Int
    fun setting(key: String, default: String): String
    fun settingInt(key: String, default: Int): Int
    fun settingsSnapshot(): Map<String, String>
    fun setSetting(key: String, value: String)
    fun unsuspendDeck(deckId: Long, includeChildren: Boolean = false): Int
    fun deleteDeck(deckId: Long, includeSubdecks: Boolean = false): Int
    fun deleteDeckTree(name: String): Int
}

/** SQLite implementation; one instance belongs to one FlashcardViewModel. */
class SqliteFlashcardReviewRepository(context: Context) : FlashcardReviewRepository {
    private val db = FlashcardDb(context.applicationContext)

    override fun decks() = db.decks()
    override fun reviewCount() = db.reviewCount()
    override fun bookmarkCount() = db.bookmarkCount()
    override fun dueStatsAll() = db.dueStatsAll()
    override fun dueStats(deckId: Long) = db.dueStats(deckId)
    override fun modeCount(mode: String) = db.modeCount(mode)
    override fun reviewedToday() = db.reviewedToday()
    override fun totalReviews() = db.totalReviews()
    override fun setting(key: String, default: String) = db.setting(key, default)
    override fun settingInt(key: String, default: Int) = db.settingInt(key, default)
    override fun settingsSnapshot() = db.settingsSnapshot()
    override fun setSetting(key: String, value: String) = db.setSetting(key, value)
    override fun unsuspendDeck(deckId: Long, includeChildren: Boolean) = db.unsuspendDeck(deckId, includeChildren)
    override fun deleteDeck(deckId: Long, includeSubdecks: Boolean) = db.deleteDeck(deckId, includeSubdecks)
    override fun deleteDeckTree(name: String) = db.deleteDeckTree(name)
    override fun close() = db.close()
}
