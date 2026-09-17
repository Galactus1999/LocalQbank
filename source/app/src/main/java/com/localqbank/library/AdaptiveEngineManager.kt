package com.localqbank.library

import android.app.ActivityManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Rovex Adaptive Intelligence Core.
 *
 * Closed loop:
 * Observe -> feature extraction -> predict/plan -> policy guard -> actuate -> verify -> learn.
 *
 * The engine is deliberately NOT allowed to modify question data, progress, schema, themes,
 * executable code or backup contents. It controls only bounded runtime performance policies.
 */
class AdaptiveEngineManager(context: Context) {
    enum class Action(val key: String) {
        PREFETCH_1("prefetch_1"), PREFETCH_2("prefetch_2"), PREFETCH_3("prefetch_3"),
        CACHE_SHORT("cache_short"), CACHE_NORMAL("cache_normal"), CACHE_LONG("cache_long"),
        SAFE_MODE("safe_mode")
    }

    data class State(
        val observations: Long, val answers: Long, val correct: Long, val wrong: Long,
        val refreshes: Long, val navigations: Long, val learningScore: Int, val confidence: Int,
        val preferredPrefetch: Int, val recommendedRefreshMs: Long,
        val autonomousChanges: Long = 0L, val lastAction: String = "none",
        val health: Int = 100, val exploration: Int = 0, val recentActions: List<String> = emptyList(),
        val uncertainty: Int = 100, val safeMode: Boolean = false,
        val userModel: String = "cold", val systemModel: String = "normal",
        val lastOutcome: String = "none", val rollbackCount: Long = 0L
    )

    data class ActionProposal(
        val action: Action,
        val reason: String,
        val confidence: Int,
        val expectedRenefit: String,
        val context: String
    )

    data class ActionRecord(
        val action: Action,
        val timestamp: Long,
        val reason: String,
        val confidence: Int,
        val result: String,
        val previousPrefetch: Int,
        val previousCacheTtlMs: Long,
        val newPrefetch: Int,
        val newCacheTtlMs: Long
    )

    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "adaptive-engine") }
    private val prefs = app.getSharedPreferences("adaptive_engine", Context.MODE_PRIVATE)
    private val running = AtomicBoolean(true)

    private val minActuationGapMs = 8_000L
    private val minLearningSamples = 6L
    private val maxHistory = 16
    private val bandit = ThompsonBanditPolicy()

    private fun ema(key: String, value: Float, alpha: Float): Float {
        val old = PrefsCompat.float(prefs,key,.5f)
        return old + alpha * (value - old)
    }

    private fun observeInternal(event: String) {
        val observations = PrefsCompat.long(prefs,"observations",0L) + 1L
        val e = prefs.edit()
            .putLong("observations", observations)
            .putLong(event, PrefsCompat.long(prefs,event,0L) + 1L)
        when (event) {
            "answer_correct" -> e.putFloat("accuracy_ema", ema("accuracy_ema", 1f, .08f))
            "answer_wrong" -> e.putFloat("accuracy_ema", ema("accuracy_ema", 0f, .08f))
        }
        e.apply()
    }

    private fun userModel(): String {
        val answers = PrefsCompat.long(prefs,"answers",0L)
        val acc = PrefsCompat.float(prefs,"accuracy_ema",.5f)
        return AdaptivePolicyMath.userModel(answers, acc)
    }

    private fun systemModel(): String {
        val health = PerformanceManager.healthScore()
        val memory = memoryPressure()
        return when {
            health < 55 || memory >= 85 -> "constrained"
            health < 75 || memory >= 70 -> "cautious"
            else -> "normal"
        }
    }

    private fun memoryPressure(): Int = runCatching {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (((info.totalMem - info.availMem).toDouble() / info.totalMem.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }.getOrDefault(0)

    /** Delegates to ThompsonBanditPolicy (pure, unit-tested); see that class for the approximation. */
    private fun sampleBeta(alpha0: Double, beta0: Double): Double = bandit.sampleBeta(alpha0, beta0)

    private fun doublePref(key: String, fallback: Double): Double =
        prefs.getString(key, null)?.toDoubleOrNull() ?: fallback

    private fun contextBucket(): String = "${userModel()}:${systemModel()}"

    private fun selectAction(): Action {
        val bucket = contextBucket()
        return listOf(Action.PREFETCH_1, Action.PREFETCH_2, Action.PREFETCH_3,
            Action.CACHE_SHORT, Action.CACHE_NORMAL, Action.CACHE_LONG).maxByOrNull { action ->
            val a = doublePref("$bucket:${action.key}:a", 1.0)
            val b = doublePref("$bucket:${action.key}:b", 1.0)
            sampleBeta(a, b)
        } ?: Action.PREFETCH_2
    }

    private fun proposeAction(): ActionProposal {
        val user = userModel()
        val system = systemModel()
        val health = PerformanceManager.healthScore()
        if (system == "constrained") {
            return ActionProposal(Action.SAFE_MODE, "System health or memory pressure is high", 95,
                "Protect responsiveness and avoid background pressure", "$user/$system")
        }
        val action = selectAction()
        val reason = when {
            action.key.startsWith("prefetch") -> "Recent question navigation/loading data suggests prefetch tuning may reduce next-question latency."
            else -> "Recent cache behaviour suggests a different retention window may improve responsiveness."
        }
        val confidence = confidence()
        val renefit = if (action.key.startsWith("prefetch")) "Lower question-to-question latency" else "Balance cache hits, freshness and memory"
        return ActionProposal(action, reason, confidence, renefit, "$user/$system")
    }

    /** Hard safety contract: AI may only control bounded performance knobs. */
    private fun policyAllows(proposal: ActionProposal): Boolean {
        if (!running.get()) return false
        if (proposal.confidence < 35 && PrefsCompat.long(prefs,"observations",0L) >= minLearningSamples) return false
        if (System.currentTimeMillis() - PrefsCompat.long(prefs,"last_action_at",0L) < minActuationGapMs) return false
        if (proposal.action == Action.SAFE_MODE) return true
        if (systemModel() == "constrained") return false
        return proposal.action in setOf(
            Action.PREFETCH_1, Action.PREFETCH_2, Action.PREFETCH_3,
            Action.CACHE_SHORT, Action.CACHE_NORMAL, Action.CACHE_LONG
        )
    }

    private fun confidence(): Int {
        val observations = PrefsCompat.long(prefs,"observations",0L)
        val answers = PrefsCompat.long(prefs,"answers",0L)
        return AdaptivePolicyMath.confidence(observations, answers)
    }

    private fun recordAction(record: ActionRecord) {
        val history = prefs.getString("action_history", "")?.lineSequence()
            ?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        val line = buildString {
            append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp)))
            append(" • ").append(record.action.key)
            append(" • ").append(record.result)
            append(" • ").append(record.reason)
        }
        history.add(0, line)
        while (history.size > maxHistory) history.removeAt(history.lastIndex)
        prefs.edit()
            .putLong("autonomous_changes", PrefsCompat.long(prefs,"autonomous_changes",0L) + 1L)
            .putLong("last_action_at", record.timestamp)
            .putString("last_action", record.action.key)
            .putString("last_reason", record.reason)
            .putString("last_outcome", record.result)
            .putString("action_history", history.joinToString("\n"))
            .apply()
    }

    private fun actuate(proposal: ActionProposal) {
        if (!policyAllows(proposal)) return
        val previousPrefetch = PerformanceManager.adaptivePrefetchDepth()
        val previousTtl = PerformanceManager.adaptiveCacheTtlMs()
        when (proposal.action) {
            Action.PREFETCH_1 -> PerformanceManager.setAdaptivePolicy(prefetchDepth = 1)
            Action.PREFETCH_2 -> PerformanceManager.setAdaptivePolicy(prefetchDepth = 2)
            Action.PREFETCH_3 -> PerformanceManager.setAdaptivePolicy(prefetchDepth = 3)
            Action.CACHE_SHORT -> PerformanceManager.setAdaptivePolicy(cacheTtlMs = 2_000L)
            Action.CACHE_NORMAL -> PerformanceManager.setAdaptivePolicy(cacheTtlMs = 5_000L)
            Action.CACHE_LONG -> PerformanceManager.setAdaptivePolicy(cacheTtlMs = 12_000L)
            Action.SAFE_MODE -> PerformanceManager.setSafeMode(true)
        }
        val now = System.currentTimeMillis()
        val result = if (proposal.action == Action.SAFE_MODE) "safe_mode" else "applied"
        recordAction(ActionRecord(proposal.action, now, proposal.reason, proposal.confidence, result,
            previousPrefetch, previousTtl, PerformanceManager.adaptivePrefetchDepth(), PerformanceManager.adaptiveCacheTtlMs()))
        prefs.edit()
            .putInt("last_confidence", proposal.confidence)
            .putString("last_context", proposal.context)
            .putString("last_renefit", proposal.expectedRenefit)
            .putInt("last_prev_prefetch", previousPrefetch)
            .putLong("last_prev_cache", previousTtl)
            .apply()
    }

    private fun learnAction(action: Action, reward: Double) {
        val bucket = contextBucket()
        val ak = "$bucket:${action.key}:a"
        val bk = "$bucket:${action.key}:b"
        val a = doublePref(ak, 1.0)
        val b = doublePref(bk, 1.0)
        val (newA, newB) = bandit.updateBelief(a, b, reward)
        val r = reward.coerceIn(0.0, 1.0)
        prefs.edit().putString(ak, newA.toString()).putString(bk, newB.toString()).apply()
        prefs.edit().putLong("last_reward_at", System.currentTimeMillis()).putFloat("last_reward", r.toFloat()).apply()
    }

    private fun maybeRollback() {
        val outcome = prefs.getString("last_outcome", "") ?: ""
        if (outcome != "applied") return
        val latency = PrefsCompat.long(prefs,"last_latency_ms",0L)
        val baseline = PrefsCompat.long(prefs,"last_baseline_latency_ms",0L)
        if (latency <= 0L || baseline <= 0L) return
        if (latency > baseline * 1.35) {
            val previousPrefetch = prefs.getInt("last_prev_prefetch", 2).coerceIn(1, 3)
            val previousTtl = PrefsCompat.long(prefs,"last_prev_cache",5_000L).coerceIn(2_000L, 12_000L)
            PerformanceManager.setAdaptivePolicy(previousPrefetch, previousTtl)
            prefs.edit()
                .putLong("rollback_count", PrefsCompat.long(prefs,"rollback_count",0L) + 1L)
                .putString("last_outcome", "rolled_back")
                .putLong("last_rollback_at", System.currentTimeMillis())
                .apply()
            learnAction(lastAction(), 0.0)
        }
    }

    /** Receives an application event and closes the learning/action loop asynchronously. */
    fun onEvent(event: String, latencyMs: Long = 0L) {
        if (!running.get()) return
        executor.execute {
            runCatching {
                observeInternal(event)
                if (latencyMs > 0L) {
                    prefs.edit().putLong("last_latency_ms", latencyMs).apply()
                    PerformanceManager.reportLatency(latencyMs)
                }
                if (event == "answer_correct" || event == "answer_wrong") {
                    learnAction(lastAction(), if (event == "answer_correct") .7 else .3)
                }
                if (event == "question_opened" || event == "navigation" || event == "database_slow") {
                    val reward = AdaptivePolicyMath.navigationReward(latencyMs)
                    if (latencyMs > 0L) learnAction(lastAction(), reward)
                    if (event == "database_slow") {
                        PerformanceManager.setSafeMode(true)
                        PerformanceManager.setAdaptivePolicy(prefetchDepth = 1, cacheTtlMs = 2_000L)
                    } else if (isAutonomyEnabled()) {
                        maybeRollback()
                        if (shouldActuate()) {
                            val proposal = proposeAction()
                            if (policyAllows(proposal)) {
                                prefs.edit().putLong("last_baseline_latency_ms", latencyMs.coerceAtLeast(1L)).apply()
                                actuate(proposal)
                            }
                        }
                    }
                }
                if (event == "system_recovered" && PerformanceManager.healthScore() >= 80) {
                    PerformanceManager.setSafeMode(false)
                }
            }
        }
    }

    private fun shouldActuate(): Boolean =
        System.currentTimeMillis() - PrefsCompat.long(prefs,"last_action_at",0L) >= minActuationGapMs

    private fun lastAction(): Action = runCatching {
        Action.values().firstOrNull { it.key == prefs.getString("last_action", "") } ?: Action.PREFETCH_2
    }.getOrDefault(Action.PREFETCH_2)

    fun observeAsync(event: String) = onEvent(event)
    fun observe(event: String) { if (running.get()) observeInternal(event) }
    fun recordAnswer(correct: Boolean) {
        if (!running.get()) return
        executor.execute {
            runCatching {
                prefs.edit().putLong("answers", PrefsCompat.long(prefs,"answers",0L) + 1L).apply()
                onEvent(if (correct) "answer_correct" else "answer_wrong")
            }
        }
    }
    fun recordNavigation() = onEvent("navigation")
    fun recordRefresh() = onEvent("refresh")

    fun state(): State {
        val o = PrefsCompat.long(prefs,"observations",0L)
        val a = PrefsCompat.long(prefs,"answers",0L)
        val c = PrefsCompat.long(prefs,"answer_correct",0L)
        val w = PrefsCompat.long(prefs,"answer_wrong",0L)
        val r = PrefsCompat.long(prefs,"refresh",0L)
        val n = PrefsCompat.long(prefs,"navigation",0L)
        val acc = PrefsCompat.float(prefs,"accuracy_ema", if (a == 0L) .5f else c.toFloat() / max(1L, a))
        val conf = confidence()
        val score = min(100, (o * .35f + a * 1.2f + conf * .4f).toInt())
        val health = PerformanceManager.healthScore()
        val exploration = if (conf < 30) 20 else if (conf < 70) 8 else 3
        val history = prefs.getString("action_history", "")?.lineSequence()?.filter { it.isNotBlank() }?.toList() ?: emptyList()
        return State(o, a, c, w, r, n, score, conf,
            PerformanceManager.adaptivePrefetchDepth(),
            when { a < 20 -> 15_000L; r > 200 && acc >= .75f -> 45_000L; else -> 30_000L },
            PrefsCompat.long(prefs,"autonomous_changes",0L), prefs.getString("last_action", "none") ?: "none",
            health, exploration, history, 100 - conf, PerformanceManager.isSafeMode(), userModel(), systemModel(),
            prefs.getString("last_outcome", "none") ?: "none", PrefsCompat.long(prefs,"rollback_count",0L))
    }

    fun insight(): String {
        val st = state()
        return when {
            st.safeMode -> "Adaptive Safe Mode is active. I detected constrained system conditions and reduced background pressure to protect solving responsiveness."
            st.rollbackCount > 0 && st.lastOutcome == "rolled_back" -> "I reverted my last runtime change because measured latency worsened. I will learn from the failed experiment before trying again."
            st.autonomousChanges == 0L -> "I am observing your solving pattern and device behaviour before making autonomous changes."
            st.lastAction.startsWith("prefetch_") -> "I changed question prefetch to ${st.preferredPrefetch}. I am measuring whether that reduces next-question latency without increasing system pressure."
            st.lastAction.startsWith("cache_") -> "I changed cache lifetime to ${PerformanceManager.adaptiveCacheTtlMs() / 1000}s to balance cache hits, freshness and memory."
            else -> "I am continuously measuring your study behaviour and the device's runtime health; autonomous changes remain bounded and reversible."
        }
    }

    fun architectureSummary(): String = "Observe → Feature Store → User/System Models → Thompson Learner → Action Planner → Policy Guard → Manager Action → Measure → Verify/Rollback → Learn\n\nUser model: accuracy and solving behaviour\nSystem model: latency, memory and runtime health\nActs on: prefetch and cache policy\nSafety: bounded capabilities, confidence gate, cooldown, safe mode and rollback\nAI cannot modify question data, progress, schema or executable code"

    fun currentProposal(): ActionProposal = proposeAction()

    fun managerContracts(): String = ManagerContractRegistry.all.joinToString("\n") { contract ->
        "${contract.name}: inputs=${contract.inputs.joinToString(", ")}; outputs=${contract.outputs.joinToString(", ")}; failure=${contract.failureMode}"
    }

    fun isAutonomyEnabled(): Boolean = prefs.getBoolean("autonomy_enabled", true)

    fun setAutonomyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("autonomy_enabled", enabled).apply()
        if (!enabled) PerformanceManager.setSafeMode(false)
    }

    fun studyStrategy(): String {
        val st = state()
        return when {
            st.safeMode -> "Protect performance: background pressure has been reduced until the device recovers."
            st.answers < 20 -> "Build a baseline: use Today's Revision and mixed questions before fine-tuning weak areas."
            st.userModel == "weak" -> "Consolidate weak areas: prioritise Wrong Questions and Weakest 50, then revisit PYQs."
            st.userModel == "strong" -> "Shift to retention: mix PYQs with timed exam blocks and spaced flashcards."
            else -> "Maintain a balanced cycle: Today's Revision → weak topics → PYQs → timed practice."
        }
    }

    fun resetLearning() {
        val autonomy = isAutonomyEnabled()
        prefs.edit().clear().putBoolean("autonomy_enabled", autonomy).apply()
        PerformanceManager.setSafeMode(false)
        PerformanceManager.setAdaptivePolicy(prefetchDepth = 2, cacheTtlMs = 5_000L)
    }

    fun shutdown() { running.set(false); executor.shutdownNow() }
}
