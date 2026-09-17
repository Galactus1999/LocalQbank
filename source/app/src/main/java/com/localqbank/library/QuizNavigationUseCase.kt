package com.localqbank.library

/** Navigation/session policy for quiz screens. No UI or persistence implementation details. */
class QuizNavigationUseCase(private val repository: QuizSessionRepository) {
    data class SessionResolution(
        val testId: String,
        val sourceId: Long,
        val questionCount: Int,
        val position: Int,
        val collectionIds: LongArray,
        val bankName: String
    )

    /** Pure question-position policy used by the Activity presentation layer. */
    fun targetPosition(current: Int, delta: Int, count: Int): Int {
        if (count <= 0 || delta == 0) return current.coerceAtLeast(0)
        return (current + delta).coerceIn(0, count - 1)
    }

    fun isBoundary(current: Int, delta: Int, count: Int): Boolean =
        count <= 0 || delta == 0 || targetPosition(current, delta, count) == current

    fun resolve(
        title: String,
        requestedTestId: String,
        requestedQuestionId: Long,
        requestedPosition: Int,
        sessionIdsRaw: String?,
        collectionMode: Boolean,
        filterType: String,
        filterValue: String,
        sessionLabel: String?
    ): Result<SessionResolution> = runCatching {
        var testId = requestedTestId
        var collectionIds = longArrayOf()
        var count: Int
        var position: Int

        if (!sessionIdsRaw.isNullOrBlank()) {
            val requestedIds = sessionIdsRaw.split(',').mapNotNull { it.toLongOrNull() }
                .filter { it > 0L }.distinct().take(500).toLongArray()
            val existing = repository.existingQuestionIds(requestedIds).toSet()
            collectionIds = requestedIds.filter { it in existing }.toLongArray()
            count = collectionIds.size
            require(count > 0) { "This practice session has no available questions." }
            position = requestedQuestionId.takeIf { it > 0L }?.let { collectionIds.indexOf(it).takeIf { i -> i >= 0 } }
                ?: requestedPosition.coerceIn(0, count - 1)
            testId = repository.questionById(collectionIds[position])?.let { repository.testIdForQuestion(it.id) }.orEmpty()
        } else if (collectionMode) {
            collectionIds = repository.collectionQuestionIds(filterType, filterValue)
            count = collectionIds.size
            require(count > 0) { "There are no questions in this collection yet." }
            position = requestedQuestionId.takeIf { it > 0L }?.let { collectionIds.indexOf(it).takeIf { i -> i >= 0 } }
                ?: requestedPosition.coerceIn(0, count - 1)
            if (testId.isBlank()) testId = repository.questionById(collectionIds[position])?.let { repository.testIdForQuestion(it.id) }.orEmpty()
        } else {
            if (testId.isBlank() && requestedQuestionId > 0L) testId = repository.testIdForQuestion(requestedQuestionId).orEmpty()
            require(testId.isNotBlank()) { "This section could not be opened because its QBank ID is missing." }
            count = repository.questionCount(testId)
            position = (if (requestedPosition >= 0) requestedPosition else repository.progress.position(testId))
                .coerceIn(0, (count - 1).coerceAtLeast(0))
        }

        val sourceId = if (testId.isBlank()) 0L else repository.sourceIdForTest(testId)
        val exactPosition = if (requestedQuestionId > 0L && !collectionMode && sessionIdsRaw.isNullOrBlank()) {
            repository.rawQuestionPosition(testId, requestedQuestionId)
        } else null
        if (exactPosition != null) position = exactPosition.coerceIn(0, (count - 1).coerceAtLeast(0))
        val bankName = sessionLabel ?: repository.sourceNameForTest(testId) ?: title.ifBlank { "QBank" }
        SessionResolution(testId, sourceId, count, position, collectionIds, bankName)
    }
}
