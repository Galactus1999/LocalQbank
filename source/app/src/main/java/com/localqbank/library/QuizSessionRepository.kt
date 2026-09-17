package com.localqbank.library

import android.content.Context

/**
 * Persistence seam for the quiz presentation layer.
 *
 * This is intentionally thin: QBankDb and ProgressRepository remain the authoritative
 * storage implementations. No quiz business rules are duplicated here.
 */
class QuizSessionRepository(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val db = QBankDb(context.applicationContext)
    val progress: ProgressRepository = ProgressStore(context.applicationContext)

    fun existingQuestionIds(ids: LongArray): LongArray = db.existingQuestionIds(ids)
    fun questionById(id: Long): Question? = db.questionById(id)
    fun questionAt(testId: String, position: Int): Question? = db.questionAt(testId, position)
    fun testIdForQuestion(id: Long): String? = db.testIdForQuestion(id)
    fun sourceIdForTest(testId: String): Long = db.sourceIdForTest(testId)
    fun sourceNameForTest(testId: String): String? = db.sourceNameForTest(testId)
    fun questionCount(testId: String): Int = db.questionCount(testId)
    fun collectionQuestionIds(type: String, value: String): LongArray {
        val refs = PerformanceManager.refs(app)
        val progressStore = PerformanceManager.progress(app)
        return refs.asSequence().filter { r ->
            val p = progressStore.record(r.stableKey)
            when (type) {
                "status" -> when (value) {
                    "unsolved" -> p?.status == null
                    else -> p?.status == value
                }
                "bookmark" -> if (value == "all") !p?.bookmark.isNullOrBlank() else p?.bookmark == value
                else -> false
            }
        }.map { it.id }.toList().toLongArray()
    }
    fun rawQuestionPosition(testId: String, questionId: Long): Int? = db.rawQuestionPosition(testId, questionId)
    fun note(questionId: Long): String? = db.note(questionId)
    fun saveNote(questionId: Long, note: String) = db.saveNote(questionId, note)

    override fun close() {
        db.close()
    }
}
