package com.localqbank.library

import android.content.Context
import android.os.Build
import android.app.ActivityManager
import android.os.Debug
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Durable, bounded, model-independent Ben IPC diagnostic journal.
 *
 * IMPORTANT: the Ben worker runs in a separate normal application process. Android
 * SharedPreferences are not a safe cross-process transport, so the IPC trace uses
 * an append-only file journal in the shared application data directory. Critical
 * writes are forced to storage before returning. The journal therefore survives
 * Activity recreation and ordinary process death much better than an in-memory
 * or SharedPreferences-only trace.
 */
object BenIpcDiagnosticRecorder {
    private const val PREFS = "ben_ipc_diagnostic"
    private const val ACTIVE = "active"
    private const val ACTIVE_STARTED = "active_started"
    private const val ACTIVE_KIND = "active_kind"
    private const val MAX_CHARS = 14000
    private const val MAX_EVENT_CHARS = 500
    private const val RECOVERY_STALE_MS = 180_000L
    private const val TERMINAL_RECONCILE_GRACE_MS = 2_000L
    private const val JOURNAL_NAME = "ben_ipc_diagnostic.journal"
    private const val ACTIVE_FILE_NAME = "ben_ipc_diagnostic.active"
    private const val TERMINAL_MARKER = "|TEST_TERMINAL|"
    private const val DIAGNOSTIC_SCHEMA_VERSION = 17
    private val sequence = AtomicLong(0)

    private fun journal(context: Context) = File(context.applicationContext.filesDir, JOURNAL_NAME)
    private fun activeFile(context: Context) = File(context.applicationContext.filesDir, ACTIVE_FILE_NAME)

    @Synchronized
    fun start(context: Context, kind: String = "IPC"): String {
        val app = context.applicationContext
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val journal = journal(app)
        // A diagnostic run is single-flight. Replace an old stale run only after preserving it.
        recoverStale(app)
        journal.parentFile?.mkdirs()
        FileOutputStream(journal, false).use { it.channel.use { ch -> ch.force(true) } }
        writeAtomic(activeFile(app), "$id|$now|${kind.take(32)}")
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACTIVE, id)
            .putLong(ACTIVE_STARTED, now)
            .putString(ACTIVE_KIND, kind.take(32))
            .commit()
        val mem = runCatching {
            val info = ActivityManager.MemoryInfo()
            (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            "availRamMb=${info.availMem / (1024L * 1024L)} lowRam=${info.lowMemory} totalRamMb=${info.totalMem / (1024L * 1024L)}"
        }.getOrDefault("memory=unavailable")
        val heap = runCatching {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            memoryInfo.totalPss
        }.getOrDefault(-1)
        val appVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
        val appVersionCode = runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()
        }.getOrDefault("unknown")
        event(app, id, "TEST_START", "kind=${kind.take(32)} diagnosticSchemaVersion=$DIAGNOSTIC_SCHEMA_VERSION appVersionName=$appVersion appVersionCode=$appVersionCode androidRelease=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} manufacturer=${Build.MANUFACTURER} abi=${Build.SUPPORTED_ABIS.joinToString(",")} cores=${Runtime.getRuntime().availableProcessors()} $mem mainPid=${android.os.Process.myPid()} pssKb=$heap")
        event(app, id, "DIAGNOSTIC_COVERAGE_DECLARED",
            "artifact_hash;tokenizer_parse;graph_contract;runtime_lib_inventory;qnn_provider;compiled_model_create;tensor_contract;input_write;native_run;output_read;embedding_dimension;finite_values;embedding_norm;semantic_cosine;ram;java_heap;native_heap;thermal;power_save;fgs;binder;worker_lifecycle;watchdog;worker_terminal_reply;client_reply_received;client_reply_decode;client_terminal_receipt;terminal_reply;native_cleanup;diagnostic_state_consistency")
        return id
    }

    @Synchronized
    fun event(context: Context, testId: String?, stage: String, detail: String = "") {
        val app = context.applicationContext
        val id = testId ?: activeId(app) ?: return
        if (id == "NO_TEST") return
        val safeDetail = detail.replace("\n", " ").take(MAX_EVENT_CHARS)
        val processPid = android.os.Process.myPid()
        val line = "${System.currentTimeMillis()}|${sequence.incrementAndGet()}|$id|$stage|pid=$processPid detail=$safeDetail\n"
        appendForced(journal(app), line)
        // Keep a lightweight compatibility marker for existing telemetry/UI code.
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACTIVE, id)
            .commit()
    }

    @Synchronized
    fun finish(context: Context, testId: String?, result: String, reason: String? = null): String {
        val app = context.applicationContext
        val id = testId ?: activeId(app)
        val trace = readBounded(journal(app))
        val alreadyTerminal = trace.contains(TERMINAL_MARKER)
        if (!alreadyTerminal) {
            event(app, id, "TEST_TERMINAL", "result=$result reason=${reason?.take(400).orEmpty()}")
        }
        val finalTrace = readBounded(journal(app))
        val effectiveResult = finalTrace.lineSequence()
            .lastOrNull { it.contains(TERMINAL_MARKER) }
            ?.substringAfter("result=")
            ?.substringBefore(" reason=")
            ?.ifBlank { null }
            ?: result
        val effectiveReason = finalTrace.lineSequence()
            .lastOrNull { it.contains(TERMINAL_MARKER) }
            ?.substringAfter(" reason=", "")
            ?.takeIf { it.isNotBlank() }
            ?: reason
        val report = buildReport(app, id, effectiveResult, effectiveReason, resolvedKind(app, finalTrace))
        RovexDiagnosticsStore.publishIpc(app, report)
        clearActive(app)
        return report
    }

    /**
     * Called whenever the diagnostics UI opens. If the previous run disappeared before
     * it could execute finish(), convert the surviving journal into an explicit report.
     */
    @Synchronized
    fun recoverStale(context: Context): String? {
        val app = context.applicationContext
        val id = activeId(app) ?: return null
        val journalText = readBounded(journal(app))
        if (journalText.isBlank()) {
            clearActive(app)
            return null
        }
        val terminalLine = journalText.lineSequence().lastOrNull { it.contains(TERMINAL_MARKER) }
        if (terminalLine != null) {
            val result = terminalLine.substringAfter("result=").substringBefore(" reason=").ifBlank { "RECOVERED" }
            val reason = terminalLine.substringAfter(" reason=", "").takeIf { it.isNotBlank() }
            val report = buildReport(app, id, result, reason, resolvedKind(app, journalText))
            RovexDiagnosticsStore.publishIpc(app, report)
            clearActive(app)
            return report
        }
        val lastTimestamp = journalText.lineSequence().lastOrNull { it.isNotBlank() }
            ?.substringBefore('|')?.toLongOrNull() ?: 0L
        val ageMs = if (lastTimestamp > 0L) System.currentTimeMillis() - lastTimestamp else Long.MAX_VALUE
        val kind = activeKind(app)
        val embeddingInferenceStarted = journalText.contains("|WORKER_EMBEDDING_INFERENCE_BEGIN|")
        val staleLimit = if (kind.equals("EMBEDDINGGEMMA", true) && embeddingInferenceStarted) 30_000L else RECOVERY_STALE_MS
        if (ageMs < staleLimit) return null
        val lastStage = journalText.lineSequence()
            .filter { it.isNotBlank() }
            .lastOrNull()
            ?.split('|')?.getOrNull(3)
            ?.ifBlank { "UNKNOWN" } ?: "UNKNOWN"
        val failureClass = classifyInterruptedStage(journalText, embeddingInferenceStarted)
        val reason = failureClass
        val report = buildReport(app, id, "INTERRUPTED", "$reason;lastStage=$lastStage;lastEventAgeMs=$ageMs", kind)
        RovexDiagnosticsStore.publishIpc(app, report)
        clearActive(app)
        return report
    }

    fun latest(context: Context): String? =
        reconcileTerminal(context) ?: RovexDiagnosticsStore.latestIpc(context) ?: recoverStale(context)

    /**
     * Reconciles durable worker-side terminal evidence with the main-process diagnostic state.
     * The worker writes WORKER_EMBEDDING_REPLY_PASS immediately after sending the terminal Binder
     * reply. If the main process is interrupted before its callback runs, the journal can therefore
     * contain stronger evidence than SharedPreferences. Never downgrade that evidence to FAILED.
     */
    @Synchronized
    fun reconcileTerminal(context: Context): String? {
        val app = context.applicationContext
        val id = activeId(app) ?: return null
        val trace = readBounded(journal(app))
        if (trace.isBlank()) return null
        if (trace.contains(TERMINAL_MARKER)) {
            val terminalLine = trace.lineSequence().lastOrNull { it.contains(TERMINAL_MARKER) }
            val result = terminalLine?.substringAfter("result=")?.substringBefore(" reason=")?.ifBlank { null }
            val report = buildReport(app, id, result ?: "RECOVERED", terminalLine?.substringAfter(" reason=", "")?.takeIf { it.isNotBlank() }, resolvedKind(app, trace))
            RovexDiagnosticsStore.publishIpc(app, report)
            clearActive(app)
            return report
        }

        val workerReply = trace.contains("|WORKER_EMBEDDING_REPLY_PASS|")
        val clientReceived = trace.contains("|CLIENT_EMBEDDING_REPLY_RECEIVED|")
        val clientPass = trace.contains("|CLIENT_EMBEDDING_REPLY_PASS|")
        if (workerReply) {
            if (!clientPass) {
                val workerReplyAgeMs = traceLastAgeMs(trace)
                if (workerReplyAgeMs in 0 until TERMINAL_RECONCILE_GRACE_MS) return null
            }
            val result = if (clientPass) "PASS" else "PARTIAL_PASS"
            val reason = when {
                clientPass -> null
                clientReceived -> "EMBEDDING_CLIENT_REPLY_DECODE_OR_COMPLETION_MISSING"
                else -> "EMBEDDING_WORKER_REPLY_SENT_CLIENT_RECEIPT_MISSING"
            }
            event(app, id, "DIAGNOSTIC_TERMINAL_RECONCILED", "derived=$result clientReceived=$clientReceived clientPass=$clientPass")
            event(app, id, "TEST_TERMINAL", "result=$result reason=${reason.orEmpty()}")
            val report = buildReport(app, id, result, reason, resolvedKind(app, trace))
            RovexDiagnosticsStore.publishIpc(app, report)
            clearActive(app)
            return report
        }
        return null
    }

    /**
     * Returns only the journal for an actually active diagnostic. Once the terminal
     * state has been reconciled and active state is cleared, the durable report in
     * RovexDiagnosticsStore is authoritative. Returning the old journal after that
     * point made COMPLETE exports look like a live run and caused the consistency
     * parser to report RESULT_NOT_DECLARED even though the terminal result was PASS.
     */
    fun current(context: Context): String? {
        val app = context.applicationContext
        if (activeId(app) == null) return null
        reconcileTerminal(app)
        return readBounded(journal(app)).takeIf { it.isNotBlank() }
    }

    fun isRunning(context: Context): Boolean = activeId(context.applicationContext) != null

    private fun classifyInterruptedStage(trace: String, embeddingInferenceStarted: Boolean): String {
        if (!embeddingInferenceStarted) return "IPC_DIAGNOSTIC_PROCESS_OR_ACTIVITY_INTERRUPTED"
        return when {
            trace.contains("|WORKER_EMBEDDING_INFERENCE_3_PASS|") && !trace.contains("|WORKER_EMBEDDING_POST_INFERENCE_BEGIN|") ->
                "EMBEDDING_POST_INFERENCE_TRANSITION_FAILURE_OR_PROCESS_DEATH"
            trace.contains("|WORKER_EMBEDDING_POST_INFERENCE_BEGIN|") && !trace.contains("|WORKER_EMBEDDING_OUTPUT_VALIDATION_PASS|") && !trace.contains("|WORKER_EMBEDDING_OUTPUT_VALIDATION_FAIL|") ->
                "EMBEDDING_OUTPUT_VALIDATION_FAILURE_OR_PROCESS_DEATH"
            trace.contains("|WORKER_EMBEDDING_SEMANTIC_SCORE_BEGIN|") && !trace.contains("|WORKER_EMBEDDING_SEMANTIC_SCORE_PASS|") ->
                "EMBEDDING_SEMANTIC_SCORING_FAILURE_OR_PROCESS_DEATH"
            trace.contains("|WORKER_EMBEDDING_REPORT_BUILD_BEGIN|") && !trace.contains("|WORKER_EMBEDDING_REPORT_BUILD_PASS|") ->
                "EMBEDDING_REPORT_BUILD_FAILURE_OR_PROCESS_DEATH"
            trace.contains("|WORKER_EMBEDDING_RUNTIME_CLOSE_BEGIN|") && !trace.contains("|WORKER_EMBEDDING_RUNTIME_CLOSE_PASS|") ->
                "EMBEDDING_NATIVE_RUNTIME_CLEANUP_STALL_OR_PROCESS_DEATH"
            trace.contains("|WORKER_EMBEDDING_REPLY_PASS|") && !trace.contains("|CLIENT_EMBEDDING_REPLY_PASS|") ->
                when {
                    trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_BEGIN|") && !trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_PASS|") ->
                        "EMBEDDING_CLIENT_REPLY_DECODE_FAILURE_OR_MAIN_PROCESS_INTERRUPTION"
                    trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_PASS|") ->
                        "EMBEDDING_CLIENT_REPLY_COMPLETION_STATE_FAILURE"
                    trace.contains("|CLIENT_EMBEDDING_REPLY_RECEIVED|") ->
                        "EMBEDDING_CLIENT_REPLY_DECODE_OR_COMPLETION_MISSING"
                    else ->
                        "EMBEDDING_WORKER_REPLY_SENT_CLIENT_RECEIPT_MISSING"
                }
            trace.contains("|WORKER_EMBEDDING_ENGINE_COMPLETE|") && !trace.contains("|WORKER_EMBEDDING_REPLY_PASS|") ->
                "EMBEDDING_TERMINAL_REPLY_FAILURE_OR_PROCESS_DEATH"
            trace.contains("|WORKER_EMBEDDING_NATIVE_RUN_BEGIN|") && !trace.contains("|WORKER_EMBEDDING_NATIVE_RUN_PASS|") ->
                "EMBEDDING_NATIVE_RUN_STALL_OR_PROCESS_DEATH"
            trace.contains("|WORKER_EMBEDDING_OUTPUT_READ_BEGIN|") && !trace.contains("|WORKER_EMBEDDING_OUTPUT_READ_PASS|") ->
                "EMBEDDING_OUTPUT_READ_FAILURE_OR_PROCESS_DEATH"
            else -> "EMBEDDING_UNKNOWN_POST_STAGE_FAILURE_OR_PROCESS_DEATH"
        }
    }

    private fun buildReport(context: Context, testId: String?, result: String, reason: String?, kind: String?): String {
        val app = context.applicationContext
        val trace = readBounded(journal(app))
        return buildString {
            if (kind.equals("EMBEDDINGGEMMA", true)) {
                appendLine("BEN EMBEDDINGGEMMA ISOLATED DIAGNOSTIC REPORT")
                appendLine("===========================================")
            } else {
                appendLine("BEN IPC DIAGNOSTIC REPORT")
                appendLine("=========================")
            }
            appendLine("testId=${testId ?: "unknown"}")
            appendLine("result=$result")
            if (kind.equals("EMBEDDINGGEMMA", true)) {
                appendLine("modelsRequired=true")
                appendLine("modelsLoaded=unknown (real model/runtime diagnostic)")
            } else {
                appendLine("modelsRequired=false")
                appendLine("modelsLoaded=false (transport/lifecycle diagnostic)")
            }
            if (!reason.isNullOrBlank()) appendLine("failure=${reason.take(500)}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine()
            appendLine("FORENSIC CLASSIFICATION")
            appendLine("failureClass=${reason ?: "NONE"}")
            appendLine("lastStage=${traceStage(trace)}")
            appendLine("lastEventAgeMs=${traceLastAgeMs(trace)}")
            appendLine("workerPid=${traceValue(trace, "WORKER_ON_CREATE", "pid") ?: "unknown"}")
            appendLine("workerLifecycle=${traceLifecycle(trace)}")
            appendLine("nativeRuns=${countStage(trace, "WORKER_EMBEDDING_NATIVE_RUN_PASS")}")
            appendLine("nativeRunTimingsMs=${traceDetails(trace, "WORKER_EMBEDDING_NATIVE_RUN_PASS", "ms")}")
            appendLine("inferencePasses=${countStage(trace, "WORKER_EMBEDDING_INFERENCE_1_PASS") + countStage(trace, "WORKER_EMBEDDING_INFERENCE_2_PASS") + countStage(trace, "WORKER_EMBEDDING_INFERENCE_3_PASS")}")
            appendLine("postInferenceReached=${trace.contains("|WORKER_EMBEDDING_POST_INFERENCE_BEGIN|")}")
            appendLine("semanticScoreReached=${trace.contains("|WORKER_EMBEDDING_SEMANTIC_SCORE_PASS|")}")
            appendLine("reportBuildReached=${trace.contains("|WORKER_EMBEDDING_REPORT_BUILD_PASS|")}")
            appendLine("workerTerminalReplyReached=${trace.contains("|WORKER_EMBEDDING_REPLY_PASS|")}")
            appendLine("clientTerminalReplyReceived=${trace.contains("|CLIENT_EMBEDDING_REPLY_RECEIVED|")}")
            appendLine("clientReplyDecodeBegin=${trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_BEGIN|")}")
            appendLine("clientReplyDecodePass=${trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_PASS|")}")
            appendLine("clientReplyDecodeFail=${trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_FAIL|")}")
            appendLine("clientTerminalReplyPass=${trace.contains("|CLIENT_EMBEDDING_REPLY_PASS|")}")
            appendLine("terminalReplyReached=${trace.contains("|CLIENT_EMBEDDING_REPLY_PASS|")}")
            appendLine("terminalEvidence=${when { trace.contains("|CLIENT_EMBEDDING_REPLY_PASS|") -> "END_TO_END_PASS"; trace.contains("|WORKER_EMBEDDING_REPLY_PASS|") -> "WORKER_REPLY_SENT_CLIENT_ACK_MISSING"; else -> "NO_TERMINAL_REPLY" }}")
            appendLine("processDeathSuspected=${result == "INTERRUPTED" && !trace.contains("|TEST_TERMINAL|")}")
            appendLine()
            appendLine("FUTURE FAILURE COVERAGE / GATE STATUS")
            appendLine("artifact_hash=${gateStatus(trace, "WORKER_EMBEDDING_PROGRESS", "Hashing model + tokenizer", "NOT_RECORDED")}")
            appendLine("tokenizer_parse=${if (trace.contains("Parsing SentencePiece tokenizer")) "REACHED" else "NOT_REACHED"}")
            appendLine("graph_contract=${if (trace.contains("Inspecting model graph contract")) "REACHED" else "NOT_REACHED"}")
            appendLine("runtime_lib_inventory=${if (trace.contains("Preparing verified Qualcomm runtime libraries")) "REACHED" else "NOT_REACHED"}")
            appendLine("qnn_provider=${if (trace.contains("Checking Qualcomm NPU provider")) "REACHED" else "NOT_REACHED"}")
            appendLine("compiled_model_create=${if (trace.contains("|WORKER_EMBEDDING_COMPILED_MODEL_CREATE_PASS|")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_COMPILED_MODEL_CREATE_BEGIN|")) "IN_PROGRESS_OR_PROCESS_DEATH" else "NOT_REACHED"}")
            appendLine("tensor_contract=${if (trace.contains("Tensor contract ready")) "PASS" else "NOT_REACHED_OR_FAILED"}")
            appendLine("input_write=${if (trace.contains("|WORKER_EMBEDDING_INPUT_WRITE_PASS|")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_INPUT_WRITE_BEGIN|")) "IN_PROGRESS_OR_PROCESS_DEATH" else "NOT_REACHED"}")
            appendLine("native_run=${if (trace.contains("|WORKER_EMBEDDING_NATIVE_RUN_PASS|")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_NATIVE_RUN_BEGIN|")) "IN_PROGRESS_OR_PROCESS_DEATH" else "NOT_REACHED"}")
            appendLine("output_read=${if (trace.contains("|WORKER_EMBEDDING_OUTPUT_READ_PASS|")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_OUTPUT_READ_BEGIN|")) "IN_PROGRESS_OR_PROCESS_DEATH" else "NOT_REACHED"}")
            appendLine("embedding_dimension=${if (trace.contains("|WORKER_EMBEDDING_OUTPUT_VALIDATION_PASS|")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_OUTPUT_VALIDATION_FAIL|")) "FAIL" else "NOT_REACHED"}")
            appendLine("finite_values=${if (trace.contains("finite=true")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_OUTPUT_VALIDATION_FAIL|")) "FAIL" else "NOT_REACHED"}")
            appendLine("embedding_norm=${if (trace.contains("|WORKER_EMBEDDING_SEMANTIC_SCORE_PASS|")) "AVAILABLE" else "NOT_REACHED"}")
            appendLine("semantic_cosine=${if (trace.contains("|WORKER_EMBEDDING_SEMANTIC_SCORE_PASS|")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_SEMANTIC_SCORE_BEGIN|")) "IN_PROGRESS_OR_PROCESS_DEATH" else "NOT_REACHED"}")
            appendLine("ram_java_heap_native_heap_thermal_power_save=CAPTURED_AT_TEST_START_AND_MODEL_REPORT")
            appendLine("fgs=${if (trace.contains("|FGS_PROMOTION_PASS|")) "PASS" else "NOT_REACHED_OR_FAILED"}")
            appendLine("binder=${if (trace.contains("|CLIENT_WORKER_READY|")) "PASS" else "NOT_REACHED_OR_FAILED"}")
            appendLine("worker_lifecycle=${traceLifecycle(trace)}")
            appendLine("watchdog=${if (trace.contains("WATCHDOG")) "RECORDED" else "NO_WATCHDOG_EVENT"}")
            appendLine("worker_terminal_reply=${if (trace.contains("|WORKER_EMBEDDING_REPLY_PASS|")) "PASS" else "NOT_REACHED_OR_FAILED"}")
            appendLine("client_terminal_receipt=${if (trace.contains("|CLIENT_EMBEDDING_REPLY_PASS|")) "PASS" else if (trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_PASS|")) "DECODE_PASS_COMPLETION_MISSING" else if (trace.contains("|CLIENT_EMBEDDING_REPLY_DECODE_BEGIN|")) "DECODE_STARTED_NOT_COMPLETED" else if (trace.contains("|CLIENT_EMBEDDING_REPLY_RECEIVED|")) "RECEIVED_NOT_DECODED" else if (trace.contains("|WORKER_EMBEDDING_REPLY_PASS|")) "WORKER_SENT_CLIENT_RECEIPT_MISSING" else "NOT_REACHED_OR_FAILED"}")
            appendLine("terminal_reply=${if (trace.contains("|CLIENT_EMBEDDING_REPLY_PASS|")) "PASS" else "NOT_END_TO_END"}")
            appendLine("native_cleanup=${if (trace.contains("|WORKER_EMBEDDING_RUNTIME_CLOSE_PASS|")) "PASS" else if (trace.contains("|WORKER_EMBEDDING_RUNTIME_CLOSE_BEGIN|")) "IN_PROGRESS_OR_PROCESS_DEATH" else "NOT_REACHED"}")
            appendLine("host_level_next_if_native_death=logcat+tombstone+bugreport; LiteRT run_model CPU/NPU A-B; QNN native reproduction; Saver/IR graph; culprit_finder; AOT-vs-JIT; exact LiteRT+QAIRT library inventory")
            appendLine()
            appendLine("EVENT TRACE (oldest → newest)")
            append(trace)
        }.take(MAX_CHARS)
    }

    private fun gateStatus(trace: String, stage: String, detail: String, fallback: String): String =
        when {
            trace.contains(detail) -> "REACHED"
            trace.contains("|$stage|") -> "REACHED"
            else -> fallback
        }

    private fun traceStage(trace: String): String =
        trace.lineSequence().filter { it.isNotBlank() }.lastOrNull()?.split('|')?.getOrNull(3) ?: "UNKNOWN"

    private fun traceLastAgeMs(trace: String): Long {
        val ts = trace.lineSequence().filter { it.isNotBlank() }.lastOrNull()?.substringBefore('|')?.toLongOrNull() ?: return -1L
        return (System.currentTimeMillis() - ts).coerceAtLeast(0L)
    }

    private fun traceValue(trace: String, stage: String, key: String): String? {
        val detail = trace.lineSequence().lastOrNull { it.contains("|$stage|") }
            ?.substringAfter("|$stage|") ?: return null
        return Regex("(?:^|[\\s;|])${Regex.escape(key)}=([^\\s;|]+)")
            .find(detail)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun traceLifecycle(trace: String): String {
        val states = buildList {
            if (trace.contains("|WORKER_ON_CREATE|")) add("create")
            if (trace.contains("|WORKER_ON_UNBIND|")) add("unbind")
            if (trace.contains("|WORKER_ON_DESTROY|")) add("destroy")
        }
        return if (states.isEmpty()) "unknown" else states.joinToString(">")
    }

    private fun countStage(trace: String, prefix: String): Int =
        trace.lineSequence().count { it.contains("|$prefix") && it.contains("|") }

    private fun traceDetails(trace: String, stage: String, key: String): String =
        trace.lineSequence().filter { it.contains("|$stage|") }
            .mapNotNull { line ->
                val detail = line.substringAfter("|$stage|")
                Regex("(?:^|[\\s;|])${Regex.escape(key)}=([^\\s;|]+)")
                    .find(detail)?.groupValues?.getOrNull(1)
            }
            .joinToString(",")

    private fun resolvedKind(context: Context, trace: String): String =
        kindFromTrace(trace) ?: activeKind(context) ?: "IPC"

    private fun kindFromTrace(trace: String): String? {
        val detail = trace.lineSequence().firstOrNull { it.contains("|TEST_START|") }
            ?.substringAfter("|TEST_START|") ?: return null
        return Regex("(?:^|[\\s;|])kind=([^\\s;|]+)")
            .find(detail)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun activeId(context: Context): String? {
        val file = activeFile(context)
        val raw = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        return raw.substringBefore('|').takeIf { it.isNotBlank() && it != "NO_TEST" }
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE, null)
                ?.takeIf { it.isNotBlank() && it != "NO_TEST" }
    }

    private fun activeKind(context: Context): String? {
        val app = context.applicationContext
        val raw = runCatching { activeFile(app).readText(StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        val fileKind = raw.split('|').getOrNull(2)?.takeIf { it.isNotBlank() }
        return fileKind ?: app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_KIND, "IPC")
    }

    private fun clearActive(context: Context) {
        activeFile(context).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(ACTIVE).remove(ACTIVE_STARTED).remove(ACTIVE_KIND).commit()
    }

    private fun appendForced(file: File, text: String) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.channel.lock().use {
                raf.seek(raf.length())
                raf.write(text.toByteArray(StandardCharsets.UTF_8))
                if (raf.length() > MAX_CHARS * 2L) {
                    raf.seek(maxOf(0L, raf.length() - MAX_CHARS))
                    val tail = ByteArray(minOf(MAX_CHARS.toLong(), raf.length()).toInt())
                    raf.readFully(tail)
                    raf.setLength(0L)
                    raf.seek(0L)
                    raf.write(tail)
                }
                raf.fd.sync()
            }
        }
    }

    private fun readBounded(file: File): String = runCatching {
        if (!file.exists()) return ""
        val bytes = file.readBytes()
        val start = maxOf(0, bytes.size - MAX_CHARS * 2)
        String(bytes, start, bytes.size - start, StandardCharsets.UTF_8).takeLast(MAX_CHARS)
    }.getOrDefault("")

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp, false).use { out ->
            out.write(text.toByteArray(StandardCharsets.UTF_8)); out.flush(); out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.writeText(text, StandardCharsets.UTF_8)
            tmp.delete()
        }
    }
}
