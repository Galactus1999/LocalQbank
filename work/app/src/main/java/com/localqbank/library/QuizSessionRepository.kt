package com.localqbank.library

import android.content.Context

/**
 * Quiz data boundary. UI/use-cases depend on this repository while SQLite/Performance are hidden
 * behind narrow source interfaces that can be replaced by fakes in tests or future modules.
 */
class QuizSessionRepository(
    context: Context,
    private val questions: QuizQuestionDataSource = QBankQuizDataSource(context),
    private val notes: QuizNoteDataSource = (questions as? QuizNoteDataSource) ?: QBankQuizDataSource(context),
    private val collections: QuizCollectionDataSource = PerformanceQuizCollectionDataSource(context)
) : AutoCloseable {
    val progress: ProgressRepository = ProgressStore(context.applicationContext)

    fun existingQuestionIds(ids: LongArray): LongArray = questions.existingQuestionIds(ids)
    fun questionById(id: Long): Question? = questions.questionById(id)
    fun questionAt(testId: String, position: Int): Question? = questions.questionAt(testId, position)
    fun testIdForQuestion(id: Long): String? = questions.testIdForQuestion(id)
    fun sourceIdForTest(testId: String): Long = questions.sourceIdForTest(testId)
    fun sourceNameForTest(testId: String): String? = questions.sourceNameForTest(testId)
    fun questionCount(testId: String): Int = questions.questionCount(testId)
    fun collectionQuestionIds(type: String, value: String): LongArray = collections.questionIds(type, value)
    fun rawQuestionPosition(testId: String, questionId: Long): Int? = questions.rawQuestionPosition(testId, questionId)
    fun note(questionId: Long): String? = notes.note(questionId)
    fun saveNote(questionId: Long, note: String) = notes.saveNote(questionId, note)

    override fun close() {
        runCatching { questions.close() }
        if (notes !== questions) runCatching { notes.close() }
    }
}
