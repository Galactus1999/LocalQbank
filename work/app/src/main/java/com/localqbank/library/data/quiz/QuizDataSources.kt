package com.localqbank.library.data.quiz

import com.localqbank.library.PerformanceManager
import com.localqbank.library.ProgressRepository
import com.localqbank.library.QBankDb
import com.localqbank.library.Question

/** Narrow data-source contracts keep quiz orchestration independent of SQLite implementation. */
interface QuizQuestionDataSource : AutoCloseable {
    fun existingQuestionIds(ids: LongArray): LongArray
    fun questionById(id: Long): Question?
    fun questionAt(testId: String, position: Int): Question?
    fun testIdForQuestion(id: Long): String?
    fun sourceIdForTest(testId: String): Long
    fun sourceNameForTest(testId: String): String?
    fun questionCount(testId: String): Int
    fun rawQuestionPosition(testId: String, questionId: Long): Int?
}

interface QuizNoteDataSource : AutoCloseable {
    fun note(questionId: Long): String?
    fun saveNote(questionId: Long, note: String)
}

class QBankQuizDataSource(context: android.content.Context) : QuizQuestionDataSource, QuizNoteDataSource {
    private val db = QBankDb(context.applicationContext)
    override fun existingQuestionIds(ids: LongArray) = db.existingQuestionIds(ids)
    override fun questionById(id: Long) = db.questionById(id)
    override fun questionAt(testId: String, position: Int) = db.questionAt(testId, position)
    override fun testIdForQuestion(id: Long) = db.testIdForQuestion(id)
    override fun sourceIdForTest(testId: String) = db.sourceIdForTest(testId)
    override fun sourceNameForTest(testId: String) = db.sourceNameForTest(testId)
    override fun questionCount(testId: String) = db.questionCount(testId)
    override fun rawQuestionPosition(testId: String, questionId: Long) = db.rawQuestionPosition(testId, questionId)
    override fun note(questionId: Long) = db.note(questionId)
    override fun saveNote(questionId: Long, note: String) = db.saveNote(questionId, note)
    override fun close() = db.close()
}

interface QuizCollectionDataSource {
    fun questionIds(filterType: String, filterValue: String): LongArray
}

class PerformanceQuizCollectionDataSource(private val context: android.content.Context) : QuizCollectionDataSource {
    override fun questionIds(filterType: String, filterValue: String): LongArray {
        val app = context.applicationContext
        val refs = PerformanceManager.refs(app)
        val progressStore = PerformanceManager.progress(app)
        return refs.asSequence().filter { r ->
            val p = progressStore.record(r.stableKey)
            when (filterType) {
                "status" -> when (filterValue) {
                    "unsolved" -> p?.status == null
                    else -> p?.status == filterValue
                }
                "bookmark" -> if (filterValue == "all") !p?.bookmark.isNullOrBlank() else p?.bookmark == filterValue
                else -> false
            }
        }.map { it.id }.toList().toLongArray()
    }
}
