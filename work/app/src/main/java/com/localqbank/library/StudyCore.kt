package com.localqbank.library

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Canonical study-state facade. UI and engines use this facade instead of reaching into
 * SharedPreferences/SQLite directly for common study facts. The existing stores remain the
 * physical persistence layer so this migration is backwards compatible.
 */
class StudyStateRepository(context: Context) {
    private val app = context.applicationContext
    private val progress: ProgressRepository = ProgressStore(app)

    fun snapshot(stableKey: String): StudyState = runCatching {
        val r = PerformanceManager.progress(app).record(stableKey)
        StudyState(
            stableKey = stableKey,
            status = r?.status,
            bookmark = r?.bookmark,
            attempts = r?.attempts ?: 0,
            lastAttempted = r?.lastAttempted ?: 0L,
            nextDue = r?.nextDue ?: 0L,
            timeMs = r?.timeMs ?: 0L
        )
    }.getOrDefault(StudyState(stableKey))

    fun setBookmark(stableKey: String, bookmark: String?) = runCatching { progress.setBookmark(stableKey, bookmark) }.isSuccess
    fun isDue(stableKey: String): Boolean = runCatching { progress.isDue(stableKey) }.getOrDefault(true)

    fun commitAnswer(stableKey: String, answer: String, status: String): Boolean =
        runCatching { progress.setAnswer(stableKey, answer, status) }.isSuccess
}

data class StudyState(
    val stableKey: String,
    val status: String? = null,
    val bookmark: String? = null,
    val attempts: Int = 0,
    val lastAttempted: Long = 0L,
    val nextDue: Long = 0L,
    val timeMs: Long = 0L
)

/** Typed event spine with an asynchronous lane for non-UI observers. */
object StudyEventSpine {
    data class Event(
        val name: String,
        val stableKey: String? = null,
        val source: String = "system",
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: Map<String, String> = emptyMap()
    )

    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "study-event-spine") }

    fun subscribe(listener: (Event) -> Unit) { listeners.addIfAbsent(listener) }
    fun unsubscribe(listener: (Event) -> Unit) { listeners.remove(listener) }
    fun publish(event: Event) { listeners.forEach { runCatching { it(event) } } }
    fun publishAsync(event: Event) { executor.execute { publish(event) } }
}

/** Lightweight engine health contract used for capability discovery and diagnostics. */
data class EngineContractV2(
    val id: String,
    val version: Int,
    val capabilities: Set<String>,
    val mutates: Set<String> = emptySet()
)

interface StudyEngineV2 {
    fun contract(): EngineContractV2
    fun health(): EngineHealth
}

data class EngineHealth(val healthy: Boolean = true, val score: Int = 100, val detail: String = "ready")

/** Canonical transition policy. Screens may use it without coupling themselves to animation APIs. */
object TransitionCoordinator {
    const val ENTER_MS = 180L
    const val EXIT_MS = 150L
    const val SLIDE_DISTANCE_DP = 18

    fun install(activity: android.app.Activity) {
        // Keep Android's system/predictive-back transitions authoritative on Android 15+.
        // Only provide a subtle activity-enter animation on older releases where appropriate.
        if (android.os.Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}

/** Session continuity separate from crash recovery. Stores only the current study cursor. */
class StudySessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("study_session", Context.MODE_PRIVATE)
    fun save(sessionId: String, testId: String, position: Int, stableKey: String?) {
        // The cursor is deliberately tiny and is the user's resume point. Persist it
        // synchronously so a process kill immediately after an answer cannot discard it.
        prefs.edit().putString("id", sessionId).putString("test", testId).putInt("pos", position.coerceAtLeast(0))
            .putString("key", stableKey ?: "").putLong("at", System.currentTimeMillis()).commit()
    }
    fun current(): StudySession? {
        val id = prefs.getString("id", null) ?: return null
        return StudySession(id, prefs.getString("test", "").orEmpty(), prefs.getInt("pos", 0), prefs.getString("key", "").orEmpty(), PrefsCompat.long(prefs,"at",0L))
    }
    fun clear() { prefs.edit().clear().apply() }
}

data class StudySession(val id: String, val testId: String, val position: Int, val stableKey: String, val savedAt: Long)

/** Deterministic learning graph: question -> category -> test -> source. No opaque network graph. */
class LearningGraph(context: Context) {
    private val app = context.applicationContext
    data class Node(val id: String, val label: String, val type: String)
    data class Edge(val from: String, val to: String, val relation: String)

    fun neighbourhood(ref: QuestionRef): Pair<List<Node>, List<Edge>> {
        val q = Node("q:${ref.id}", "Q${ref.position}", "question")
        val c = Node("c:${ref.category.lowercase()}", ref.category, "category")
        val t = Node("t:${ref.testId}", ref.testTitle, "test")
        val s = Node("s:${ref.sourceId}", ref.sourceName, "source")
        return listOf(q, c, t, s) to listOf(
            Edge(q.id, c.id, "tests"), Edge(c.id, t.id, "belongs_to"), Edge(t.id, s.id, "from_source")
        )
    }
}

/** Study strategy is intentionally separate from Ren: it produces explainable ranked actions. */
class StudyStrategyEngine(context: Context) {
    private val app = context.applicationContext
    data class Recommendation(val title: String, val reason: String, val priority: Int, val ids: LongArray)

    fun recommend(limit: Int = 25): List<Recommendation> = runCatching {
        val refs = PerformanceManager.refs(app)
        val p = PerformanceManager.progress(app)
        val now = System.currentTimeMillis()
        val wrong = refs.filter { p.record(it.stableKey)?.status == "wrong" }.take(limit).map { it.id }.toLongArray()
        val due = refs.filter { r -> val x = p.record(r.stableKey); x != null && x.status != null && ((x.nextDue > 0L && now >= x.nextDue) || (x.nextDue == 0L && x.lastAttempted > 0L && now - x.lastAttempted >= when (x.attempts) { 1 -> 86_400_000L; 2 -> 3 * 86_400_000L; 3 -> 7 * 86_400_000L; 4 -> 14 * 86_400_000L; else -> 30 * 86_400_000L })) }.take(limit).map { it.id }.toLongArray()
        val unseen = refs.filter { p.record(it.stableKey) == null }.take(limit).map { it.id }.toLongArray()
        buildList {
            if (wrong.isNotEmpty()) add(Recommendation("Repair wrong answers", "Recent misses have the highest immediate learning value.", 100, wrong))
            if (due.isNotEmpty()) add(Recommendation("Clear due revision", "Spaced-repetition items are waiting for recall before adding more volume.", 90, due))
            if (unseen.isNotEmpty()) add(Recommendation("Add new questions", "Unseen questions expand coverage after weak/due work is controlled.", 60, unseen))
        }
    }.getOrDefault(emptyList())
}

/** Explainability surface for adaptive decisions. */
object AdaptiveExplainability {
    fun explain(state: AdaptiveEngineManager.State): String = buildString {
        append("Dr. Frankenstein is using ").append(state.userModel).append(" user model and ").append(state.systemModel).append(" device mode. ")
        append("Prefetch=").append(state.preferredPrefetch).append(", cache=").append(state.recommendedRefreshMs).append("ms. ")
        append("Confidence=").append(state.confidence).append("%. ")
        if (state.safeMode) append("Safe mode is active because recent runtime signals were degraded.")
        else append("The policy is bounded to reversible performance changes.")
    }
}

/** Four-tier local Ren memory; bounded and user-data-only. */
class RenMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ren_memory", Context.MODE_PRIVATE)
    fun putWorking(value: String) = put("working", value, 1200)
    fun putStudy(value: String) = put("study", value, 2400)
    fun working(): String = prefs.getString("working", "").orEmpty()
    fun study(): String = prefs.getString("study", "").orEmpty()
    private fun put(key: String, value: String, max: Int) { prefs.edit().putString(key, value.take(max)).apply() }
    fun clearWorking() { prefs.edit().remove("working").apply() }

    /** Bounded Frankenstein chat memory: recent user intent + answer only, never secrets. */
    fun appendChatTurn(role: String, text: String) {
        val safeRole = role.take(12).uppercase()
        val safeText = text.replace("\u0000", " ").trim().take(2200)
        if (safeText.isBlank()) return
        val existing = prefs.getString("frankenstein_chat_history", "").orEmpty()
        val next = (existing + "\n[$safeRole] $safeText").trim().takeLast(18_000)
        prefs.edit().putString("frankenstein_chat_history", next).apply()
    }

    fun chatContext(maxChars: Int = 9000): String =
        prefs.getString("frankenstein_chat_history", "").orEmpty().takeLast(maxChars.coerceIn(1000, 18_000))

    fun clearChatContext() { prefs.edit().remove("frankenstein_chat_history").apply() }

}

/** Import safety policy: bounded local parsing; never executes imported HTML/JS as trusted code. */
object ImportSecurityPolicy {
    const val MAX_HTML_BYTES = 260L * 1024L * 1024L
    const val MAX_QUESTION_TEXT = 250_000
    fun acceptsSize(bytes: Long): Boolean = bytes in 1..MAX_HTML_BYTES
    fun acceptsImportUri(uri: String): Boolean = runCatching {
        val scheme = android.net.Uri.parse(uri).scheme.orEmpty()
        scheme.equals("content", true) || scheme.equals("file", true)
    }.getOrDefault(false)
    fun isSafeRemoteUri(uri: String): Boolean = runCatching {
        val u = android.net.Uri.parse(uri)
        u.scheme.equals("https", true)
    }.getOrDefault(false)
}

/** Runtime diagnostics: cheap counters suitable for CI/device logging without tracing user content. */
object PerformanceLab {
    private val starts = ConcurrentHashMap<String, Long>()
    fun begin(name: String): String { val token = "$name:${System.nanoTime()}"; starts[token] = SystemClock.elapsedRealtime(); return token }
    fun end(token: String): Long { val t = starts.remove(token) ?: return -1L; return SystemClock.elapsedRealtime() - t }
}
