package com.localqbank.library

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-lifetime owner for the real EmbeddingGemma diagnostic.
 *
 * The diagnostics Activity is presentation only. A lifecycle-bound Activity coroutine must not
 * own a long native/ML operation: Activity recreation, a transient window failure, or leaving the
 * screen would otherwise cancel the IPC request and cause the worker's native coroutine to die.
 * The coordinator lives in the main application process and owns the request until a terminal
 * remote result is received or the user explicitly stops the test.
 */
class BenIsolatedEmbeddingDiagnosticCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    @Volatile private var activeTestId: String? = null
    @Volatile private var job: Job? = null

    fun isRunning(): Boolean = running.get()

    fun activeId(): String? = activeTestId

    fun start(context: Context): String? {
        if (!running.compareAndSet(false, true)) return activeTestId
        val app = context.applicationContext
        val testId = try {
            BenIpcDiagnosticRecorder.start(app, "EMBEDDINGGEMMA")
        } catch (error: Exception) {
            running.set(false)
            return null
        }
        activeTestId = testId
        job = scope.launch {
            try {
                val result = BenInferenceProcessClient(app).use { client ->
                    BenIpcDiagnosticRecorder.event(app, testId, "CLIENT_EMBEDDING_DIAGNOSTIC_BEGIN")
                    val ping = client.pingSuspend()
                    BenIpcDiagnosticRecorder.event(
                        app, testId,
                        if (ping.ok) "CLIENT_EMBEDDING_PING_PASS" else "CLIENT_EMBEDDING_PING_FAIL",
                        ping.failure ?: "ok"
                    )
                    if (!ping.ok) {
                        BenInferenceProcessClient.DiagnosticResult(null, ping.failure ?: "ISOLATED_EMBEDDING_PING_FAILED")
                    } else {
                        client.isolatedEmbeddingDiagnosticSuspend { progress ->
                            BenIpcDiagnosticRecorder.event(app, testId, "CLIENT_REMOTE_PROGRESS", progress)
                        }
                    }
                }

                BenIpcDiagnosticRecorder.event(app, testId, "COORDINATOR_RESULT_RECEIVED", "report=${!result.report.isNullOrBlank()} failure=${result.failure ?: "none"}")
                val report = result.report
                if (!report.isNullOrBlank()) {
                    val modelPassed = report.contains("Overall: PASS")
                    val traceResult = if (modelPassed) "PASS" else "MODEL_REVIEW"
                    BenIpcDiagnosticRecorder.event(app, testId, "COORDINATOR_FINISH_BEGIN", "result=$traceResult")
                    val trace = BenIpcDiagnosticRecorder.finish(app, testId, traceResult)
                    RovexDiagnosticsStore.publishIpc(
                        app,
                        trace + "\nREMOTE EMBEDDINGGEMMA REPORT\n===========================\n" + report
                    )
                    BenNeuralTelemetry.setDiagnosticReport(trace + "\nREMOTE EMBEDDINGGEMMA REPORT\n===========================\n" + report)
                } else {
                    val reason = result.failure ?: "ISOLATED_EMBEDDING_EMPTY_RESULT"
                    val trace = BenIpcDiagnosticRecorder.finish(app, testId, "FAIL", reason)
                    RovexDiagnosticsStore.publishIpc(app, trace)
                }
            } catch (cancel: CancellationException) {
                val trace = BenIpcDiagnosticRecorder.finish(
                    app, testId, "CANCELLED", "ISOLATED_EMBEDDING_DIAGNOSTIC_CANCELLED"
                )
                RovexDiagnosticsStore.publishIpc(app, trace)
                throw cancel
            } catch (error: Exception) {
                val reason = "ISOLATED_EMBEDDING_COORDINATOR_EXCEPTION:${error.javaClass.simpleName}:${error.message ?: "unknown"}".take(500)
                val trace = BenIpcDiagnosticRecorder.finish(app, testId, "FAIL", reason)
                RovexDiagnosticsStore.publishIpc(app, trace)
            } finally {
                activeTestId = null
                job = null
                running.set(false)
            }
        }
        return testId
    }

    /** User-requested cancellation only. Activity destruction never calls this. */
    fun cancel() {
        job?.cancel()
    }
}
