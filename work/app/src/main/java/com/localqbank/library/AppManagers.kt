package com.localqbank.library

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-level coordination layer.
 *
 * UI screens never own business rules. Managers read/write through the existing
 * QBankDb/ProgressStore and communicate through AppState. Each manager is deliberately
 * deterministic and failure-tolerant: a derived metric can fail without taking down the UI.
 */
object AppManagers {
    @Volatile private var initialized = false
    lateinit var analytics: AnalyticsManager
        private set
    lateinit var todaySolved: TodaySolvedManager
        private set
    lateinit var health: DataHealthManager
        private set
    lateinit var learning: LearningManager
        private set
    lateinit var dashboard: DashboardManager
        private set
    lateinit var adaptive: AdaptiveEngineManager
        private set
    lateinit var studyIntelligence: StudyIntelligenceManager
        private set
    lateinit var flashcardIntelligence: FlashcardIntelligenceManager
        private set
    lateinit var knowledge: KnowledgeEngineManager
        private set
    lateinit var resilienceHealth: EngineHealthManager
        private set
    lateinit var guardian: IntelligenceGuardian
        private set
    lateinit var orchestrator: IntelligenceOrchestrator
        private set
    lateinit var bio: RenIntelligenceOrchestrator
        private set
    lateinit var renCognitive: RenCognitiveEngine
        private set
    lateinit var benBrain: BenCognitiveArchitecture
        private set
    lateinit var engineMesh: EngineMeshCoordinator
        private set
    lateinit var studyState: StudyStateRepository
        private set
    lateinit var strategy: StudyStrategyEngine
        private set
    lateinit var learningGraph: LearningGraph
        private set
    lateinit var sessionStore: StudySessionStore
        private set
    lateinit var renMemory: RenMemoryStore
        private set
    lateinit var importer: ImportPipelineCoordinator
        private set
    lateinit var battery: RovexBatteryManager
        private set
    lateinit var runtime: RovexRuntimeEngine
    lateinit var qbankLoading: QBankLoadingEngine
        private set
    lateinit var experience: ExperiencePerformanceManager
        private set
    lateinit var frankensteinSupport: FrankensteinSupportEngine
        private set
    lateinit var subjectFocus: SubjectFocusSearchEngine
        private set
    lateinit var cloudAi: BenCloudAiGateway
        private set
    lateinit var frankensteinContext: FrankensteinContextEngine
        private set

    fun isReady(): Boolean = initialized

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        android.os.Trace.beginSection("Rovex.AppManagers.initialize")
        try {
            val app = context.applicationContext
            battery = RovexBatteryManager(app)
        runtime = RovexRuntimeEngine(app)
        qbankLoading = QBankLoadingEngine(app)
        experience = ExperiencePerformanceManager(app)
        frankensteinSupport = FrankensteinSupportEngine(app)
        subjectFocus = SubjectFocusSearchEngine(app)
        analytics = AnalyticsManager(app)
        todaySolved = TodaySolvedManager(app)
        health = DataHealthManager(app)
        adaptive = AdaptiveEngineManager(app)
        studyIntelligence = StudyIntelligenceManager(app, subjectFocus)
        flashcardIntelligence = FlashcardIntelligenceManager(app)
        knowledge = KnowledgeEngineManager(app)
        resilienceHealth = EngineHealthManager(app)
        guardian = IntelligenceGuardian(app)
        // One shared deterministic clinical engine is injected into the Ben/BIO facades.
        // This keeps Ren as the authoritative local knowledge/retrieval layer rather than
        // allowing multiple independent Ren instances to evolve separate routing state.
        renCognitive = RenCognitiveEngine(app)
        benBrain = BenCognitiveArchitecture(app, renCognitive)
        cloudAi = BenCloudAiGateway(app)
        frankensteinContext = FrankensteinContextEngine(app)
        bio = RenIntelligenceOrchestrator(app, renCognitive)
        orchestrator = IntelligenceOrchestrator(app, bio)
        engineMesh = EngineMeshCoordinator(app)
        studyState = StudyStateRepository(app)
        strategy = StudyStrategyEngine(app)
        learningGraph = LearningGraph(app)
        sessionStore = StudySessionStore(app)
        renMemory = RenMemoryStore(app)
        importer = ImportPipelineCoordinator(app)
        dashboard = DashboardManager(app, analytics, todaySolved, adaptive)
            learning = LearningManager(app, analytics)
            initialized = true
        } finally {
            android.os.Trace.endSection()
        }
    }
}

data class AnalyticsSnapshot(
    val total: Int = 0,
    val attempted: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val unsolved: Int = 0,
    val accuracy: Int = 0,
    val mastery: Int = 0,
    val due: Int = 0,
    val todaySolved: Int = 0,
    val bookmarks: Int = 0,
    val notes: Int = 0,
    val averageTimeMs: Long = 0L
)

class AnalyticsManager(context: Context) {
    private val app = context.applicationContext
    @Volatile private var lastValid: AnalyticsSnapshot? = null
    private val noteCount = AtomicInteger(0)
    private val noteCountLoaded = AtomicBoolean(false)
    private val noteExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "note-index") }

    fun invalidate() {
        lastValid = null
        noteCount.set(0)
        noteCountLoaded.set(false)
    }

    private fun notesCached(): Int {
        if (noteCountLoaded.compareAndSet(false, true)) {
            noteExecutor.execute {
                runCatching { QBankDb(app).useDb { noteCount() } }.onSuccess { noteCount.set(it) }
            }
        }
        return noteCount.get()
    }

    fun snapshot(refs: List<QuestionRef>, periodDays: Int = 0): AnalyticsSnapshot {
        return runCatching {
            val progress = PerformanceManager.progress(app)
            val now = System.currentTimeMillis()
            val since = if (periodDays > 0) now - periodDays * 86_400_000L else Long.MIN_VALUE
            val todaySince = startOfToday()
            var total = 0
            var correct = 0
            var wrong = 0
            var bookmarks = 0
            var today = 0
            var due = 0
            var timeTotal = 0L
            var timed = 0
            refs.forEach { ref ->
                val r = progress.record(ref.stableKey)
                if (r != null && r.lastAttempted < since) return@forEach
                total++
                when (r?.status) { "correct" -> correct++; "wrong" -> wrong++ }
                if (!r?.bookmark.isNullOrBlank()) bookmarks++
                if (r != null && r.lastAttempted >= todaySince && r.status != null) today++
                if (r != null && r.status != null && (r.nextDue == 0L && r.lastAttempted > 0L && now - r.lastAttempted >= when (r.attempts) { 1 -> 86_400_000L; 2 -> 3 * 86_400_000L; 3 -> 7 * 86_400_000L; 4 -> 14 * 86_400_000L; else -> 30 * 86_400_000L } || r.nextDue > 0L && now >= r.nextDue)) due++
                if (r != null && r.timeMs > 0L) { timeTotal += r.timeMs; timed++ }
            }
            val attempted = correct + wrong
            AnalyticsSnapshot(
                total = total,
                attempted = attempted,
                correct = correct,
                wrong = wrong,
                unsolved = (total - attempted).coerceAtLeast(0),
                accuracy = if (attempted == 0) 0 else (correct * 100 / attempted).coerceIn(0, 100),
                mastery = if (total == 0) 0 else (correct * 100 / total).coerceIn(0, 100),
                due = due,
                todaySolved = today,
                bookmarks = bookmarks,
                notes = notesCached(),
                averageTimeMs = if (timed == 0) 0L else timeTotal / timed
            ).also { lastValid = it }
        }.getOrElse { lastValid ?: AnalyticsSnapshot() }
    }

    private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

class DashboardManager(
    private val context: Context, private val analytics: AnalyticsManager,
    private val todaySolved: TodaySolvedManager, private val adaptive: AdaptiveEngineManager
) {
    data class DashboardModel(val refs:List<QuestionRef>,val sources:List<Source>,val analytics:AnalyticsSnapshot,val bookmarks:Map<String,Int>,val sourceProgress:Map<Long,ProgressSummary>,val engine:AdaptiveEngineManager.State)
    @Volatile private var lastValid: DashboardModel? = null
    fun prepareForDisplay(){ PerformanceManager.invalidateProgress(); adaptive.recordRefresh(); adaptive.onEvent("dashboard_opened"); AppEventBus.publish(AppEventBus.Event(AppEventBus.Type.DASHBOARD_OPENED)) }

    /**
     * Source metadata is the critical visibility path for Home. It must never be hidden behind
     * a failure in an unrelated derived metric (analytics, progress, adaptive state, etc.).
     * Keep this query tiny and independently recoverable so a newly committed QBank can appear
     * even if a secondary dashboard calculation is temporarily unavailable.
     */
    fun sources(): List<Source> = runCatching {
        QBankDb(context.applicationContext).let { db -> try { db.sources() } finally { db.close() } }
    }.getOrElse { lastValid?.sources ?: emptyList() }

    fun build():DashboardModel {
        val app=context.applicationContext
        val sources=sources()
        val refs=runCatching { PerformanceManager.lightRefs(app) }.getOrElse { lastValid?.refs ?: emptyList() }
        val snap=runCatching { analytics.snapshot(refs) }.getOrElse { lastValid?.analytics ?: AnalyticsSnapshot(total=refs.size) }
        val progress=runCatching { PerformanceManager.progress(app) }.getOrElse { null }
        val bm=if(progress!=null) runCatching {
            mapOf(
                "important" to refs.count{progress.record(it.stableKey)?.bookmark=="important"},
                "revise" to refs.count{progress.record(it.stableKey)?.bookmark=="revise"},
                "doubt" to refs.count{progress.record(it.stableKey)?.bookmark=="doubt"},
                "favorite" to refs.count{val b=progress.record(it.stableKey)?.bookmark;b=="favorite"||b=="favourite"}
            )
        }.getOrElse { lastValid?.bookmarks ?: emptyMap() } else lastValid?.bookmarks ?: emptyMap()
        val sourceProgress=HashMap<Long,ProgressSummary>()
        if(progress!=null) runCatching {
            refs.groupBy { it.sourceId }.forEach { (sourceId, rows) ->
                var solved=0; var correct=0; var resume=0; var resumeFound=false
                rows.forEach { r ->
                    val p=progress.record(r.stableKey)
                    if(p?.status!=null){ solved++; if(p.status=="correct") correct++ }
                    else if(!resumeFound){ resume=r.position; resumeFound=true }
                }
                sourceProgress[sourceId]=ProgressSummary(rows.size,solved,correct,resume)
            }
        }.onFailure {
            lastValid?.sourceProgress?.let { sourceProgress.putAll(it) }
        }
        val today=runCatching { todaySolved.count(refs) }.getOrDefault(lastValid?.analytics?.todaySolved ?: 0)
        val engine=runCatching { adaptive.state() }.getOrElse { lastValid?.engine ?: AdaptiveEngineManager.State(0L,0L,0L,0L,0L,0L,0,0,2,5_000L) }
        val model=DashboardModel(refs,sources,snap.copy(todaySolved=today),bm,sourceProgress,engine)
        lastValid=model
        return model
    }
}

class TodaySolvedManager(context: Context) {
    private val app = context.applicationContext
    fun ids(refs: List<QuestionRef>, limit: Int = 200): LongArray {
        val since = startOfToday()
        val progress = PerformanceManager.progress(app)
        return refs.asSequence().filter { r ->
            val p = progress.record(r.stableKey)
            p != null && (p.status == "correct" || p.status == "wrong") && p.lastAttempted >= since
        }.map { it.id }.distinct().take(limit.coerceIn(1, 500)).toList().toLongArray()
    }
    fun count(refs: List<QuestionRef>): Int {
        val since = startOfToday(); val progress = PerformanceManager.progress(app)
        return refs.count { r -> progress.record(r.stableKey)?.let { (it.status == "correct" || it.status == "wrong") && it.lastAttempted >= since } == true }
    }
    private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Performs cheap integrity checks before a screen consumes progress-derived state. */
class DataHealthManager(context: Context) {
    private val app = context.applicationContext
    private val store = ProgressStore(app)

    fun sanitizeProgressKeys(validStableKeys: Set<String>): Int {
        if (validStableKeys.isEmpty()) return 0
        val entries = store.allEntries()
        val known = ConcurrentHashMap.newKeySet<String>()
        validStableKeys.forEach { known.add(it) }
        var removed = 0
        entries.keys.forEach { key ->
            val base = key.substringBeforeLast(':')
            val suffix = key.substringAfterLast(':', "")
            // Position/resume keys and source-level settings are intentionally preserved.
            if (suffix in setOf("answer", "status", "bookmark", "review", "attempts", "lastAttempted", "timeMs", "mistake", "ef", "reps", "intervalDays", "nextDue") && !known.contains(base)) {
                removed++
            }
        }
        return removed
    }
}

/** Small adaptive rule engine. It observes outcomes but does not directly mutate core state. */
class LearningManager(context: Context, private val analyticsManager: AnalyticsManager) {
    private val app = context.applicationContext

    fun recommendation(refs: List<QuestionRef>): String {
        return runCatching {
            val analytics = analyticsManager.snapshot(refs)
            when {
                analytics.attempted == 0 -> "Start with a mixed set to establish your baseline."
                analytics.due > 0 && analytics.due >= analytics.attempted / 2 -> "Clear the revision queue before adding much new volume."
                analytics.wrong > analytics.correct -> "Prioritize wrong questions and revisit their explanations."
                analytics.accuracy >= 85 -> "Accuracy is strong; use timed mixed practice to improve speed."
                else -> "Keep solving, then revisit weak questions on their scheduled due dates."
            }
        }.getOrDefault("Continue with a balanced mix of new questions and revision.")
    }
}

/** Safe extension used only for short-lived DB reads. */
private inline fun <T> QBankDb.useDb(block: QBankDb.() -> T): T {
    return try { block() } finally { close() }
}
