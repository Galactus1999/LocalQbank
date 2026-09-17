package com.localqbank.library

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Permanent, exportable diagnostics surface for Ben and future Rovex performance tests. */
class RovexDiagnosticsDataCenter : AppCompatActivity() {
    private lateinit var reportView: TextView
    private lateinit var summaryView: TextView
    private lateinit var statusView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var runButton: TextView
    private var diagnosticJob: Job? = null
    private val isolatedEmbeddingDiagnosticCoordinator
        get() = (application as LocalQBankApplication).isolatedEmbeddingDiagnosticCoordinator
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppManagers.initialize(applicationContext)
        SystemUi.immersive(this)
        // Recover an interrupted IPC test before constructing the UI. This is deliberately
        // independent of the Activity/coroutine that originally launched the test.
        BenIpcDiagnosticRecorder.reconcileTerminal(this)
        BenIpcDiagnosticRecorder.recoverStale(this)
        setContentView(build())
        if (intent.getBooleanExtra("OPEN_IPC", false)) {
            window.decorView.post { runIsolatedIpcDiagnostic() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::reportView.isInitialized) {
            BenIpcDiagnosticRecorder.reconcileTerminal(this)
            BenIpcDiagnosticRecorder.recoverStale(this)
            reportView.text = RovexDiagnosticsStore.latest(this) ?: "No completed diagnostic is stored yet."
            if (isolatedEmbeddingDiagnosticCoordinator.isRunning()) {
                progressBar.visibility = ProgressBar.VISIBLE
                runButton.text = "STOP TEST"
                runButton.setOnClickListener { isolatedEmbeddingDiagnosticCoordinator.cancel() }
                statusView.text = "RUNNING • isolated EmbeddingGemma diagnostic owned by application process"
            }
            refreshSnapshot()
        }
    }

    private fun build(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
            background=ThemeManager.backgroundDrawable(this@RovexDiagnosticsDataCenter)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"; textSize = 32f; gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(this@RovexDiagnosticsDataCenter))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        header.addView(TextView(this).apply {
            text = "Rovex Test & Diagnostics Center"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(this@RovexDiagnosticsDataCenter)); setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(header)
        root.addView(label("The single test window for EmbeddingGemma, Ben IPC, resources and future runtime measurements."), lpWrap(0, 10))

        val summary = card(); summary.addView(title("CURRENT SNAPSHOT")); summaryView = label(""); summary.addView(summaryView); root.addView(summary, cardLp(0, 10))

        val actions = card(); actions.addView(title("EMBEDDINGGEMMA FULL TEST"))
        actions.addView(label("Contract → tokenizer → backend/NPU → inference → semantic smoke test → RAM/thermal report. First runtime initialization can take around 20 seconds."), lpWrap(0, 6))
        statusView = label("Ready • no test running"); statusView.textSize = 13f; statusView.setTextColor(ThemeManager.text(this)); actions.addView(statusView, lpWrap(0, 4))
        progressBar = ProgressBar(this).apply { isIndeterminate = true; visibility = ProgressBar.GONE }
        actions.addView(progressBar, LinearLayout.LayoutParams(-1, dp(4)).apply { setMargins(0, dp(2), 0, dp(6)) })
        runButton = button("RUN FULL EMBEDDINGGEMMA TEST") { runEmbeddingDiagnostic() }
        actions.addView(runButton, lp(0, 8))
        actions.addView(button("RUN ISOLATED BEN IPC TEST") { runIsolatedIpcDiagnostic() }, lp(0, 4))
        actions.addView(button("RUN ISOLATED EMBEDDINGGEMMA TEST") { runIsolatedEmbeddingDiagnostic() }, lp(0, 4))
        actions.addView(label("The full EmbeddingGemma test is a direct main-process control test based on the previously working v8.3.140 diagnostic path. The IPC test is separate so a Binder/process failure cannot hide a model-runtime result."), lpWrap(0, 4))
        actions.addView(button("OPEN BEN MODEL LAB") { startActivity(Intent(this, BenModelLabActivity::class.java)) }, lp(0, 4))
        actions.addView(button("REFRESH SNAPSHOT") { refreshSnapshot() }, lp(0, 4))
        actions.addView(button("OPEN LAST BEN IPC REPORT") { openLastIpcReport() }, lp(0, 4))
        actions.addView(button("SHARE COMPLETE DIAGNOSTICS") { shareDiagnostics() }, lp(0, 4))
        root.addView(actions, cardLp(0, 10))

        val report = card(); report.addView(title("LATEST TEST REPORT"))
        val stored = RovexDiagnosticsStore.latest(this)
        val storedStatus = RovexDiagnosticsStore.latestStatus(this)
        reportView = label(when {
            !stored.isNullOrBlank() -> stored
            !storedStatus.isNullOrBlank() -> "Latest diagnostic status: $storedStatus"
            else -> "No completed diagnostic is stored yet."
        })
        reportView.textSize = 12f; reportView.setTextIsSelectable(true)
        report.addView(reportView, lpWrap(0, 6)); root.addView(report, cardLp(0, 10))

        val telemetry = card(); telemetry.addView(title("LIVE TELEMETRY")); telemetry.addView(label(telemetryText())); root.addView(telemetry, cardLp(0, 10))
        refreshSnapshot()
        return ScrollView(this).apply { addView(root); isFillViewport = true }
    }

    private fun runEmbeddingDiagnostic() {
        if (diagnosticJob?.isActive == true) return
        runButton.text = "STOP TEST"
        runButton.setOnClickListener { diagnosticJob?.cancel() }
        progressBar.visibility = ProgressBar.VISIBLE
        reportView.text = "Direct control test started. The proven v8.3.140-style EmbeddingGemma diagnostic is running in the main process.\n\nNo study data will be changed."
        statusView.text = "STARTING • direct EmbeddingGemma control path…"
        val started = System.currentTimeMillis()
        diagnosticJob = lifecycleScope.launch {
            try {
                val report = withTimeout(DIRECT_DIAGNOSTIC_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        BenEmbeddingGemmaEngine(applicationContext).diagnostic { progress ->
                            runOnUiThread { statusView.text = "RUNNING • $progress" }
                        }
                    }
                }
                if (!report.isNullOrBlank()) {
                    RovexDiagnosticsStore.publish(this@RovexDiagnosticsDataCenter, report)
                    BenNeuralTelemetry.setDiagnosticReport(report)
                    reportView.text = report
                    statusView.text = "COMPLETE • direct control report stored • ${System.currentTimeMillis() - started} ms"
                } else {
                    val reason = "DIRECT_DIAGNOSTIC_EMPTY_REPORT"
                    RovexDiagnosticsStore.publishFailure(this@RovexDiagnosticsDataCenter, reason)
                    reportView.text = "Direct EmbeddingGemma diagnostic failed.\n\nReason: $reason\n\nThe direct control path returned no report. No study data was changed."
                    statusView.text = "FAILED • $reason"
                }
                refreshSnapshot()
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                val reason = "DIRECT_DIAGNOSTIC_TIMEOUT_${DIRECT_DIAGNOSTIC_TIMEOUT_MS}MS"
                RovexDiagnosticsStore.publishFailure(this@RovexDiagnosticsDataCenter, reason)
                reportView.text = "Direct EmbeddingGemma diagnostic timed out.\n\nReason: $reason\n\nThis is independent of Binder/isolated-process IPC. No study data was changed."
                statusView.text = "FAILED • $reason"
            } catch (_: kotlinx.coroutines.CancellationException) {
                RovexDiagnosticsStore.publishFailure(this@RovexDiagnosticsDataCenter, "DIRECT_DIAGNOSTIC_CANCELLED")
                reportView.text = "Direct EmbeddingGemma diagnostic cancelled. No study data was changed."
                statusView.text = "CANCELLED • direct control test"
            } catch (e: Exception) {
                val reason = "DIRECT_DIAGNOSTIC_EXCEPTION: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}".take(500)
                RovexDiagnosticsStore.publishFailure(this@RovexDiagnosticsDataCenter, reason)
                reportView.text = "Direct EmbeddingGemma diagnostic failed.\n\nReason: $reason\n\nNo study data was changed."
                statusView.text = "FAILED • direct control exception"
            } finally {
                if (!isFinishing) finishDiagnosticUi()
            }
        }
    }

    private fun runIsolatedEmbeddingDiagnostic() {
        if (isolatedEmbeddingDiagnosticCoordinator.isRunning()) {
            statusView.text = "RUNNING • isolated EmbeddingGemma diagnostic already active"
            return
        }
        runButton.text = "STOP TEST"
        runButton.setOnClickListener {
            isolatedEmbeddingDiagnosticCoordinator.cancel()
            statusView.text = "STOPPING • isolated EmbeddingGemma"
        }
        progressBar.visibility = ProgressBar.VISIBLE
        reportView.text = "Isolated EmbeddingGemma diagnostic started. The test is now owned by the application process, not this Activity.\n\nNo study data will be changed."
        statusView.text = "STARTING • isolated EmbeddingGemma • Binder/FGS control path…"
        val testId = isolatedEmbeddingDiagnosticCoordinator.start(applicationContext)
        if (testId == null) {
            statusView.text = "RUNNING • isolated EmbeddingGemma diagnostic already active"
            return
        }
        lifecycleScope.launch {
            while (isolatedEmbeddingDiagnosticCoordinator.isRunning()) {
                val live = BenIpcDiagnosticRecorder.current(applicationContext)
                if (!live.isNullOrBlank()) reportView.text = live
                delay(350)
            }
            val latest = RovexDiagnosticsStore.latest(applicationContext)
                ?: BenIpcDiagnosticRecorder.latest(applicationContext)
            if (!latest.isNullOrBlank()) reportView.text = latest
            statusView.text = "COMPLETE • isolated EmbeddingGemma diagnostic terminal report stored"
            refreshSnapshot()
            finishDiagnosticUi()
        }
    }

    private fun runIsolatedIpcDiagnostic() {
        if (diagnosticJob?.isActive == true) return
        runButton.text = "STOP TEST"
        runButton.setOnClickListener { diagnosticJob?.cancel() }
        progressBar.visibility = ProgressBar.VISIBLE
        reportView.text = "Isolated Ben IPC diagnostic started. This test is intentionally separate from the direct EmbeddingGemma control test."
        statusView.text = "STARTING • binding isolated inference process…"
        val started = System.currentTimeMillis()
        val ipcTestId = BenIpcDiagnosticRecorder.start(this)
        diagnosticJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BenInferenceProcessClient(this@RovexDiagnosticsDataCenter).use { client ->
                        runOnUiThread { statusView.text = "RUNNING • IPC • Binder handshake…" }
                        BenIpcDiagnosticRecorder.event(this@RovexDiagnosticsDataCenter, ipcTestId, "CLIENT_PING_BEGIN")
                        val ping = client.pingSuspend()
                        BenIpcDiagnosticRecorder.event(this@RovexDiagnosticsDataCenter, ipcTestId, if (ping.ok) "CLIENT_PING_PASS" else "CLIENT_PING_FAIL", ping.failure ?: "ok")
                        if (!ping.ok) {
                            BenInferenceProcessClient.DiagnosticResult(null, ping.failure ?: "IPC_PING_FAILED")
                        } else {
                            runOnUiThread { statusView.text = "RUNNING • IPC • Binder handshake PASS • starting remote diagnostic…" }
                            BenIpcDiagnosticRecorder.event(this@RovexDiagnosticsDataCenter, ipcTestId, "CLIENT_REMOTE_DIAGNOSTIC_BEGIN")
                            client.diagnosticSuspend { progress ->
                                runOnUiThread { statusView.text = "RUNNING • IPC • $progress" }
                            }
                        }
                    }
                }
                val report = result.report
                if (!report.isNullOrBlank()) {
                    val traceReport = BenIpcDiagnosticRecorder.finish(this@RovexDiagnosticsDataCenter, ipcTestId, "PASS")
                    val combinedReport = traceReport + "\nREMOTE MODEL REPORT\n===================\n" + report
                    RovexDiagnosticsStore.publishIpc(this@RovexDiagnosticsDataCenter, combinedReport)
                    BenNeuralTelemetry.setDiagnosticReport(combinedReport)
                    reportView.text = combinedReport
                    statusView.text = "COMPLETE • isolated IPC report stored • ${System.currentTimeMillis() - started} ms"
                } else {
                    val reason = result.failure ?: "IPC_DIAGNOSTIC_EMPTY_RESULT"
                    val traceReport = BenIpcDiagnosticRecorder.finish(this@RovexDiagnosticsDataCenter, ipcTestId, "FAIL", reason)
                    RovexDiagnosticsStore.publishIpc(this@RovexDiagnosticsDataCenter, traceReport)
                    reportView.text = traceReport + "\n\n" + buildIpcFailureReport(reason, started)
                    statusView.text = "FAILED • ${reason.take(180)}"
                }
                refreshSnapshot()
            } catch (_: kotlinx.coroutines.CancellationException) {
                val traceReport = BenIpcDiagnosticRecorder.finish(this@RovexDiagnosticsDataCenter, ipcTestId, "CANCELLED", "IPC_DIAGNOSTIC_CANCELLED")
                RovexDiagnosticsStore.publishIpc(this@RovexDiagnosticsDataCenter, traceReport)
                reportView.text = traceReport
                statusView.text = "CANCELLED • IPC test"
            } catch (e: Exception) {
                val reason = "IPC_DIAGNOSTIC_EXCEPTION: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}".take(500)
                val traceReport = BenIpcDiagnosticRecorder.finish(this@RovexDiagnosticsDataCenter, ipcTestId, "FAIL", reason)
                reportView.text = traceReport
                statusView.text = "FAILED • IPC exception"
            } finally {
                if (!isFinishing) finishDiagnosticUi()
            }
        }
    }

    private fun buildIpcFailureReport(reason: String, started: Long): String {
        val elapsed = System.currentTimeMillis() - started
        val timeout = reason.startsWith("IPC_TIMEOUT_", ignoreCase = true)
        return buildString {
            append("Isolated Ben IPC diagnostic failed.\n\n")
            append("Reason: ").append(reason).append("\n\n")
            append("Elapsed: ").append(elapsed).append(" ms\n")
            append("Binder transport: PASS — request reached the isolated process\n")
            val marker = reason.substringAfter("_STAGE=", "NO_REMOTE_PROGRESS")
                .substringBefore(";IPC_TRANSPORT_RESET_REQUESTED")
            append("Last remote stage: ").append(marker.replace('_', ' ')).append("\n")
            if (timeout) {
                append("\nInterpretation: the client watchdog expired after the last reported remote stage.\n")
                append("Cancellation was attempted and the client reset the Binder/service transport so the next test starts from a fresh connection.\n")
                append("This does not prove EmbeddingGemma itself failed. Run the direct EmbeddingGemma test to separate model/runtime behavior from isolated-process IPC behavior.\n")
            }
            append("\nNo study data was changed.")
        }
    }

    private fun finishDiagnosticUi() {
        progressBar.visibility = ProgressBar.GONE
        runButton.text = "RUN FULL EMBEDDINGGEMMA TEST"
        runButton.setOnClickListener { runEmbeddingDiagnostic() }
    }

    private fun refreshSnapshot() {
        if (!::summaryView.isInitialized) return
        val t = BenNeuralTelemetry.snapshot()
        val g = BenAiResourceGovernor(this).snapshot(true)
        val latest = RovexDiagnosticsStore.latest(this)
        summaryView.text = "Stage: ${t.stage.name}\nModel: ${t.activeModel}\nLast latency: ${t.lastElapsedMs} ms\nConfidence: ${t.lastConfidence}%\nEvidence: ${t.lastEvidenceCount}\nVerified: ${if (t.lastVerified) "YES" else "NO"}\nRAM: ${g.availableMemoryMb} MB\nThermal: ${g.thermalStatus}\nPower-save: ${if (g.powerSave) "ON" else "OFF"}\nStored diagnostic: ${if (latest.isNullOrBlank()) "NONE" else "AVAILABLE"}"

    }

    private fun telemetryText(): String {
        val t = BenNeuralTelemetry.snapshot()
        return "Requests ${t.totalRequests} • neural ${t.neuralRequests} • fallback ${t.fallbackRequests} • blocked ${t.blockedRequests}\nSemantic runs ${t.semanticRuns} • generation runs ${t.generationRuns}\nLast error: ${t.lastError ?: "None"}"

    }

    private fun openLastIpcReport() {
        val report = BenIpcDiagnosticRecorder.latest(this)
        reportView.text = report ?: "No Ben IPC diagnostic report is available yet. Run the IPC test once."
        if (report != null) {
            statusView.text = "IPC REPORT • durable journal loaded"
            reportView.post { reportView.requestFocus() }
        }
    }

    private fun shareDiagnostics() {
        BenIpcDiagnosticRecorder.reconcileTerminal(this)
        BenIpcDiagnosticRecorder.recoverStale(this)
        val t = BenNeuralTelemetry.snapshot()
        val gov = BenAiResourceGovernor(this).snapshot(true)
        val ipcCurrent = BenIpcDiagnosticRecorder.current(this)
        val ipc = BenIpcDiagnosticRecorder.latest(this)
        val latest = RovexDiagnosticsStore.latest(this)
        val latestStatus = RovexDiagnosticsStore.latestStatus(this)
        val text = buildString {
            appendLine("ROVEX COMPLETE DIAGNOSTICS EXPORT")
            appendLine("exportedAt=${System.currentTimeMillis()}")
            appendLine("stage=${t.stage.name}")
            appendLine("stageDetail=${t.stageDetail}")
            appendLine("activeModel=${t.activeModel}")
            appendLine("modelKind=${t.modelKind}")
            appendLine("lastLatencyMs=${t.lastElapsedMs}")
            appendLine("lastSemanticMs=${t.lastSemanticMs}")
            appendLine("lastGenerationMs=${t.lastGenerationMs}")
            appendLine("evidenceCount=${t.lastEvidenceCount}")
            appendLine("verified=${t.lastVerified}")
            appendLine("confidence=${t.lastConfidence}")
            appendLine("totalRequests=${t.totalRequests}")
            appendLine("neuralRequests=${t.neuralRequests}")
            appendLine("fallbackRequests=${t.fallbackRequests}")
            appendLine("blockedRequests=${t.blockedRequests}")
            appendLine("semanticRuns=${t.semanticRuns}")
            appendLine("generationRuns=${t.generationRuns}")
            appendLine("ramAvailableMb=${gov.availableMemoryMb}")
            appendLine("thermalStatus=${gov.thermalStatus}")
            appendLine("powerSave=${gov.powerSave}")
            appendLine("lastError=${t.lastError ?: ""}")
            appendLine("latestDiagnosticStatus=${latestStatus ?: "NONE"}")
            appendLine("ipcDiagnostic=${if (ipc.isNullOrBlank()) "NONE" else "AVAILABLE"}")
            appendLine("ipcLiveJournal=${if (ipcCurrent.isNullOrBlank()) "NONE" else "AVAILABLE"}")
            appendLine("diagnosticStatusConsistency=${diagnosticStatusConsistency(latestStatus, ipcCurrent ?: ipc ?: latest)}")
            appendLine()
            appendLine(ipcCurrent ?: ipc ?: latest ?: "No diagnostic report stored.")
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Rovex complete diagnostics")
            putExtra(Intent.EXTRA_TEXT, text.take(28000))
        }, "Share Rovex diagnostics"))
    }

    private fun diagnosticStatusConsistency(status: String?, report: String?): String {
        if (report.isNullOrBlank()) return "NO_REPORT"

        // A live journal encodes the terminal result inside the TEST_TERMINAL detail,
        // while a persisted diagnostic report has a top-level result= line. Accept both
        // forms so export consistency reflects the actual durable state machine.
        val result = report.lineSequence()
            .firstOrNull { it.startsWith("result=") }
            ?.substringAfter('=')
            ?.trim()
            ?.uppercase()
            ?: report.lineSequence()
                .lastOrNull { it.contains("|TEST_TERMINAL|") }
                ?.substringAfter("result=")
                ?.substringBefore(" reason=")
                ?.trim()
                ?.uppercase()

        return when {
            result == "PASS" && status?.contains("FAILED", true) == true -> "MISMATCH_STALE_FAILURE"
            result == "PARTIAL_PASS" && status?.contains("FAILED", true) == true -> "MISMATCH_STALE_FAILURE"
            result == null -> "RESULT_NOT_DECLARED"
            status.isNullOrBlank() -> "STATUS_MISSING"
            else -> "CONSISTENT"
        }
    }

    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(ThemeManager.elevated(this@RovexDiagnosticsDataCenter), 18) }
    private fun title(t: String) = TextView(this).apply { text = t; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(this@RovexDiagnosticsDataCenter)) }
    private fun label(t: String) = TextView(this).apply { text = t; textSize = 12f; setTextColor(ThemeManager.muted(this@RovexDiagnosticsDataCenter)) }
    private fun button(t: String, click: () -> Unit) = TextView(this).apply { text = t; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(ThemeManager.accent(this@RovexDiagnosticsDataCenter), 13); setOnClickListener { click() } }
    private fun lp(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun lpWrap(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun cardLp(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun rounded(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }

    companion object {
        // Direct control path is intentionally generous because v8.3.140 was known to need
        // substantial cold-start time on the Qualcomm/SM8650 runtime.
        private const val DIRECT_DIAGNOSTIC_TIMEOUT_MS = 150_000L
    }
}
