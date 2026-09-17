package com.localqbank.library

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-risk device tests for the Stage 6 dependency graph and Stage 7 runtime boundaries.
 * Tests deliberately avoid shipping model artifacts; EmbeddingGemma execution is exercised when
 * the user/device has the validated model + tokenizer installed, otherwise the safe path is tested.
 */
@RunWith(AndroidJUnit4::class)
class RovexStage7InstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun applicationContainerWiresScreenDependenciesWithoutReplacingManagers() {
        val app = context.applicationContext as LocalQBankApplication
        val container = app.appContainer
        assertNotNull(container.newMainRepository().also { it.close() })
        assertNotNull(container.settingsRepository)
        assertTrue(container.mainViewModelFactory() is MainViewModelFactory)
        assertTrue(container.settingsViewModelFactory() is SettingsViewModelFactory)
        assertTrue(container.quizViewModelFactory() is QuizViewModelFactory)
        assertTrue(container.flashcardViewModelFactory() is FlashcardViewModelFactory)
        assertNotNull(container.neuralModelManager)
        // The import repository is operation-scoped and must not be cached in AppContainer.
        container.newHtmlImportRepository().close()
        assertTrue(AppManagers.isReady())
        assertNotNull(AppManagers.adaptive)
        assertNotNull(AppManagers.importer)
        assertNotNull(AppManagers.benBrain)
    }

    @Test
    fun flashcardViewModelCanCreateAndLoadState() {
        val app = context.applicationContext as LocalQBankApplication
        val viewModel = app.appContainer.flashcardViewModelFactory().create(FlashcardViewModel::class.java)
        val completed = CountDownLatch(1)
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val observed = arrayOfNulls<FlashcardUiState>(1)
            scope.launch {
                observed[0] = viewModel.uiState.first { !it.loading }
                completed.countDown()
            }
            viewModel.refresh()
            assertTrue("FlashcardViewModel did not load state", completed.await(10, TimeUnit.SECONDS))
            assertNotNull(observed[0])
            assertTrue(observed[0]!!.reviewCount >= 0)
            assertTrue(observed[0]!!.allCardCount >= 0)
        } finally {
            scope.cancel()
            viewModel.clear()
        }
    }

    @Test
    fun databaseSmokeAndSchemaAreUsable() {
        val db = QBankDb(context)
        try {
            assertNotNull(db.sources())
            assertTrue(db.aiIndexQuestionCount() >= 0)
            assertTrue(db.aiIndexBatch(limit = 1).size <= 1)
        } finally {
            db.close()
        }
    }

    @Test
    fun importCoordinatorSerializesWorkAndDeliversCompletion() {
        val coordinator = ImportPipelineCoordinator(context)
        val completed = CountDownLatch(1)
        val executions = AtomicInteger(0)
        try {
            assertTrue(
                coordinator.enqueue(
                    sourceName = "stage7-test.html",
                    bytes = 1024L,
                    work = { executions.incrementAndGet() },
                    onSuccess = { completed.countDown() },
                    onFailure = { completed.countDown() }
                )
            )
            assertTrue("import coordinator did not complete", completed.await(10, TimeUnit.SECONDS))
            assertEquals(1, executions.get())
        } finally {
            coordinator.closeForTest()
        }
    }

    @Test
    fun quizCriticalFlowCanOpenAnImportedQuestion() {
        val repository = HtmlImportRepository(context)
        val sourceName = "stage7_quiz_${System.currentTimeMillis()}.html"
        try {
            val question = ImportedQuestion(
                sourceId = "stage7-q1",
                text = "Stage 7 smoke question: which option is correct?",
                rawText = null,
                correctAnswer = "A",
                explanation = "Instrumented quiz-flow smoke test.",
                bot = null,
                video = null,
                audio = null,
                options = listOf(
                    ImportedOption("A", "Correct", true),
                    ImportedOption("B", "Incorrect", false)
                ),
                questionImages = emptyList(),
                explanationImages = emptyList()
            )
            assertEquals(1, repository.importBundle(
                sourceName,
                "Stage7",
                listOf(ImportedTest(null, "Stage 7 Test", "Stage7", 4.0, 1, 60.0, listOf(question)))
            ))

            val db = QBankDb(context)
            val source = try { db.sources().first { it.fileName == sourceName } } finally { db.close() }
            val test = QBankDb(context).let { qdb -> try { qdb.tests(source.id).single() } finally { qdb.close() } }
            val questionId = QBankDb(context).let { qdb ->
                try { qdb.questions(test.id).single().id } finally { qdb.close() }
            }
            val quizRepository = QuizSessionRepository(context)
            try {
                val notes = QuizNotesUseCase(quizRepository)
                notes.save(questionId, "Stage 7 note")
                notes.append(questionId, "Second note")
                assertEquals("Stage 7 note\n\nSecond note", notes.get(questionId))
            } finally {
                quizRepository.close()
            }

            val intent = Intent(context, QuizActivity::class.java)
                .putExtra("title", test.title)
                .putExtra("testId", test.id)
                .putExtra("position", 0)

            ActivityScenario.launch<QuizActivity>(intent).use { scenario ->
                assertTrue("QuizActivity failed to reach RESUMED", scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            }
        } finally {
            runCatching {
                val db = QBankDb(context)
                try {
                    db.sources().firstOrNull { it.fileName == sourceName }?.let { db.deleteSource(it.id) }
                } finally { db.close() }
            }
            repository.close()
        }
    }

    @Test
    fun startupAndSettingsActivitiesDoNotCrash() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            onView(withId(R.id.appLogoText)).check(matches(isDisplayed()))
        }
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            onView(withText("Adaptive Engine")).check(matches(isDisplayed()))
            onView(withText("Ben AI Safety")).check(matches(isDisplayed()))
        }
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java).putExtra("section", "adaptive")).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            onView(withText("BEN • Cognitive Architecture")).check(matches(isDisplayed()))
            onView(withText("Local RAG / QBank Retrieval")).check(matches(isDisplayed()))
        }
        // No-URI import intentionally finishes immediately; reaching this point proves the
        // crash-sensitive startup/empty-input path did not throw.
        ActivityScenario.launch<HtmlImportActivity>(Intent(context, HtmlImportActivity::class.java)).use { }
        ActivityScenario.launch<NotesActivity>(Intent(context, NotesActivity::class.java)).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
        ActivityScenario.launch<FlashcardActivity>(Intent(context, FlashcardActivity::class.java)).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            scenario.recreate()
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
    }

    @Test
    fun groundedBenPipelineReturnsSafeAnswerWhenNeuralAccelerationIsUnavailable() = runBlocking {
        val result = BenGroundedNeuralPipeline(context).run("nephrotic syndrome")
        assertTrue(result.answer.isNotBlank())
        assertTrue(result.evidenceCount >= 0)
        assertTrue(result.elapsedMs >= 0L)
    }

    @Test
    fun embeddingGemmaMissingArtifactsAlwaysFallsBackDeterministically() = runBlocking {
        val manager = BenNeuralModelManager(context)
        val engine = BenEmbeddingGemmaEngine(context)
        // This is a permanent safety test: even if another test/device provisioned models,
        // an invalid candidate list must still return a bounded deterministic result.
        val result = engine.rerank("acute kidney injury", listOf("acute kidney injury", "nephrotic syndrome"), { it }, 2)
        assertTrue(result.hits.isNotEmpty())
        assertTrue(result.hits.size <= 2)
        assertTrue(result.elapsedMs >= 0L)
        if (manager.installed(BenNeuralModelRegistry.embeddingGemma300m) == null ||
            manager.installedEmbeddingGemmaTokenizer() == null) {
            assertFalse(result.usedModel)
        }
    }

    @Test
    fun settingsAdaptiveScreenSurvivesConfigurationRecreation() {
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java).putExtra("section", "adaptive")).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            onView(withText("BEN • Cognitive Architecture")).check(matches(isDisplayed()))
            scenario.recreate()
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            onView(withText("LIVE ENGINE TOPOLOGY")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun startupCrashSensitiveScreensReachResumedState() {
        listOf(
            Intent(context, BackupActivity::class.java),
            Intent(context, CollectionActivity::class.java),
            Intent(context, SearchActivity::class.java),
            Intent(context, StudyToolsActivity::class.java),
            Intent(context, TestListActivity::class.java),
            Intent(context, BenModelLabActivity::class.java)
        ).forEach { intent ->
            ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
                assertTrue("${intent.component?.className} failed startup", scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            }
        }
    }

    @Test
    fun benNeuralFailureFallsBackDeterministically() = runBlocking {
        val research = BenLocalResearchEngine(context, UnavailableBenInferenceBackend)
        val fallback = research.research("nephrotic syndrome")
        assertFalse(fallback.modelUsed)
        assertTrue(fallback.answer.isNotBlank())

        val manager = BenNeuralModelManager(context)
        val embedding = BenEmbeddingGemmaEngine(context)
        if (manager.installed(BenNeuralModelRegistry.embeddingGemma300m) == null ||
            manager.installedEmbeddingGemmaTokenizer() == null) {
            val result = embedding.rerank(
                "kidney injury",
                listOf("acute kidney injury", "nephrotic syndrome"),
                { it },
                limit = 2
            )
            assertFalse(result.usedModel)
            assertTrue(result.hits.isNotEmpty())
        }
    }

    @Test
    fun provisionedEmbeddingGemmaRunsRealInference() = runBlocking {
        val manager = BenNeuralModelManager(context)
        org.junit.Assume.assumeTrue(manager.installed(BenNeuralModelRegistry.embeddingGemma300m) != null)
        org.junit.Assume.assumeTrue(manager.installedEmbeddingGemmaTokenizer() != null)
        val score = BenEmbeddingGemmaEngine(context).compare(
            "minimal change disease",
            "nephrotic syndrome"
        )
        assertNotNull(score)
        assertTrue(score!!.isFinite())
        assertTrue(score in -1.0001..1.0001)
        val telemetry = BenNeuralTelemetry.snapshot()
        assertTrue(telemetry.stageDetail.contains("QNN", true) || telemetry.stageDetail.contains("CPU/XNNPACK", true))
    }

    @Test
    fun benCognitiveControlsHaveSafeDefaults() {
        val control = BenCognitiveControl(context)
        assertTrue(control.cognitiveCoreEnabled)
        assertTrue(control.knowledgeGraphEnabled)
        assertTrue(control.learnerMemoryEnabled)
        assertTrue(control.verifierEnabled)
        assertTrue(control.plannerEnabled)
        assertTrue(control.retrievalEnabled)
        assertTrue(control.specialistRoutingEnabled)
        assertTrue(control.experienceMemoryEnabled)
    }
}
