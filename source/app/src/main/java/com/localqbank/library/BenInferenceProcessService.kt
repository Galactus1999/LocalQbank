package com.localqbank.library

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.Parcel
import android.os.PowerManager
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Isolated neural-runtime service.
 *
 * v8.3.145: single-flight/latest-request-wins generation, explicit remote cancellation, native
 * LiteRT-LM async streaming, and a release barrier before a replacement request may run.
 */
class BenInferenceProcessService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Dedicated scope for the long real-model diagnostic; never coupled to ordinary request cancellation. */
    private val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val singleFlight = Mutex()
    private val requests = ConcurrentHashMap<String, GenerationControl>()
    private val oneShotRequests = ConcurrentHashMap<String, OneShotControl>()
    private val serviceGenerationId = AtomicLong(System.nanoTime())
    private val latestGenerationSequence = AtomicLong(0L)
    private val handlerThread by lazy { HandlerThread("ben-inference-binder").also { it.start() } }
    private var generator: BenLiteRtLmGenerator? = null
    private var embedding: BenEmbeddingGemmaEngine? = null
    private var generatorWarm = false
    private var embeddingWarm = false
    private lateinit var messenger: Messenger
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val lifecycleHandler by lazy { Handler(mainLooper) }
    /** Independent watchdog scheduler: it must not share Dispatchers.IO with native work. */
    private val ipcWatchdog: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ben-ipc-watchdog").apply { isDaemon = true }
    }
    private val warmTrimRunnable = Runnable {
        if (requests.isEmpty() && oneShotRequests.isEmpty()) {
            scope.launch {
                singleFlight.withLock { closeRuntimes() }
                stopSelf()
            }
        }
    }

    // Foreground protection is provided by the main-process BenInferenceForegroundService.
    private var wakeLock: PowerManager.WakeLock? = null
    private val activeForegroundOps = AtomicInteger(0)
    private val shuttingDown = AtomicBoolean(false)

    /**
     * Foreground protection is owned by BenInferenceForegroundService in the main process.
     * The worker only acquires a bounded wake lock while native work is active.
     */
    private fun beginForegroundWork(): Boolean {
        activeForegroundOps.incrementAndGet()
        runCatching {
            val lock = wakeLock ?: powerManager
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Rovex:BenInference")
                ?.apply { setReferenceCounted(false) }
                ?.also { wakeLock = it }
            lock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        }.onFailure { BenNeuralTelemetry.error("Wake lock acquire failed: ${it.javaClass.simpleName}: ${it.message}") }
        return true
    }

    private fun endForegroundWork() {
        if (activeForegroundOps.updateAndGet { (it - 1).coerceAtLeast(0) } != 0) return
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
    }


    private val thermalListener = if (Build.VERSION.SDK_INT >= 29) object : PowerManager.OnThermalStatusChangedListener {
        override fun onThermalStatusChanged(status: Int) {
            if (status >= PowerManager.THERMAL_STATUS_SEVERE) activeGeneration?.cancel()
        }
    } else null
    @Volatile private var activeGeneration: GenerationControl? = null

    private class GenerationControl(
        val requestId: String,
        val clientGeneration: Long,
        val reply: Messenger,
        val sequence: Long,
    ) {
        val cancelled = AtomicBoolean(false)
        val terminalSent = AtomicBoolean(false)
        val nativeReleased = AtomicBoolean(false)
        val nativeStarted = AtomicBoolean(false)
        val lifecycle = BenIpcLifecycleState()
        @Volatile var job: Job? = null
        @Volatile var nativeCancel: (() -> Unit)? = null
        val tokenLock = Any()
        val pendingTokens = StringBuilder()
        @Volatile var tokenFlush: ScheduledFuture<*>? = null

        fun cancel() {
            if (cancelled.compareAndSet(false, true)) runCatching { nativeCancel?.invoke() }
        }
    }

    private class OneShotControl(
        val requestId: String,
        val clientGeneration: Long,
        val reply: Messenger?,
        val survivesWorkerUnbind: Boolean = false,
    ) {
        val cancelled = AtomicBoolean(false)
        val terminalSent = AtomicBoolean(false)
        val phase = AtomicReference("queued")
        @Volatile var job: Job? = null
        @Volatile var watchdog: ScheduledFuture<*>? = null
    }

    override fun onCreate() {
        super.onCreate()
        BenIpcDiagnosticRecorder.event(this, null, "WORKER_ON_CREATE", "pid=${android.os.Process.myPid()}")
        messenger = Messenger(Handler(handlerThread.looper) { message -> handle(message) })
        if (Build.VERSION.SDK_INT >= 29) runCatching { thermalListener?.let { powerManager?.addThermalStatusListener(it) } }
    }

    private fun handle(message: Message): Boolean {
        if (bundleSizeBytes(message.data) > MAX_IPC_PAYLOAD_BYTES) {
            val replyCode = when (message.what) {
                CMD_PING -> REPLY_PING
                CMD_DIAGNOSTIC -> REPLY_DIAGNOSTIC
                CMD_ISOLATED_EMBEDDING_DIAGNOSTIC -> REPLY_ISOLATED_EMBEDDING_DIAGNOSTIC
                else -> REPLY_GENERATE
            }
            sendReply(message.replyTo, replyCode, Bundle().apply {
                putString(KEY_REQUEST_ID, message.data.getString(KEY_REQUEST_ID).orEmpty())
                putLong(KEY_CLIENT_GENERATION, message.data.getLong(KEY_CLIENT_GENERATION))
                putBoolean(KEY_FAILED, true)
                putString(KEY_ERROR, "IPC_PAYLOAD_TOO_LARGE")
            })
            return true
        }
        if (shuttingDown.get() && message.what != CMD_CANCEL) {
            sendCommandFailure(message, "INFERENCE_SERVICE_SHUTTING_DOWN")
            return true
        }
        BenIpcDiagnosticRecorder.event(this, null, "WORKER_COMMAND", "what=${message.what} request=${message.data.getString(KEY_REQUEST_ID)?.take(8).orEmpty()}")
        when (message.what) {
            CMD_GENERATE -> startGeneration(message)
            CMD_CANCEL -> cancelGeneration(message.data.getString(KEY_REQUEST_ID).orEmpty())
            CMD_RERANK, CMD_DIAGNOSTIC, CMD_COMPARE, CMD_ISOLATED_EMBEDDING_DIAGNOSTIC -> startOneShot(message)
            CMD_PING -> {
                BenIpcDiagnosticRecorder.event(this, null, "WORKER_PING_PASS")
                sendReply(message.replyTo, REPLY_PING, Bundle().apply {
                putString(KEY_REQUEST_ID, message.data.getString(KEY_REQUEST_ID).orEmpty())
                putLong(KEY_CLIENT_GENERATION, message.data.getLong(KEY_CLIENT_GENERATION))
                putBoolean(KEY_OK, true)
            })
            }
            else -> {
                sendCommandFailure(message, "IPC_UNKNOWN_COMMAND_${message.what}")
                return true
            }
        }
        return true
    }

    private fun startGeneration(message: Message) {
        val requestId = message.data.getString(KEY_REQUEST_ID).orEmpty()
        val reply = message.replyTo
        if (reply == null) {
            BenNeuralTelemetry.error("IPC generate rejected: missing reply Messenger")
            return
        }
        if (requestId.isBlank()) {
            sendCommandFailure(message, "IPC_MISSING_REQUEST_ID")
            return
        }
        activeGeneration?.cancel()
        requests.values.forEach { if (it !== activeGeneration) it.cancel() }
        val control = GenerationControl(
            requestId = requestId,
            clientGeneration = message.data.getLong(KEY_CLIENT_GENERATION),
            reply = reply,
            sequence = latestGenerationSequence.incrementAndGet(),
        )
        requests[requestId] = control
        control.job = scope.launch {
            if (!beginForegroundWork()) {
                sendTerminal(control, failed = true, error = "IPC_FGS_PROMOTION_FAILED")
                requests.remove(requestId, control)
                return@launch
            }
            val watchdog = launch {
                delay(GENERATION_HARD_TIMEOUT_MS)
                if (!control.terminalSent.get() && requests[control.requestId] === control) {
                    BenNeuralTelemetry.error("Generation hard deadline exceeded; terminating isolated inference process")
                    Process.killProcess(Process.myPid())
                }
            }
            try {
                singleFlight.withLock {
                    if (control.cancelled.get() || control.sequence != latestGenerationSequence.get()) {
                        // A newer request can supersede this control before it acquires the
                        // single-flight native lock. It still owns a client-side Flow, so it must
                        // receive exactly one terminal event rather than silently disappearing.
                        sendTerminal(control, cancelled = true)
                        return@withLock
                    }
                    activeGeneration = control
                    control.nativeStarted.set(true)
                    try {
                        runGeneration(control, benIpcGetText(message.data, KEY_PROMPT), message.data.getInt(KEY_MAX_TOKENS, 160))
                    } finally {
                        watchdog.cancel()
                        control.lifecycle.released()
                        control.nativeReleased.set(true)
                        activeGeneration = null
                    }
                }
            } finally {
                watchdog.cancel()
                requests.remove(requestId, control)
                endForegroundWork()
            }
        }
    }

    private suspend fun runGeneration(control: GenerationControl, prompt: String, maxTokens: Int) {
        val governor = BenAiResourceGovernor(applicationContext)
        val policy = BenAiRuntimePolicy(applicationContext)
        val profile = BenNeuralModelRegistry.gemma3_270m
        val installed = BenNeuralModelManager(applicationContext).installed(profile)
        if (installed == null || !governor.mayRun(policy, profile.estimatedRuntimeMb, foreground = true)) {
            sendTerminal(control, failed = true, error = "Generation blocked by model/resource policy")
            return
        }
        val promptBudget = BenAiResourceGate.promptBudgetChars(governor.snapshot(foreground = true).thermalStatus)
        if (promptBudget <= 0) {
            sendTerminal(control, failed = true, error = "Thermal safety gate denied neural generation")
            return
        }
        try {
            if (generator == null) generator = BenLiteRtLmGenerator(applicationContext)
            val stream = generator!!.generateStream(
                prompt = prompt.take(promptBudget),
                maxOutputTokens = maxTokens,
                onCancel = { control.cancelled.get() },
                onNativeCancelReady = { cancel -> control.nativeCancel = cancel },
            )
            stream
                .catch { error ->
                    if (error is CancellationException || control.cancelled.get()) {
                        sendTerminal(control, cancelled = true)
                    } else {
                        policy.recordBackendFailure()
                        sendTerminal(control, failed = true, error = error.message ?: error.javaClass.simpleName)
                    }
                }
                .collect { chunk ->
                    if (!control.cancelled.get()) sendToken(control, chunk)
                }
            if (control.cancelled.get()) {
                sendTerminal(control, cancelled = true)
            } else {
                policy.recordBackendSuccess()
                sendTerminal(control, text = controlReplyText.remove(control.requestId)?.toString().orEmpty())
            }
        } catch (error: Exception) {
            if (error is Error) throw error
            if (control.cancelled.get() || error is CancellationException) sendTerminal(control, cancelled = true)
            else {
                policy.recordBackendFailure()
                sendTerminal(control, failed = true, error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private val controlReplyText = ConcurrentHashMap<String, StringBuilder>()

    private fun sendToken(control: GenerationControl, token: String) {
        if (control.cancelled.get() || control.terminalSent.get()) return
        controlReplyText.getOrPut(control.requestId) { StringBuilder() }.append(token)
        synchronized(control.tokenLock) {
            if (control.cancelled.get() || control.terminalSent.get()) return
            control.pendingTokens.append(token)
            val flushNow = control.pendingTokens.length >= TOKEN_BATCH_MAX_CHARS
            if (flushNow) {
                control.tokenFlush?.cancel(false)
                control.tokenFlush = null
            } else if (control.tokenFlush == null || control.tokenFlush?.isDone == true) {
                control.tokenFlush = ipcWatchdog.schedule(
                    { flushTokens(control) },
                    TOKEN_BATCH_WINDOW_MS,
                    TimeUnit.MILLISECONDS,
                )
                return
            }
        }
        flushTokens(control)
    }

    private fun flushTokens(control: GenerationControl, allowReschedule: Boolean = true) {
        val chunk: String
        synchronized(control.tokenLock) {
            if (control.pendingTokens.isEmpty()) {
                control.tokenFlush = null
                return
            }
            val length = minOf(control.pendingTokens.length, TOKEN_BATCH_MAX_CHARS)
            chunk = control.pendingTokens.substring(0, length)
            control.pendingTokens.delete(0, length)
            control.tokenFlush = null
            if (allowReschedule && control.pendingTokens.isNotEmpty() && !control.cancelled.get() && !control.terminalSent.get()) {
                control.tokenFlush = ipcWatchdog.schedule(
                    { flushTokens(control) },
                    TOKEN_BATCH_WINDOW_MS,
                    TimeUnit.MILLISECONDS,
                )
            }
        }
        if (control.cancelled.get() || control.terminalSent.get()) return
        sendReply(control.reply, REPLY_TOKEN, Bundle().apply {
            putString(KEY_REQUEST_ID, control.requestId)
            putLong(KEY_CLIENT_GENERATION, control.clientGeneration)
            putString(KEY_TOKEN, chunk)
        })
    }

    private fun sendTerminal(
        control: GenerationControl,
        text: String? = null,
        failed: Boolean = false,
        cancelled: Boolean = false,
        error: String? = null,
    ) {
        if (!control.lifecycle.terminal()) return
        control.tokenFlush?.cancel(false)
        control.tokenFlush = null
        if (control.cancelled.get()) {
            synchronized(control.tokenLock) { control.pendingTokens.setLength(0) }
        } else {
            do {
                flushTokens(control, allowReschedule = false)
            } while (synchronized(control.tokenLock) { control.pendingTokens.isNotEmpty() })
        }
        control.terminalSent.set(true)
        synchronized(control.tokenLock) { control.pendingTokens.setLength(0) }
        val finalText = text ?: controlReplyText.remove(control.requestId)?.toString().orEmpty()
        sendReply(control.reply, REPLY_GENERATE, Bundle().apply {
            putString(KEY_REQUEST_ID, control.requestId)
            putLong(KEY_CLIENT_GENERATION, control.clientGeneration)
            putString(KEY_TEXT, finalText)
            putBoolean(KEY_FAILED, failed)
            putBoolean(KEY_CANCELLED, cancelled)
            if (error != null) putString(KEY_ERROR, error)
        })
    }

    private fun sendOneShotFailure(control: OneShotControl, command: Int, error: String) {
        if (!control.terminalSent.compareAndSet(false, true)) return
        val reply = control.reply ?: return
        val data = Bundle().apply {
            putString(KEY_REQUEST_ID, control.requestId)
            putLong(KEY_CLIENT_GENERATION, control.clientGeneration)
            putBoolean(KEY_FAILED, true)
            putString(KEY_ERROR, error)
            if (command == CMD_COMPARE) putDouble(KEY_SCORE, Double.NaN)
        }
        sendReply(reply, when (command) {
            CMD_RERANK -> REPLY_RERANK
            CMD_DIAGNOSTIC -> REPLY_DIAGNOSTIC
            CMD_ISOLATED_EMBEDDING_DIAGNOSTIC -> REPLY_ISOLATED_EMBEDDING_DIAGNOSTIC
            else -> REPLY_COMPARE
        }, data)
    }

    private fun cancelGeneration(requestId: String) {
        if (requestId.isBlank()) return
        val control = requests[requestId]
        if (control != null) {
            control.cancel()
        } else {
            oneShotRequests[requestId]?.cancelled?.set(true)
        }
        if (control == null) return
        // LiteRT-LM documents that CancelProcess() can leave a native session unusable, and
        // current upstream reports show that a pathological cancellation can delay session
        // release. Never let that wedge the single-flight barrier indefinitely: the isolated
        // process is disposable, so kill it after a short cancellation grace period.
        scope.launch {
            delay(CANCEL_GRACE_MS)
            if (!control.nativeReleased.get() && requests[requestId] === control) {
                BenNeuralTelemetry.error("Cancellation grace exceeded; terminating isolated inference process")
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun startOneShot(message: Message) {
        val requestId = message.data.getString(KEY_REQUEST_ID).orEmpty()
        val reply = message.replyTo
        if (reply == null) {
            BenNeuralTelemetry.error("IPC one-shot rejected: missing reply Messenger; command=${message.what}")
            return
        }
        if (requestId.isBlank()) {
            sendCommandFailure(message, "IPC_MISSING_REQUEST_ID")
            return
        }
        if (shuttingDown.get()) {
            sendCommandFailure(message, "INFERENCE_SERVICE_SHUTTING_DOWN")
            return
        }
        val control = OneShotControl(
            requestId,
            message.data.getLong(KEY_CLIENT_GENERATION),
            reply,
            survivesWorkerUnbind = message.what == CMD_ISOLATED_EMBEDDING_DIAGNOSTIC
        )
        oneShotRequests[requestId] = control
        // Start a watchdog on a dedicated scheduler BEFORE any foreground admission or coroutine
        // dispatch. The previous watchdog shared Dispatchers.IO and was created only after
        // beginForegroundWork(), leaving the exact "accepted, then nothing for 150 s" blind spot.
        // This watchdog therefore remains independent of native work, the coroutine dispatcher,
        // and the single-flight mutex.
        val watchdogDelayMs = if (message.what == CMD_DIAGNOSTIC || message.what == CMD_ISOLATED_EMBEDDING_DIAGNOSTIC) DIAGNOSTIC_PREKILL_MS else ONE_SHOT_HARD_TIMEOUT_MS
        control.watchdog = ipcWatchdog.schedule({
            if (oneShotRequests[requestId] === control && !control.terminalSent.get()) {
                val phase = control.phase.get()
                if (message.what == CMD_DIAGNOSTIC || message.what == CMD_ISOLATED_EMBEDDING_DIAGNOSTIC) {
                    sendOneShotFailure(control, message.what, "SERVER_DIAGNOSTIC_TIMEOUT_STAGE=$phase")
                    BenNeuralTelemetry.error("Independent IPC watchdog fired; diagnostic stage=$phase")
                } else {
                    sendOneShotFailure(control, message.what, "SERVER_ONE_SHOT_TIMEOUT_STAGE=$phase")
                }
                // Give the terminal Binder transaction a short delivery window, then kill the
                // disposable process. Never let a wedged FGS/native admission consume the client's
                // full 150 s timeout without a server-side explanation.
                ipcWatchdog.schedule({
                    if (oneShotRequests[requestId] === control) {
                        val finalPhase = control.phase.get()
                        BenNeuralTelemetry.error("IPC watchdog terminal grace exceeded; killing isolated process; stage=$finalPhase")
                        Process.killProcess(Process.myPid())
                    }
                }, if (message.what == CMD_DIAGNOSTIC || message.what == CMD_ISOLATED_EMBEDDING_DIAGNOSTIC) DIAGNOSTIC_KILL_GRACE_MS else CANCEL_GRACE_MS, TimeUnit.MILLISECONDS)
            }
        }, watchdogDelayMs, TimeUnit.MILLISECONDS)

        if (message.what == CMD_ISOLATED_EMBEDDING_DIAGNOSTIC) {
            // Controlled real-model diagnostic: keep transport/FGS proven, but execute the
            // existing EmbeddingGemma diagnostic entirely inside the worker process. Every
            // boundary is journaled before the operation that may fail, so a native/runtime
            // process death still leaves a useful last-known stage.
            control.phase.set("accepted")
            BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_DIAGNOSTIC_ACCEPTED", "request=${control.requestId.take(8)}")
            sendOneShotProgress(control, "Real EmbeddingGemma diagnostic accepted • model/runtime path active")
            control.job = diagnosticScope.launch {
                try {
                    control.phase.set("fgs_work")
                    BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_FGS_WORK_BEGIN")
                    beginForegroundWork()
                    BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_FGS_WORK_READY")
                    singleFlight.withLock {
                        if (control.cancelled.get()) return@withLock
                        val thermal = if (Build.VERSION.SDK_INT >= 29) powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE else PowerManager.THERMAL_STATUS_NONE
                        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
                            val reason = "EMBEDDING_DIAGNOSTIC_THERMAL_BLOCK_$thermal"
                            BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_BLOCKED", reason)
                            sendOneShotFailure(control, message.what, reason)
                            return@withLock
                        }
                        control.phase.set("engine_prepare")
                        BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_ENGINE_PREPARE")
                        val engine = BenEmbeddingGemmaEngine(applicationContext)
                        control.phase.set("engine_diagnostic")
                        BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_ENGINE_BEGIN")
                        sendOneShotProgress(control, "Initializing EmbeddingGemma runtime • this is the real isolated model test")
                        val report = kotlinx.coroutines.withContext(NonCancellable) {
                            engine.diagnosticReport { progress ->
                            control.phase.set("progress:${progress.take(70)}")
                            BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_PROGRESS", progress)
                            sendOneShotProgress(control, progress)
                            }.toText()
                        }
                        if (control.cancelled.get()) return@withLock
                        control.phase.set("engine_complete")
                        BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_ENGINE_COMPLETE", "reportChars=${report.length}")
                        if (!control.terminalSent.compareAndSet(false, true)) return@withLock
                        sendReply(control.reply, REPLY_ISOLATED_EMBEDDING_DIAGNOSTIC, Bundle().apply {
                            putString(KEY_REQUEST_ID, control.requestId)
                            putLong(KEY_CLIENT_GENERATION, control.clientGeneration)
                            putBoolean(KEY_FAILED, false)
                            putString(KEY_REPORT, report)
                        })
                        BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_REPLY_PASS")
                        control.phase.set("complete")
                    }
                } catch (error: CancellationException) {
                    val reason = "EMBEDDING_DIAGNOSTIC_CANCELLATION:${error.javaClass.simpleName}:${error.message?.take(240) ?: "cancelled"}"
                    BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_CANCELLATION", reason)
                    sendOneShotFailure(control, message.what, reason)
                } catch (error: LinkageError) {
                    val reason = "EMBEDDING_DIAGNOSTIC_LINKAGE:${error.javaClass.simpleName}:${error.message?.take(240) ?: "no_message"}"
                    BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_FATAL_LINKAGE", reason)
                    sendOneShotFailure(control, message.what, reason)
                } catch (error: Exception) {
                    val reason = "EMBEDDING_DIAGNOSTIC_EXCEPTION:${error.javaClass.simpleName}:${error.message?.take(240) ?: "no_message"}"
                    BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_EMBEDDING_EXCEPTION", reason)
                    sendOneShotFailure(control, message.what, reason)
                } finally {
                    control.watchdog?.cancel(false)
                    control.watchdog = null
                    oneShotRequests.remove(control.requestId, control)
                    endForegroundWork()
                }
            }
            return
        }

        if (message.what == CMD_DIAGNOSTIC) {
            // The IPC diagnostic is deliberately model-free. Its job is to prove the
            // FGS/Binder/process round-trip, not to initialize EmbeddingGemma. Keep this
            // first diagnostic response on the Binder handler thread so a coroutine-scope
            // or model-runtime failure cannot masquerade as an IPC failure.
            control.phase.set("accepted")
            BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_DIAGNOSTIC_ACCEPTED", "request=${control.requestId.take(8)}")
            BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_DIAGNOSTIC_HANDLER_ENTER")
            sendOneShotProgress(control, "Remote IPC diagnostic accepted; transport-only path active")
            control.phase.set("transport_reply")
            BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_DIAGNOSTIC_TRANSPORT_REPLY_BEGIN")
            val diagnosticReport = buildTransportDiagnosticReport(control)
            if (control.terminalSent.compareAndSet(false, true)) {
                sendReply(control.reply, REPLY_DIAGNOSTIC, Bundle().apply {
                    putString(KEY_REQUEST_ID, control.requestId)
                    putLong(KEY_CLIENT_GENERATION, control.clientGeneration)
                    putBoolean(KEY_FAILED, false)
                    putString(KEY_REPORT, diagnosticReport)
                })
                BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_DIAGNOSTIC_TRANSPORT_REPLY_PASS")
                control.phase.set("complete")
            }
            control.watchdog?.cancel(false)
            control.watchdog = null
            oneShotRequests.remove(control.requestId, control)
            return
        }
        control.job = scope.launch {
            try {
                if (message.what == CMD_DIAGNOSTIC) {
                    control.phase.set("worker_admitted")
                    BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_FGS_GOVERNOR_CONFIRMED")
                    sendOneShotProgress(control, "IPC accepted • main-process foreground governor active")
                }
                beginForegroundWork()
                if (message.what == CMD_DIAGNOSTIC) {
                    control.phase.set("diagnostic_engine")
                    BenIpcDiagnosticRecorder.event(this@BenInferenceProcessService, null, "WORKER_DIAGNOSTIC_ENGINE_BEGIN")
                    sendOneShotProgress(control, "Foreground governor active • entering diagnostic engine")
                }
                singleFlight.withLock {
                    if (control.cancelled.get()) return@withLock
                    if (Build.VERSION.SDK_INT >= 29 && (powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) >= PowerManager.THERMAL_STATUS_SEVERE) {
                        sendOneShotFailure(control, message.what, "Thermal safety gate denied neural operation")
                        return@withLock
                    }
                    if (message.what == CMD_DIAGNOSTIC) {
                        control.phase.set("admission")
                        sendOneShotProgress(control, "Starting full EmbeddingGemma contract + NPU test")
                    }
                    handleOneShot(message, control)
                }
            } finally {
                control.watchdog?.cancel(false)
                control.watchdog = null
                oneShotRequests.remove(requestId, control)
                endForegroundWork()
            }
        }
    }

    private suspend fun handleOneShot(message: Message, control: OneShotControl) {
        val reply = control.reply
        if (control.cancelled.get()) return
        val result = when (message.what) {
            CMD_RERANK -> runCatching {
                if (embedding == null) embedding = BenEmbeddingGemmaEngine(applicationContext)
                val query = benIpcGetText(message.data, KEY_QUERY)
                val candidates = message.data.getStringArrayList(KEY_CANDIDATES) ?: arrayListOf()
                val limit = message.data.getInt(KEY_LIMIT, 8)
                embedding!!.rerank(query, candidates.mapIndexed { index, text -> Candidate(index, text) }, { it.text }, limit.coerceIn(1, 8))
            }.getOrNull()?.let { r -> Bundle().apply {
                putIntArray(KEY_INDICES, r.hits.map { it.item.index }.toIntArray())
                putDoubleArray(KEY_SCORES, r.hits.map { it.similarity }.toDoubleArray())
                putLong(KEY_ELAPSED, r.elapsedMs)
                putBoolean(KEY_USED_MODEL, r.usedModel)
                putBoolean(KEY_FAILED, false)
            }} ?: Bundle().apply { putBoolean(KEY_FAILED, true) }
            CMD_DIAGNOSTIC -> runCatching {
                control.phase.set("engine_start")
                sendOneShotProgress(control, "Initializing EmbeddingGemma runtime • first run may take around 20 seconds")
                if (embedding == null) embedding = BenEmbeddingGemmaEngine(applicationContext)
                control.phase.set("diagnostic")
                embedding!!.diagnostic { progress ->
                    control.phase.set(progress.take(80))
                    sendOneShotProgress(control, progress)
                }
            }.getOrNull()?.let { Bundle().apply { putString(KEY_REPORT, it); putBoolean(KEY_FAILED, false) } }
                ?: Bundle().apply { putBoolean(KEY_FAILED, true) }
            CMD_COMPARE -> runCatching {
                if (embedding == null) embedding = BenEmbeddingGemmaEngine(applicationContext)
                embedding!!.compare(benIpcGetText(message.data, KEY_FIRST), benIpcGetText(message.data, KEY_SECOND))
            }.getOrNull()?.let { Bundle().apply { putDouble(KEY_SCORE, it); putBoolean(KEY_FAILED, false) } }
                ?: Bundle().apply { putDouble(KEY_SCORE, Double.NaN); putBoolean(KEY_FAILED, true) }
            else -> Bundle().apply { putBoolean(KEY_FAILED, true) }
        }
        if (control.cancelled.get()) return
        if (message.what == CMD_DIAGNOSTIC) {
            control.phase.set("complete")
            BenIpcDiagnosticRecorder.event(this, null, "WORKER_DIAGNOSTIC_COMPLETE")
            sendOneShotProgress(control, "Diagnostic complete • publishing report")
        }
        if (!control.terminalSent.compareAndSet(false, true)) return
        result.putString(KEY_REQUEST_ID, message.data.getString(KEY_REQUEST_ID).orEmpty())
        result.putLong(KEY_CLIENT_GENERATION, message.data.getLong(KEY_CLIENT_GENERATION))
        sendReply(reply, when (message.what) {
            CMD_RERANK -> REPLY_RERANK
            CMD_DIAGNOSTIC -> REPLY_DIAGNOSTIC
            CMD_ISOLATED_EMBEDDING_DIAGNOSTIC -> REPLY_ISOLATED_EMBEDDING_DIAGNOSTIC
            else -> REPLY_COMPARE
        }, result)
    }

    private data class Candidate(val index: Int, val text: String)

    private fun buildTransportDiagnosticReport(control: OneShotControl): String = buildString {
        appendLine("BEN IPC TRANSPORT DIAGNOSTIC")
        appendLine("============================")
        appendLine("modelsRequired=false")
        appendLine("modelsLoaded=false")
        appendLine("workerPid=${android.os.Process.myPid()}")
        appendLine("requestId=${control.requestId}")
        appendLine("clientGeneration=${control.clientGeneration}")
        appendLine("transport=Binder Messenger")
        appendLine("result=PASS")
        appendLine("stage=WORKER_TRANSPORT_REPLY")
        appendLine("This diagnostic intentionally performs no neural/model initialization.")
    }.take(4000)

    private fun sendOneShotProgress(control: OneShotControl, progress: String) {
        val reply = control.reply ?: return
        sendReply(reply, REPLY_DIAGNOSTIC_PROGRESS, Bundle().apply {
            putString(KEY_REQUEST_ID, control.requestId)
            putLong(KEY_CLIENT_GENERATION, control.clientGeneration)
            putString(KEY_PROGRESS, progress.take(220))
        })
    }

    private fun sendCommandFailure(message: Message, error: String) {
        val replyCode = when (message.what) {
            CMD_PING -> REPLY_PING
            CMD_RERANK -> REPLY_RERANK
            CMD_DIAGNOSTIC -> REPLY_DIAGNOSTIC
            CMD_ISOLATED_EMBEDDING_DIAGNOSTIC -> REPLY_ISOLATED_EMBEDDING_DIAGNOSTIC
            CMD_COMPARE -> REPLY_COMPARE
            else -> REPLY_GENERATE
        }
        sendReply(message.replyTo, replyCode, Bundle().apply {
            putString(KEY_REQUEST_ID, message.data.getString(KEY_REQUEST_ID).orEmpty())
            putLong(KEY_CLIENT_GENERATION, message.data.getLong(KEY_CLIENT_GENERATION))
            putBoolean(KEY_FAILED, true)
            putBoolean(KEY_OK, false)
            putString(KEY_ERROR, error)
            if (message.what == CMD_COMPARE) putDouble(KEY_SCORE, Double.NaN)
        })
    }

    private fun sendReply(target: Messenger?, what: Int, data: Bundle) {
        if (target == null) {
            BenNeuralTelemetry.error("IPC reply dropped: null reply Messenger; what=$what")
            return
        }
        val outbound = Bundle(data)
        val sharedResources = mutableListOf<android.os.SharedMemory>()
        listOf(KEY_TEXT, KEY_REPORT, KEY_ERROR).forEach { key ->
            val text = outbound.getString(key) ?: return@forEach
            runCatching { benIpcPutText(outbound, key, text) }
                .onSuccess { memory -> if (memory != null) sharedResources += memory }
                .onFailure { error ->
                    BenNeuralTelemetry.error("IPC shared-memory reply preparation failed: key=$key ${error.javaClass.simpleName}: ${error.message ?: "unknown"}")
                }
        }
        try {
            target.send(Message.obtain(null, what).apply { this.data = outbound })
        } catch (error: Exception) {
            BenNeuralTelemetry.error(
                "IPC reply delivery failed: what=$what ${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
            )
        } finally {
            sharedResources.forEach(::benIpcCloseSharedMemory)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This worker is deliberately NOT a foreground-service host. The main process owns
        // BenInferenceForegroundService; this process is protected by the active binding and
        // bounded wake lock while native work is running.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        lifecycleHandler.removeCallbacks(warmTrimRunnable)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        BenIpcDiagnosticRecorder.event(this, null, "WORKER_ON_UNBIND", "pid=${android.os.Process.myPid()} oneShots=${oneShotRequests.size}")
        requests.values.forEach { it.cancel() }
        oneShotRequests.values.forEach {
            if (!it.survivesWorkerUnbind) it.cancelled.set(true)
        }
        // Keep a successfully initialized runtime warm for a short bounded window. This avoids
        // paying EmbeddingGemma's ~20 s cold initialization for every isolated request, while
        // still trimming deterministically when the process becomes idle.
        lifecycleHandler.removeCallbacks(warmTrimRunnable)
        lifecycleHandler.postDelayed(warmTrimRunnable, WARM_RUNTIME_TTL_MS)
        return false
    }

    override fun onDestroy() {
        BenIpcDiagnosticRecorder.event(this, null, "WORKER_ON_DESTROY", "pid=${android.os.Process.myPid()} phase=${oneShotRequests.values.firstOrNull()?.phase?.get() ?: "none"}")
        shuttingDown.set(true)
        lifecycleHandler.removeCallbacks(warmTrimRunnable)
        if (Build.VERSION.SDK_INT >= 29) runCatching { thermalListener?.let { powerManager?.removeThermalStatusListener(it) } }
        requests.values.forEach { it.cancel() }
        oneShotRequests.values.forEach { it.cancelled.set(true) }
        // Do not synchronously close native runtimes from Service.onDestroy(). onDestroy() can
        // run while the service coroutine is still returning from native inference; direct close
        // here would reintroduce the exact concurrent-close race this barrier prevents. The
        // isolated process is disposable, so OS process teardown is the final cleanup boundary.
        scope.coroutineContext[Job]?.cancel()
        diagnosticScope.coroutineContext[Job]?.cancel()
        ipcWatchdog.shutdownNow()
        handlerThread.quitSafely()
        // Defensive: the coroutine finally blocks normally pair every beginForegroundWork()
        // with endForegroundWork(), but if the service is torn down mid-request, make sure
        // the wake lock does not outlive the process.
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            scope.launch { singleFlight.withLock { closeRuntimes() } }
        }
    }

    private fun bundleSizeBytes(bundle: Bundle): Int {
        return runCatching {
            val parcel = Parcel.obtain()
            return try {
                bundle.writeToParcel(parcel, 0)
                parcel.dataSize()
            } finally {
                parcel.recycle()
            }
        }.getOrDefault(Int.MAX_VALUE)
    }

    private fun closeRuntimes() {
        runCatching { generator?.close() }
        generator = null
        generatorWarm = false
        runCatching { embedding?.close() }
        embedding = null
        embeddingWarm = false
    }

    companion object {
        const val CMD_GENERATE = 1
        const val CMD_RERANK = 2
        const val CMD_PING = 3
        const val CMD_DIAGNOSTIC = 4
        const val CMD_COMPARE = 5
        const val CMD_CANCEL = 6
        const val CMD_ISOLATED_EMBEDDING_DIAGNOSTIC = 7
        const val REPLY_GENERATE = 101
        const val REPLY_RERANK = 102
        const val REPLY_DIAGNOSTIC = 104
        const val REPLY_COMPARE = 105
        const val REPLY_PING = 103
        const val REPLY_TOKEN = 106
        const val REPLY_DIAGNOSTIC_PROGRESS = 107
        const val REPLY_ISOLATED_EMBEDDING_DIAGNOSTIC = 108
        const val KEY_REQUEST_ID = "requestId"
        const val KEY_CLIENT_GENERATION = "clientGeneration"
        const val KEY_PROMPT = "prompt"
        const val KEY_MAX_TOKENS = "maxTokens"
        const val KEY_QUERY = "query"
        const val KEY_CANDIDATES = "candidates"
        const val KEY_LIMIT = "limit"
        const val KEY_TEXT = "text"
        const val KEY_TOKEN = "token"
        const val KEY_INDICES = "indices"
        const val KEY_SCORES = "scores"
        const val KEY_ELAPSED = "elapsedMs"
        const val KEY_USED_MODEL = "usedModel"
        const val KEY_FAILED = "failed"
        const val KEY_CANCELLED = "cancelled"
        const val KEY_ERROR = "error"
        const val KEY_OK = "ok"
        const val KEY_REPORT = "report"
        const val KEY_PROGRESS = "progress"
        const val KEY_FIRST = "first"
        const val KEY_SECOND = "second"
        const val KEY_SCORE = "score"
        private const val CANCEL_GRACE_MS = 2_500L
        private const val GENERATION_HARD_TIMEOUT_MS = 45_000L
        private const val ONE_SHOT_HARD_TIMEOUT_MS = 30_000L
        private const val DIAGNOSTIC_PREKILL_MS = 100_000L
        private const val DIAGNOSTIC_HARD_TIMEOUT_MS = 120_000L
        private const val DIAGNOSTIC_KILL_GRACE_MS = 2_500L
        // Slightly longer than the longest hard deadline in this file (DIAGNOSTIC_HARD_TIMEOUT_MS)
        // so the lock cannot expire out from under a legitimately still-running diagnostic; every
        // acquire() is still paired with a release() in endForegroundWork()/onDestroy(), this is
        // only a backstop against a leaked wake lock outliving its request.
        private const val WAKE_LOCK_TIMEOUT_MS = 130_000L
        // Evidence-driven warm retention: the verified SM8650 cold init is ~20 s while warm
        // inference is ~100 ms. Keep the native runtime for 120 s after unbind, then trim.
        private const val WARM_RUNTIME_TTL_MS = 120_000L
        private const val MAX_IPC_PAYLOAD_BYTES = 128 * 1024
        private const val TOKEN_BATCH_WINDOW_MS = 50L
        private const val TOKEN_BATCH_MAX_CHARS = 8 * 1024
    }
}
