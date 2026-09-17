package com.localqbank.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle

/**
 * Screen-level quiz state holder. Android lifecycle/context objects stay outside the ViewModel;
 * storage and application dependencies are supplied by the factory.
 */
class QuizViewModel(
    private val data: QuizSessionRepository,
    private val navigation: QuizNavigationUseCase,
    private val session: QuizSessionUseCase,
    private val answerUseCase: QuizAnswerUseCase,
    private val savedState: SavedStateHandle? = null
) : ViewModel() {
    private object SavedKeys {
        const val TEST_ID = "quiz.testId"
        const val POSITION = "quiz.position"
        const val PRACTICE_KEY = "quiz.practiceAnsweredKey"
        const val EXAM_REMAINING = "quiz.examRemainingMs"
        const val SESSION_LABEL = "quiz.sessionLabel"
        const val EXAM_KEYS = "quiz.examAnswerKeys"
        const val EXAM_VALUES = "quiz.examAnswerValues"
    }

    val state = QuizUiState()

    init { restoreSavedTransientState() }

    private fun persistTransientState() {
        val h=savedState ?: return
        h[SavedKeys.TEST_ID]=state.testId
        h[SavedKeys.POSITION]=state.position
        h[SavedKeys.PRACTICE_KEY]=state.practiceAnsweredKey
        h[SavedKeys.EXAM_REMAINING]=state.examRemainingMs
        h[SavedKeys.SESSION_LABEL]=state.sessionLabel
        h[SavedKeys.EXAM_KEYS]=ArrayList(state.examAnswers.keys)
        h[SavedKeys.EXAM_VALUES]=ArrayList(state.examAnswers.values)
    }

    private fun restoreSavedTransientState() {
        val h=savedState ?: return
        state.testId=h[SavedKeys.TEST_ID] ?: state.testId
        state.position=(h[SavedKeys.POSITION] ?: 0).coerceAtLeast(0)
        state.practiceAnsweredKey=h[SavedKeys.PRACTICE_KEY]
        state.examRemainingMs=(h[SavedKeys.EXAM_REMAINING] ?: 0L).coerceAtLeast(0L)
        state.sessionLabel=h[SavedKeys.SESSION_LABEL]
        val keys=h.get<ArrayList<String>>(SavedKeys.EXAM_KEYS).orEmpty()
        val values=h.get<ArrayList<Boolean>>(SavedKeys.EXAM_VALUES).orEmpty()
        state.examAnswers.clear()
        keys.indices.filter { it < values.size && keys[it].isNotBlank() }.forEach { state.examAnswers[keys[it]]=values[it] }
    }
    private val notes = QuizNotesUseCase(data)
    private val bookmarks = QuizBookmarkUseCase(data.progress) {
        if (AppManagers.isReady()) AppManagers.adaptive.onEvent("bookmark_changed")
    }
    private val flashcards = QuizFlashcardUseCase()
    private val knowledge = QuizKnowledgeUseCase()

    fun answer(question: Question, label: String, practiceMode: Boolean, examMode: Boolean): QuizAnswerUseCase.Result =
        answerUseCase.answer(question, label, practiceMode, examMode)

    fun resolveSession(
        title: String, requestedTestId: String, requestedQuestionId: Long, requestedPosition: Int,
        sessionIdsRaw: String?, collectionMode: Boolean, filterType: String, filterValue: String, sessionLabel: String?
    ): Result<QuizNavigationUseCase.SessionResolution> =
        navigation.resolve(title, requestedTestId, requestedQuestionId, requestedPosition, sessionIdsRaw, collectionMode, filterType, filterValue, sessionLabel)

    fun note(questionId: Long): String? = notes.get(questionId)
    fun saveNote(questionId: Long, value: String) = notes.save(questionId, value)
    fun appendNote(questionId: Long, value: String) = notes.append(questionId, value)
    fun bookmark(stableKey: String): String? = bookmarks.current(stableKey)
    fun setBookmark(stableKey: String, value: String?) = bookmarks.set(stableKey, value)
    fun mistakeType(stableKey: String): String? = bookmarks.mistakeType(stableKey)
    fun setMistakeType(stableKey: String, value: String?) = bookmarks.setMistakeType(stableKey, value)
    fun createFlashcard(questionId: Long, contextLabel: String, onResult: (FlashcardIntelligenceManager.Result) -> Unit) =
        flashcards.createFromQuestion(questionId, contextLabel, onResult)

    fun captureQuestionToKnowledge(question: Question, reason: String, onDone: (KnowledgeEngineManager.Result) -> Unit) =
        knowledge.captureQuestion(question, reason, onDone)

    fun saveKnowledgeImage(raw: String, questionId: Long, onDone: (Boolean) -> Unit) =
        knowledge.saveImage(raw, questionId, onDone)

    fun saveKnowledgeImageWithNote(raw: String, questionId: Long, note: String, onDone: (Boolean) -> Unit) =
        knowledge.saveImageWithNote(raw, questionId, note, onDone)

    fun existingQuestionIds(ids: LongArray): LongArray = data.existingQuestionIds(ids)
    fun questionById(id: Long): Question? = data.questionById(id)
    fun questionAt(testId: String, position: Int): Question? = data.questionAt(testId, position)
    fun testIdForQuestion(questionId: Long): String = data.testIdForQuestion(questionId).orEmpty()
    fun sourceIdForTest(testId: String): Long = data.sourceIdForTest(testId)
    fun sourceNameForTest(testId: String): String? = data.sourceNameForTest(testId)
    fun questionCount(testId: String): Int = data.questionCount(testId)
    fun rawQuestionPosition(testId: String, questionId: Long): Int? = data.rawQuestionPosition(testId, questionId)
    fun progressStatus(stableKey: String): String? = data.progress.status(stableKey)
    fun selectedAnswer(stableKey: String): String? = data.progress.selected(stableKey)
    fun addTime(stableKey: String, elapsedMs: Long) = data.progress.addTime(stableKey, elapsedMs)
    fun sessionKey(label: String?, title: String, testId: String, collectionMode: Boolean): String = session.sessionKey(label, title, testId, collectionMode)
    fun saveSessionCursor(key: String, testId: String, position: Int, stableKey: String?) = session.saveCursor(key, testId, position, stableKey)
    fun checkpoint(testId: String, position: Int, stableKey: String?, sessionLabel: String?) = session.checkpoint(testId, position, stableKey, sessionLabel)
    fun savePosition(testId: String, position: Int, sourceId: Long, bankName: String) {
        if (testId.isNotBlank()) data.progress.setPosition(testId, position, sourceId, bankName)
    }

    fun findTestIdForQuestion(questionId: Long): String = data.testIdForQuestion(questionId).orEmpty()
    fun targetPosition(current: Int, delta: Int): Int = navigation.targetPosition(current, delta, state.questionCount)
    fun isNavigationBoundary(current: Int, delta: Int): Boolean = navigation.isBoundary(current, delta, state.questionCount)
    fun moveToPosition(position: Int) { setPosition(position); persistPosition() }
    fun persistPosition() {
        if (state.collectionMode || state.testId.isBlank()) return
        data.progress.setPosition(state.testId, state.position, state.sourceId, state.bankName)
    }
    fun persistSessionCursor() = saveSessionCursor(sessionKey(state.sessionLabel, state.title, state.testId, state.collectionMode), state.testId, state.position, state.currentQuestion?.stableKey)
    fun checkpointSession() = checkpoint(state.testId, state.position, state.currentQuestion?.stableKey, state.sessionLabel)
    fun recordElapsedTime(stableKey: String, elapsedMs: Long) { if (stableKey.isNotBlank() && elapsedMs > 0L) data.progress.addTime(stableKey, elapsedMs) }

    fun configureSession(title: String, requestedTestId: String, collectionMode: Boolean, examMode: Boolean, examDurationMinutes: Int, practiceMode: Boolean, sessionLabel: String?, sectionLabel: String?) {
        val restoredSameSession = savedState?.get<String>(SavedKeys.TEST_ID)?.let { it.isNotBlank() && it == requestedTestId } == true
        state.title = title
        state.testId = requestedTestId
        state.collectionMode = collectionMode
        state.examMode = examMode
        state.examDurationMinutes = examDurationMinutes
        state.practiceMode = practiceMode
        state.sessionLabel = sessionLabel
        state.sectionLabel = sectionLabel
        if (!restoredSameSession) state.resetForNewSession()
        persistTransientState()
    }

    fun applyResolution(resolution: QuizNavigationUseCase.SessionResolution) {
        state.testId = resolution.testId
        state.sourceId = resolution.sourceId
        state.questionCount = resolution.questionCount
        val restoredPosition = savedState?.get<Int>(SavedKeys.POSITION)
        state.position = if (restoredPosition != null && savedState?.get<String>(SavedKeys.TEST_ID) == resolution.testId) {
            restoredPosition.coerceIn(0, (resolution.questionCount - 1).coerceAtLeast(0))
        } else resolution.position.coerceIn(0, (resolution.questionCount - 1).coerceAtLeast(0))
        state.collectionQuestionIds = resolution.collectionIds
        state.bankName = state.sessionLabel ?: resolution.bankName
        persistTransientState()
    }
    fun setCurrentQuestion(question: Question?) { state.currentQuestion = question }
    fun setPosition(position: Int) { state.position = position.coerceIn(0, (state.questionCount - 1).coerceAtLeast(0)); persistTransientState() }
    fun setPracticeAnsweredKey(key: String?) { state.practiceAnsweredKey = key; persistTransientState() }
    fun setExamRemainingMs(value: Long) { state.examRemainingMs = value.coerceAtLeast(0L); persistTransientState() }
    fun recordExamAnswer(key: String, correct: Boolean) {
        if (key.isBlank()) return
        state.examAnswers[key] = correct
        persistTransientState()
    }
    fun examAttemptedCount(): Int = state.examAnswers.size
    fun examCorrectCount(): Int = state.examAnswers.values.count { it }

    override fun onCleared() {
        // Last-chance transient checkpoint before the repository is released. The durable
        // answer itself is already committed synchronously by StudyStateRepository.
        runCatching {
            persistTransientState()
            if (state.testId.isNotBlank()) {
                checkpointSession()
                persistPosition()
            }
        }
        data.close()
        super.onCleared()
    }
}

class QuizViewModelFactory(
    private val newRepository: () -> QuizSessionRepository,
    private val newSessionUseCase: () -> QuizSessionUseCase,
    private val newStudyStateRepository: () -> StudyStateRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = createInternal(modelClass, null)

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = createInternal(modelClass, extras.createSavedStateHandle())

    private fun <T : ViewModel> createInternal(modelClass: Class<T>, savedState: SavedStateHandle?): T {
        if (!modelClass.isAssignableFrom(QuizViewModel::class.java)) throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        val repository = newRepository()
        return QuizViewModel(
            data = repository,
            navigation = QuizNavigationUseCase(repository),
            session = newSessionUseCase(),
            answerUseCase = QuizAnswerUseCase(repository.progress, newStudyStateRepository()),
            savedState = savedState
        ) as T
    }
}
