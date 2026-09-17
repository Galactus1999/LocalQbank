package com.localqbank.library

import android.content.Context

/**
 * Controlled application dependency graph for screen-facing repositories and ViewModel factories.
 *
 * This is deliberately a small manual container rather than a second business-logic layer.
 * AppManagers remains the authoritative owner of engines/managers. Repositories that own a
 * SQLite handle are screen/ViewModel scoped and are created fresh for each ViewModel instance;
 * they are never cached here after the ViewModel can close them.
 */
class RovexAppContainer(context: Context) {
    private val app = context.applicationContext

    /** MainRepository owns a SQLite handle and is therefore created per MainViewModel. */
    fun newMainRepository(): MainRepository = MainRepository(app)

    /** SettingsRepository is lightweight SharedPreferences state with no close lifecycle. */
    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(app)
    }

    fun mainViewModelFactory(): MainViewModelFactory = MainViewModelFactory(newMainRepository())

    fun settingsViewModelFactory(): SettingsViewModelFactory = SettingsViewModelFactory(settingsRepository)

    /** Quiz persistence is intentionally screen-scoped; the ViewModel closes it when cleared. */
    fun quizViewModelFactory(): QuizViewModelFactory = QuizViewModelFactory(
        newRepository = { QuizSessionRepository(app) },
        newSessionUseCase = { QuizSessionUseCase(app) },
        newStudyStateRepository = { StudyStateRepository(app) }
    )

    /** Import DB handles are operation-scoped and must always be closed by the caller. */
    fun newHtmlImportRepository(): HtmlImportRepository = HtmlImportRepository(app)

    /** Flashcard library persistence is owned by the ViewModel-scoped repository. */
    fun flashcardViewModelFactory(): FlashcardViewModelFactory = FlashcardViewModelFactory(SqliteFlashcardReviewRepository(app))

    /** Model artifact storage is app-scoped; it does not own inference or AI policy. */
    val neuralModelManager: BenNeuralModelManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BenNeuralModelManager(app)
    }
}
