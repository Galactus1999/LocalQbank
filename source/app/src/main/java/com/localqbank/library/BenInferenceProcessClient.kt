package com.localqbank.library

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Parcel
import android.os.Messenger
import android.util.Log
import android.os.RemoteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Main-process IPC facade for Ben's isolated neural runtime.
 *
 * v8.3.145 hardening:
 * - Binder DeathRecipient is attached to every service generation.
 * - Generation IDs prevent stale callbacks after a rebind.
 * - Generation is exposed as a cancellable Flow, so UI/lifecycle cancellation propagates
 *   back across Binder instead of merely abandoning a remote request.
 * - Exactly one terminal event is accepted for each request.
 */
class BenInferenceProcessClient(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val callbackThread = HandlerThread("ben-inference-callback").also { it.start() }
    private val callbackHandler = Handler(callbackThread.looper)
    private val connectionMutex = Mutex()
    private val lifecycle = BenIpcLifecycleState()
    private val requests = ConcurrentHashMap<String, RequestState>()
    private val oneShots = ConcurrentHashMap<String, OneShotState<*>>()
    private val boundConnections = ConcurrentHashMap<ServiceConnection, AtomicBoolean>()
    private val generationCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private val requestTimeoutHandler = Handler(callbackThread.looper)
    /** Android 14+ can dispatch ServiceConnection callbacks on this executor instead of the main looper. */
    private val serviceConnectionExecutor = Executor { command -> callbackHandler.post(command) }
    @Volatile private var remote: Messenger? = null
    @Volatile private var serviceBinder: IBinder? = null
    private val fgsConnectionMutex = Mutex()
    private val fgsCallbackThread = HandlerThread("ben-fgs-callback").also { it.start() }
    private val fgsCallbackHandler = Handler(fgsCallbackThread.looper)
    private val fgsServiceConnectionExecutor = Executor { command -> fgsCallbackHandler.post(command) }
    private val fgsBoundConnections = ConcurrentHashMap<ServiceConnection, AtomicBoolean>()
    @Volatile private var fgsRemote: Messenger? = null
    @Volatile private var fgsBinder: IBinder? = null
    @Volatile private var fgsConnection: ServiceConnection? = null
    @Volatile private var fgsLeaseId: String? = null
    @Volatile private var closed = false
    @Volatile private var warm = false
    @Volatile private var currentServiceGeneration = 0L
    private var connection: ServiceConnection? = null
    private var deathRecipient: IBinder.DeathRecipient? = null
    private var pendingBindConnection: ServiceConnection? = null
    private var pendingBindGeneration: Long = 0L
    private var pendingBindContinuation: CancellableContinuation<Messenger>? = null

    sealed interface GenerationEvent {
        data class Token(val text: String) : GenerationEvent
        data class Complete(val text: String) : GenerationEvent
        data class Failed(val reason: String) : GenerationEvent
        data object Cancelled : GenerationEvent
        data object ProcessDied : GenerationEvent
    }

    private class RequestState(
        val id: String,
        val generation: Long,
        val sink: kotlinx.coroutines.channels.SendChannel<GenerationEvent>,
        val terminal: AtomicBoolean = AtomicBoolean(false),
        @Volatile var watchdog: Runnable? = null,
    )

    private class OneShotState<T>(
        val requestId: String,
        val generation: Long,
        val done: AtomicBoolean,
        val continuation: CancellableContinuation<T?>,
        val failure: ((String) -> Unit)? = null,
        @Volatile var timeout: Runnable? = null,
    )

    /** Streaming generation bridge. Collector cancellation sends CMD_CANCEL to the isolated process. */
    fun generateStream(prompt: String, maxTokens: Int = 160): Flow<GenerationEvent> = callbackFlow<GenerationEvent> {
        val fgsFailure = acquireForegroundLease()
        if (fgsFailure != null) {
            trySend(GenerationEvent.Failed(fgsFailure))
            close()
            return@callbackFlow
        }
        val target = try {
            ensureBoundWithRetry(requestForeground = true)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            trySend(GenerationEvent.Failed("IPC_BIND_FAILED: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"))
            close()
            return@callbackFlow
        }
        val requestId = UUID.randomUUID().toString()
        val generation = currentServiceGeneration
        val state = RequestState(requestId, generation, channel)
        requests[requestId] = state

        val outboundData = Bundle().apply {
            putString(BenInferenceProcessService.KEY_REQUEST_ID, requestId)
            putLong(BenInferenceProcessService.KEY_CLIENT_GENERATION, generation)
            benIpcPutText(this, BenInferenceProcessService.KEY_PROMPT, prompt.take(200_000))
            putInt(BenInferenceProcessService.KEY_MAX_TOKENS, maxTokens.coerceIn(32, 192))
        }
        val promptSharedMemory = if (Build.VERSION.SDK_INT >= 27 && benIpcHasSharedText(outboundData, BenInferenceProcessService.KEY_PROMPT)) {
            @Suppress("DEPRECATION")
            outboundData.getParcelable(BenInferenceProcessService.KEY_PROMPT + ".shm") as? android.os.SharedMemory
        } else null
        val sent = try {
            target.send(Message.obtain(null, BenInferenceProcessService.CMD_GENERATE).apply {
                replyTo = Messenger(callbackHandlerFor(requestId))
                data = outboundData
            })
            true
        } catch (_: Exception) {
            false
        } finally {
            benIpcCloseSharedMemory(promptSharedMemory)
        }

        if (!sent) {
            terminal(state, GenerationEvent.ProcessDied)
            return@callbackFlow
        }
        val watchdog = Runnable {
            if (!state.terminal.get()) {
                terminal(state, GenerationEvent.Failed("Inference request timed out"))
                sendCancelBestEffort(target, requestId, generation)
            }
        }
        state.watchdog = watchdog
        requestTimeoutHandler.postDelayed(watchdog, GENERATION_CLIENT_TIMEOUT_MS)

        awaitClose {
            requests.remove(requestId)
            if (state.terminal.compareAndSet(false, true)) {
                sendCancelBestEffort(target, requestId, generation)
            }
        }
    }

    /** Compatibility one-shot API. Callers that own a lifecycle should prefer generateStream(). */
    suspend fun generateSuspend(prompt: String, maxTokens: Int = 160): String? {
        var output: String? = null
        generateStream(prompt, maxTokens).collect { event ->
            when (event) {
                is GenerationEvent.Token -> Unit
                is GenerationEvent.Complete -> output = event.text
                is GenerationEvent.Failed -> Unit
                GenerationEvent.Cancelled -> Unit
                GenerationEvent.ProcessDied -> Unit
            }
        }
        return output
    }

    data class RerankResult(val indices: IntArray, val scores: DoubleArray, val elapsedMs: Long, val usedModel: Boolean)

    suspend fun rerankSuspend(query: String, candidates: List<String>, limit: Int = 8): RerankResult? =
        oneShot(BenInferenceProcessService.CMD_RERANK, Bundle().apply {
            putString(BenInferenceProcessService.KEY_QUERY, query.take(1_800))
            putStringArrayList(BenInferenceProcessService.KEY_CANDIDATES, ArrayList(candidates.take(8).map { it.take(1_800) }))
            putInt(BenInferenceProcessService.KEY_LIMIT, limit.coerceIn(1, 8))
        }) { message ->
            if (message.data.getBoolean(BenInferenceProcessService.KEY_FAILED, false)) null else RerankResult(
                message.data.getIntArray(BenInferenceProcessService.KEY_INDICES) ?: IntArray(0),
                message.data.getDoubleArray(BenInferenceProcessService.KEY_SCORES) ?: DoubleArray(0),
                message.data.getLong(BenInferenceProcessService.KEY_ELAPSED, 0L),
                message.data.getBoolean(BenInferenceProcessService.KEY_USED_MODEL, false)
            )
        }

    data class DiagnosticResult(val report: String?, val failure: String? = null)

    data class IpcPingResult(val ok: Boolean, val failure: String? = null)

    /** Fast Binder round-trip probe. This intentionally performs no model/native work. */
    suspend fun pingSuspend(timeoutMs: Long = 5_000L): IpcPingResult {
        var failure: String? = null
        val ok = oneShot(
            BenInferenceProcessService.CMD_PING,
            Bundle(),
            timeoutMs = timeoutMs,
            onFailure = { failure = it },
        ) { message ->
            if (message.what != BenInferenceProcessService.REPLY_PING) {
                failure = "IPC_PING_WRONG_REPLY_${message.what}"
                null
            } else if (message.data.getBoolean(BenInferenceProcessService.KEY_FAILED, false)) {
                failure = readIpcText(message.data, BenInferenceProcessService.KEY_ERROR).ifBlank { "IPC_PING_FAILED" }
                null
            } else {
                message.data.getBoolean(BenInferenceProcessService.KEY_OK, false)
            }
        } ?: false
        return IpcPingResult(ok, failure)
    }

    suspend fun diagnosticSuspend(onProgress: ((String) -> Unit)? = null): DiagnosticResult {
        var failure: String? = null
        val report = oneShot(
            BenInferenceProcessService.CMD_DIAGNOSTIC,
            Bundle(),
            timeoutMs = DIAGNOSTIC_CLIENT_TIMEOUT_MS,
            onProgress = onProgress,
            onFailure = { failure = it },
        ) { m ->
            if (m.data.getBoolean(BenInferenceProcessService.KEY_FAILED, false)) {
                failure = readIpcText(m.data, BenInferenceProcessService.KEY_ERROR).ifBlank { "REMOTE_DIAGNOSTIC_FAILED" }
                null
            } else {
                val remoteReport = readIpcText(m.data, BenInferenceProcessService.KEY_REPORT)
                if (remoteReport.isBlank()) {
                    failure = "REMOTE_DIAGNOSTIC_EMPTY_REPORT"
                    null
                } else {
                    BenIpcDiagnosticRecorder.event(app, null, "CLIENT_REMOTE_DIAGNOSTIC_REPLY_PASS")
                    remoteReport
                }
            }
        }
        return DiagnosticResult(report, failure)
    }

    /**
     * Real isolated-process EmbeddingGemma diagnostic. Unlike CMD_DIAGNOSTIC (transport-only),
     * this deliberately enters the existing model/runtime path. It is single-attempt so a native
     * failure cannot be obscured by an automatic retry with a fresh process.
     */
    suspend fun isolatedEmbeddingDiagnosticSuspend(onProgress: ((String) -> Unit)? = null): DiagnosticResult {
        var failure: String? = null
        // Keep the worker service STARTED for the duration of this diagnostic. A bound-only
        // worker can receive onUnbind() during lifecycle/connection churn; onUnbind cancels
        // active work. The diagnostic is intentionally process-lifetime-owned, so binding alone
        // must not be its only lifetime reference. The main-process FGS lease is acquired by
        // oneShot() before the worker is started.
        try {
            app.startService(Intent(app, BenInferenceProcessService::class.java).apply {
                action = "com.localqbank.library.action.RUN_EMBEDDING_DIAGNOSTIC"
            })
            BenIpcDiagnosticRecorder.event(app, null, "CLIENT_WORKER_START_PASS")
        } catch (error: Exception) {
            failure = "ISOLATED_EMBEDDING_WORKER_START_FAILED:${error.javaClass.simpleName}:${error.message ?: "unknown"}".take(500)
            BenIpcDiagnosticRecorder.event(app, null, "CLIENT_WORKER_START_FAIL", failure!!)
            return DiagnosticResult(null, failure)
        }
        val report = oneShot(
            BenInferenceProcessService.CMD_ISOLATED_EMBEDDING_DIAGNOSTIC,
            Bundle(),
            timeoutMs = ISOLATED_EMBEDDING_DIAGNOSTIC_TIMEOUT_MS,
            onProgress = onProgress,
            onFailure = { failure = it },
            maxAttempts = 1,
        ) { message ->
            BenIpcDiagnosticRecorder.event(app, null, "CLIENT_EMBEDDING_REPLY_RECEIVED", "what=${message.what}")
            BenIpcDiagnosticRecorder.event(app, null, "CLIENT_EMBEDDING_REPLY_DECODE_BEGIN")
            if (message.what != BenInferenceProcessService.REPLY_ISOLATED_EMBEDDING_DIAGNOSTIC) {
                failure = "ISOLATED_EMBEDDING_WRONG_REPLY_${message.what}"
                null
            } else if (message.data.getBoolean(BenInferenceProcessService.KEY_FAILED, false)) {
                failure = readIpcText(message.data, BenInferenceProcessService.KEY_ERROR).ifBlank { "ISOLATED_EMBEDDING_FAILED" }
                null
            } else {
                val text = readIpcText(message.data, BenInferenceProcessService.KEY_REPORT)
                if (text.isBlank()) {
                    failure = "ISOLATED_EMBEDDING_EMPTY_REPORT"
                    BenIpcDiagnosticRecorder.event(app, null, "CLIENT_EMBEDDING_REPLY_DECODE_FAIL", failure!!)
                    null
                } else {
                    BenIpcDiagnosticRecorder.event(app, null, "CLIENT_EMBEDDING_REPLY_DECODE_PASS", "chars=${text.length}")
                    BenIpcDiagnosticRecorder.event(app, null, "CLIENT_EMBEDDING_REPLY_PASS")
                    text
                }
            }
        }
        return DiagnosticResult(report, failure)
    }

    suspend fun compareSuspend(first: String, second: String): Double? = oneShot(BenInferenceProcessService.CMD_COMPARE, Bundle().apply {
        putString(BenInferenceProcessService.KEY_FIRST, first.take(1_800))
        putString(BenInferenceProcessService.KEY_SECOND, second.take(1_800))
    }) { m ->
        if (m.data.getBoolean(BenInferenceProcessService.KEY_FAILED, false)) null
        else m.data.getDouble(BenInferenceProcessService.KEY_SCORE, Double.NaN).takeUnless { it.isNaN() }
    }

    private fun readIpcText(bundle: Bundle, key: String): String =
        runCatching { benIpcGetText(bundle, key) }.getOrDefault(
            bundle.getString(key).orEmpty()
        )

    private fun callbackHandlerFor(requestId: String): Handler = Handler(callbackThread.looper) { message ->
        val id = message.data.getString(BenInferenceProcessService.KEY_REQUEST_ID) ?: requestId
        if (id != requestId) return@Handler true
        val state = requests[id] ?: return@Handler true
        if (message.data.getLong(BenInferenceProcessService.KEY_CLIENT_GENERATION, state.generation) != state.generation) return@Handler true
        when (message.what) {
            BenInferenceProcessService.REPLY_TOKEN -> {
                if (!state.terminal.get()) state.sink.trySend(GenerationEvent.Token(readIpcText(message.data, BenInferenceProcessService.KEY_TOKEN)))
            }
            BenInferenceProcessService.REPLY_GENERATE -> {
                if (message.data.getBoolean(BenInferenceProcessService.KEY_CANCELLED, false)) terminal(state, GenerationEvent.Cancelled)
                else if (message.data.getBoolean(BenInferenceProcessService.KEY_FAILED, false)) terminal(state, GenerationEvent.Failed(readIpcText(message.data, BenInferenceProcessService.KEY_ERROR)))
                else terminal(state, GenerationEvent.Complete(readIpcText(message.data, BenInferenceProcessService.KEY_TEXT)))
            }
        }
        true
    }

    private fun terminal(state: RequestState, event: GenerationEvent) {
        if (!state.terminal.compareAndSet(false, true)) return
        requests.remove(state.id)
        state.watchdog?.let(requestTimeoutHandler::removeCallbacks)
        state.watchdog = null
        state.sink.trySend(event)
        state.sink.close()
    }

    private fun sendCancelBestEffort(target: Messenger, requestId: String, generation: Long) {
        runCatching {
            target.send(Message.obtain(null, BenInferenceProcessService.CMD_CANCEL).apply {
                data = Bundle().apply {
                    putString(BenInferenceProcessService.KEY_REQUEST_ID, requestId)
                    putLong(BenInferenceProcessService.KEY_CLIENT_GENERATION, generation)
                }
            })
        }
    }

    /**
     * Foreground ownership is deliberately kept in the main application process.
     * The neural worker is a plain bound service in :inference_process. This avoids making
     * Android's FGS admission, notification lifecycle, and OEM FGS policy depend on the
     * worker process itself.
     */
    private suspend fun acquireForegroundLease(): String? = fgsConnectionMutex.withLock {
        BenIpcDiagnosticRecorder.event(app, null, "CLIENT_FGS_ACQUIRE_BEGIN")
        if (closed) return@withLock "IPC_FGS_CLIENT_CLOSED"
        if (fgsLeaseId != null && fgsRemote?.binder?.isBinderAlive == true) return@withLock null
        releaseForegroundLeaseLocked()
        val startFailure = runCatching {
            app.startForegroundService(Intent(app, BenInferenceForegroundService::class.java).apply {
                action = BenInferenceForegroundService.ACTION_ACQUIRE
            })
            null
        }.getOrElse { error ->
            val code = when (error.javaClass.simpleName) {
                "ForegroundServiceStartNotAllowedException" -> "IPC_FGS_START_NOT_ALLOWED"
                "MissingForegroundServiceTypeException" -> "IPC_FGS_TYPE_MISSING"
                "InvalidForegroundServiceTypeException" -> "IPC_FGS_TYPE_INVALID"
                "SecurityException" -> "IPC_FGS_SECURITY_EXCEPTION"
                else -> "IPC_FGS_START_FAILED"
            }
            "$code: ${error.message ?: "unknown"}".take(500)
        }
        if (startFailure != null) { BenIpcDiagnosticRecorder.event(app, null, "CLIENT_FGS_START_FAIL", startFailure); return@withLock startFailure }
        BenIpcDiagnosticRecorder.event(app, null, "CLIENT_FGS_START_PASS")
        suspendCancellableCoroutine { continuation ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    BenIpcDiagnosticRecorder.event(app, null, "CLIENT_FGS_BIND_CONNECTED", "alive=${binder?.isBinderAlive == true}")
                    if (continuation.context[Job]?.isActive != true || closed) return
                    if (binder == null || !binder.isBinderAlive) {
                        continuation.resume("IPC_FGS_BINDER_DEAD")
                        return
                    }
                    val messenger = Messenger(binder)
                    fgsBinder = binder
                    fgsRemote = messenger
                    fgsConnection = this
                    val leaseId = UUID.randomUUID().toString()
                    fgsLeaseId = leaseId
                    val handler = Handler(fgsCallbackThread.looper) { message ->
                        if (message.data.getString(BenInferenceForegroundService.KEY_LEASE_ID) != leaseId) return@Handler true
                        if (message.what == BenInferenceForegroundService.REPLY_READY) {
                            val ok = message.data.getBoolean(BenInferenceForegroundService.KEY_OK, false)
                            val error = message.data.getString(BenInferenceForegroundService.KEY_ERROR)
                            BenIpcDiagnosticRecorder.event(app, null, if (ok) "CLIENT_FGS_READY_PASS" else "CLIENT_FGS_READY_FAIL", error ?: "ready")
                            if (continuation.isActive) continuation.resume(if (ok) null else error ?: "IPC_FGS_NOT_READY")
                        }
                        true
                    }
                    try {
                        messenger.send(Message.obtain(null, BenInferenceForegroundService.CMD_ACQUIRE).apply {
                            replyTo = Messenger(handler)
                            data = Bundle().apply { putString(BenInferenceForegroundService.KEY_LEASE_ID, leaseId) }
                        })
                    } catch (error: Exception) {
                        releaseForegroundLeaseLocked()
                        if (continuation.isActive) continuation.resume("IPC_FGS_ACQUIRE_SEND_FAILED:${error.javaClass.simpleName}")
                    }
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    if (continuation.isActive) continuation.resume("IPC_FGS_CONNECTION_LOST")
                }
                override fun onBindingDied(name: ComponentName?) {
                    if (continuation.isActive) continuation.resume("IPC_FGS_BINDING_DIED")
                }
                override fun onNullBinding(name: ComponentName?) {
                    if (continuation.isActive) continuation.resume("IPC_FGS_NULL_BINDING")
                }
            }
            fgsConnection = conn
            val marker = AtomicBoolean(true)
            fgsBoundConnections[conn] = marker
            val bound = runCatching {
                if (Build.VERSION.SDK_INT >= 34) app.bindService(Intent(app, BenInferenceForegroundService::class.java), Context.BIND_AUTO_CREATE, fgsServiceConnectionExecutor, conn)
                else app.bindService(Intent(app, BenInferenceForegroundService::class.java), conn, Context.BIND_AUTO_CREATE)
            }.getOrElse { error ->
                fgsBoundConnections.remove(conn)
                if (continuation.isActive) continuation.resume("IPC_FGS_BIND_FAILED:${error.javaClass.simpleName}")
                false
            }
            if (!bound) {
                fgsBoundConnections.remove(conn)
                if (continuation.isActive) continuation.resume("IPC_FGS_BIND_FAILED")
                return@suspendCancellableCoroutine
            }
            val timeout = Runnable {
                if (continuation.isActive) {
                    unbindForegroundConnection(conn)
                    releaseForegroundLeaseLocked()
                    continuation.resume("IPC_FGS_READY_TIMEOUT")
                }
            }
            fgsCallbackHandler.postDelayed(timeout, FGS_READY_TIMEOUT_MS)
            continuation.invokeOnCancellation {
                fgsCallbackHandler.removeCallbacks(timeout)
                unbindForegroundConnection(conn)
            }
        }
    }

    private fun unbindForegroundConnection(conn: ServiceConnection?) {
        if (conn == null) return
        val marker = fgsBoundConnections.remove(conn) ?: return
        if (marker.compareAndSet(true, false)) runCatching { app.unbindService(conn) }
    }

    private fun releaseForegroundLease() {
        synchronized(this) { releaseForegroundLeaseLocked() }
    }

    private fun releaseForegroundLeaseLocked() {
        val lease = fgsLeaseId
        val remoteFgs = fgsRemote
        if (lease != null && remoteFgs?.binder?.isBinderAlive == true) {
            runCatching {
                remoteFgs.send(Message.obtain(null, BenInferenceForegroundService.CMD_RELEASE).apply {
                    data = Bundle().apply { putString(BenInferenceForegroundService.KEY_LEASE_ID, lease) }
                })
            }
        }
        unbindForegroundConnection(fgsConnection)
        fgsConnection = null
        fgsRemote = null
        fgsBinder = null
        fgsLeaseId = null
    }

    private data class OneShotAttempt<T>(val value: T?, val failure: String?)

    private suspend fun <T> oneShot(
        what: Int,
        data: Bundle,
        timeoutMs: Long = ONE_SHOT_CLIENT_TIMEOUT_MS,
        onProgress: ((String) -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
        maxAttempts: Int = BEN_IPC_MAX_ATTEMPTS,
        decode: (Message) -> T?,
    ): T? {
        var lastFailure: String? = null
        repeat(maxAttempts.coerceIn(1, BEN_IPC_MAX_ATTEMPTS)) { attempt ->
            var attemptFailure: String? = null
            val result = oneShotAttempt(
                what = what,
                data = data,
                timeoutMs = timeoutMs,
                onProgress = onProgress,
                onFailure = { attemptFailure = it },
                decode = decode,
            )
            if (result.value != null) return result.value
            val failure = attemptFailure ?: "IPC_REMOTE_REJECTED"
            if (!benIpcIsRetryable(failure)) {
                onFailure?.invoke(failure)
                return null
            }
            lastFailure = failure
            if (attempt + 1 < maxAttempts.coerceIn(1, BEN_IPC_MAX_ATTEMPTS)) {
                Log.w("BenIPC", "Retrying one-shot after transport failure: $failure")
                delay(BEN_IPC_RETRY_BACKOFF_MS)
            }
        }
        lastFailure?.let { onFailure?.invoke(it) }
        return null
    }

    private suspend fun <T> oneShotAttempt(
        what: Int,
        data: Bundle,
        timeoutMs: Long = ONE_SHOT_CLIENT_TIMEOUT_MS,
        onProgress: ((String) -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
        decode: (Message) -> T?,
    ): OneShotAttempt<T> {
        // For real work, start the service first and only then establish Binder. This removes
        // the vulnerable window where a bound-only service can be created, return its Binder, and
        // remain a background service while a long native operation is about to start. CMD_PING is
        // intentionally exempt so the transport probe remains a pure Binder test.
        if (what != BenInferenceProcessService.CMD_PING) {
            val fgsFailure = acquireForegroundLease()
            if (fgsFailure != null) {
                onFailure?.invoke(fgsFailure)
                return OneShotAttempt(null, fgsFailure)
            }
        }
        val target = try {
            ensureBoundWithRetry(requestForeground = what != BenInferenceProcessService.CMD_PING)
        } catch (_: CancellationException) {
            throw CancellationException("Inference client cancelled")
        } catch (e: Exception) {
            val reason = "IPC_BIND_FAILED: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            onFailure?.invoke(reason)
            return OneShotAttempt(null, reason)
        }
        val binder = serviceBinder
        if (!lifecycle.canSendRemote() || binder == null || !binder.isBinderAlive) {
            onConnectionLost(currentServiceGeneration, processDied = true)
            val reason = "IPC_REMOTE_NOT_READY"
            onFailure?.invoke(reason)
            return OneShotAttempt(null, reason)
        }
        var failureReason: String? = null
        val value = suspendCancellableCoroutine<T?> { continuation ->
            var lastProgress = ""
            if (closed) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val done = AtomicBoolean(false)
            val requestId = UUID.randomUUID().toString()
            val generation = currentServiceGeneration
            val requestStartedAt = System.currentTimeMillis()
            val state = OneShotState<T>(requestId, generation, done, continuation, { reason ->
                failureReason = reason
                onFailure?.invoke(reason)
            })
            oneShots[requestId] = state
            val timeout = Runnable {
                if (done.compareAndSet(false, true)) {
                    val elapsedMs = System.currentTimeMillis() - requestStartedAt
                    val stage = lastProgress.ifBlank { "NO_REMOTE_PROGRESS" }
                    Log.e("BenIPC", "oneShot timeout requestId=$requestId generation=$generation what=$what elapsedMs=$elapsedMs stage=$stage; forcing transport trim")
                    oneShots.remove(requestId)
                    sendCancelBestEffort(target, requestId, generation)
                    val suffix = "_STAGE=${stage.replace(Regex("\\s+"), "_").take(120)}"
                    val reason = "IPC_TIMEOUT_${timeoutMs}MS$suffix;IPC_TRANSPORT_RESET_REQUESTED"
                    failureReason = reason
                    onFailure?.invoke(reason)
                    continuation.resume(null)
                    trim()
                }
            }
            state.timeout = timeout
            requestTimeoutHandler.postDelayed(timeout, timeoutMs)
            val handler = Handler(callbackThread.looper) { message ->
                if (message.data.getString(BenInferenceProcessService.KEY_REQUEST_ID) != requestId) return@Handler true
                if (message.data.getLong(BenInferenceProcessService.KEY_CLIENT_GENERATION, generation) != generation) return@Handler true
                if (message.what == BenInferenceProcessService.REPLY_DIAGNOSTIC_PROGRESS) {
                    lastProgress = readIpcText(message.data, BenInferenceProcessService.KEY_PROGRESS).take(220)
                    BenIpcDiagnosticRecorder.event(app, null, "CLIENT_REMOTE_PROGRESS", lastProgress)
                    Log.d("BenIPC", "oneShot progress requestId=$requestId generation=$generation elapsedMs=${System.currentTimeMillis() - requestStartedAt} stage=$lastProgress")
                    onProgress?.invoke(lastProgress)
                    return@Handler true
                }
                BenIpcDiagnosticRecorder.event(app, null, "CLIENT_TERMINAL_REPLY_RECEIVED", "request=${requestId.take(8)} what=${message.what}")
                oneShots.remove(requestId)
                requestTimeoutHandler.removeCallbacks(timeout)
                state.timeout = null
                if (!done.compareAndSet(false, true)) return@Handler true
                val decoded = runCatching { decode(message) }.getOrElse { error ->
                    val reason = "IPC_DECODE_FAILED: ${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
                    failureReason = reason
                    onFailure?.invoke(reason)
                    null
                }
                if (decoded == null && failureReason == null) {
                    val remoteError = readIpcText(message.data, BenInferenceProcessService.KEY_ERROR)
                    if (remoteError.isNotBlank()) {
                        failureReason = remoteError
                        onFailure?.invoke(remoteError)
                    }
                }
                runCatching { continuation.resume(decoded) }
                true
            }
            continuation.invokeOnCancellation {
                requestTimeoutHandler.removeCallbacks(timeout)
                state.timeout = null
                oneShots.remove(requestId)
                if (done.compareAndSet(false, true)) sendCancelBestEffort(target, requestId, generation)
            }
            val outboundData = Bundle(data).apply {
                putString(BenInferenceProcessService.KEY_REQUEST_ID, requestId)
                putLong(BenInferenceProcessService.KEY_CLIENT_GENERATION, generation)
            }
            val sharedResources = mutableListOf<android.os.SharedMemory>()
            listOf(
                BenInferenceProcessService.KEY_QUERY,
                BenInferenceProcessService.KEY_FIRST,
                BenInferenceProcessService.KEY_SECOND,
            ).forEach { key ->
                val text = outboundData.getString(key) ?: return@forEach
                runCatching { benIpcPutText(outboundData, key, text) }
                    .onSuccess { memory -> if (memory != null) sharedResources += memory }
                    .onFailure { error -> failureReason = "IPC_SHARED_MEMORY_PREP_FAILED:${error.javaClass.simpleName}" }
            }
            if (failureReason == null && bundleSizeBytes(outboundData) > MAX_IPC_PAYLOAD_BYTES) {
                failureReason = "IPC_PAYLOAD_TOO_LARGE"
            }
            if (failureReason == null) {
                runCatching {
                    target.send(Message.obtain(null, what).apply {
                        replyTo = Messenger(handler)
                        this.data = outboundData
                    })
                }.onFailure {
                    oneShots.remove(requestId)
                    requestTimeoutHandler.removeCallbacks(timeout)
                    state.timeout = null
                    if (done.compareAndSet(false, true)) {
                        val reason = "IPC_SEND_FAILED: ${it.javaClass.simpleName}: ${it.message ?: "unknown"}"
                        failureReason = reason
                        onFailure?.invoke(reason)
                        continuation.resume(null)
                    }
                }
            } else {
                oneShots.remove(requestId)
                requestTimeoutHandler.removeCallbacks(timeout)
                state.timeout = null
                if (done.compareAndSet(false, true)) {
                    onFailure?.invoke(failureReason!!)
                    continuation.resume(null)
                }
            }
            sharedResources.forEach(::benIpcCloseSharedMemory)
        }
        return OneShotAttempt(value, failureReason)

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

    private suspend fun ensureBoundWithRetry(requestForeground: Boolean = false, maxAttempts: Int = 3): Messenger {
        var attempt = 0
        var last: Throwable? = null
        while (attempt < maxAttempts) {
            try {
                val candidate = ensureBound(requestForeground)
                val binder = serviceBinder
                if (binder != null && binder.isBinderAlive && lifecycle.canSendRemote()) return candidate
                throw RemoteException("Binder became unusable after connection")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                attempt++
                if (attempt >= maxAttempts) break
                delay(connectionBackoffMs(attempt))
            }
        }
        throw last ?: IllegalStateException("Inference process unavailable")
    }

    private fun connectionBackoffMs(attempt: Int): Long = when (attempt) {
        1 -> 250L
        2 -> 750L
        else -> 1_500L
    }

    private fun unbindIfBound(conn: ServiceConnection?) {
        if (conn == null) return
        val marker = boundConnections.remove(conn) ?: return
        if (marker.compareAndSet(true, false)) runCatching { app.unbindService(conn) }
    }

    private suspend fun ensureBound(requestForeground: Boolean = false): Messenger = connectionMutex.withLock {
        if (closed) throw CancellationException("Inference client closed")
        val existing = remote
        val existingBinder = serviceBinder
        if (existing != null && existingBinder != null && existingBinder.isBinderAlive && lifecycle.canSendRemote()) {
            return@withLock existing
        }
        if (existingBinder != null && !existingBinder.isBinderAlive) onConnectionLost(currentServiceGeneration, processDied = true)
        lifecycle.beginBinding()
        suspendCancellableCoroutine { continuation ->
            val nextGeneration = generationCounter.incrementAndGet()
            pendingBindContinuation = continuation
            lateinit var bindTimeout: Runnable
            val conn = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    callbackHandler.removeCallbacks(bindTimeout)
                    BenIpcDiagnosticRecorder.event(app, null, "CLIENT_WORKER_BIND_CONNECTED", "alive=${service?.isBinderAlive == true}")
                    if (continuation.context[Job]?.isActive != true || closed) {
                        unbindIfBound(this)
                        return
                    }
                    if (service == null || !service.isBinderAlive) {
                        pendingBindContinuation = null
                        pendingBindConnection = null
                        pendingBindGeneration = 0L
                        if (connection === this) connection = null
                        lifecycle.markDead()
                        unbindIfBound(this)
                        if (continuation.context[Job]?.isActive == true) continuation.resumeWithException(IllegalStateException("Dead inference Binder"))
                        return
                    }
                    try {
                        val dr = IBinder.DeathRecipient { onBinderDied(nextGeneration, service) }
                        service.linkToDeath(dr, 0)
                        deathRecipient = dr
                        serviceBinder = service
                        currentServiceGeneration = nextGeneration
                        remote = Messenger(service)
                        warm = false
                        lifecycle.markReady()
                        BenIpcDiagnosticRecorder.event(app, null, "CLIENT_WORKER_READY", "generation=$nextGeneration")
                        pendingBindContinuation = null
                        pendingBindConnection = null
                        pendingBindGeneration = 0L
                        continuation.resume(remote!!)
                    } catch (e: RemoteException) {
                        pendingBindContinuation = null
                        pendingBindConnection = null
                        pendingBindGeneration = 0L
                        if (connection === this) connection = null
                        lifecycle.markDead()
                        unbindIfBound(this)
                        continuation.resumeWithException(e)
                    }
                }
                override fun onServiceDisconnected(name: ComponentName?) { onConnectionLost(nextGeneration) }
                override fun onBindingDied(name: ComponentName?) { onConnectionLost(nextGeneration) }
                override fun onNullBinding(name: ComponentName?) {
                    onConnectionLost(nextGeneration)
                    if (continuation.context[Job]?.isActive == true) continuation.resumeWithException(IllegalStateException("Null inference binding"))
                }
            }
            pendingBindConnection = conn
            pendingBindGeneration = nextGeneration
            connection = conn
            val serviceIntent = Intent(app, BenInferenceProcessService::class.java)
            val boundMarker = AtomicBoolean(true)
            boundConnections[conn] = boundMarker

            // This deadline belongs to this bind generation only. It is armed before the bind
            // call so even a synchronous/very-fast callback is covered, and it is ALWAYS removed
            // when the generation reaches a terminal bind outcome. A previous implementation
            // armed a fire-and-forget timeout after bindService(); after a successful bind it
            // remained queued and later unbound the live connection ~8 s into long diagnostics.
            bindTimeout = Runnable {
                if (continuation.context[Job]?.isActive == true && pendingBindGeneration == nextGeneration) {
                    lifecycle.markDead()
                    pendingBindContinuation = null
                    pendingBindConnection = null
                    pendingBindGeneration = 0L
                    if (connection === conn) connection = null
                    unbindIfBound(conn)
                    continuation.resumeWithException(IllegalStateException("IPC_BIND_TIMEOUT_${BIND_TIMEOUT_MS}MS"))
                }
            }
            callbackHandler.postDelayed(bindTimeout, BIND_TIMEOUT_MS)

            val bound = runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    app.bindService(
                        serviceIntent,
                        Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
                        serviceConnectionExecutor,
                        conn,
                    )
                } else {
                    app.bindService(serviceIntent, conn, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
                }
            }.getOrElse {
                callbackHandler.removeCallbacks(bindTimeout)
                pendingBindConnection = null
                pendingBindGeneration = 0L
                pendingBindContinuation = null
                connection = null
                boundConnections.remove(conn)
                lifecycle.markDead()
                continuation.resumeWithException(it)
                return@suspendCancellableCoroutine
            }
            if (!bound) {
                callbackHandler.removeCallbacks(bindTimeout)
                pendingBindConnection = null
                pendingBindGeneration = 0L
                pendingBindContinuation = null
                connection = null
                boundConnections.remove(conn)
                lifecycle.markDead()
                continuation.resumeWithException(IllegalStateException("Unable to bind inference process"))
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                callbackHandler.removeCallbacks(bindTimeout)
                // Cancellation must invalidate only this bind generation; never tear down a newer
                // connection that may have won a reconnect race.
                if (pendingBindGeneration == nextGeneration) {
                    pendingBindContinuation = null
                    pendingBindConnection = null
                    pendingBindGeneration = 0L
                    if (connection === conn) connection = null
                    lifecycle.markDead()
                }
                unbindIfBound(conn)
            }
        }
    }

    private fun onBinderDied(generation: Long, binder: IBinder) {
        if (generation != currentServiceGeneration || binder !== serviceBinder) return
        onConnectionLost(generation, processDied = true)
    }

    private fun onConnectionLost(generation: Long, processDied: Boolean = true) {
        if (generation != currentServiceGeneration && currentServiceGeneration != 0L) return
        if (BenIpcDiagnosticRecorder.isRunning(app)) {
            BenIpcDiagnosticRecorder.event(app, null, "CLIENT_CONNECTION_LOST", if (processDied) "process_died" else "connection_lost")
        }
        deathRecipient?.let { dr -> serviceBinder?.let { b -> runCatching { b.unlinkToDeath(dr, 0) } } }
        remote = null
        serviceBinder = null
        deathRecipient = null
        val pending = pendingBindContinuation
        val pendingGeneration = pendingBindGeneration
        pendingBindContinuation = null
        pendingBindConnection = null
        pendingBindGeneration = 0L
        connection = null
        warm = false
        lifecycle.markDead()
        if (pending != null && pending.isActive && pendingGeneration == generation) {
            pending.resumeWithException(IllegalStateException(if (processDied) "INFERENCE_PROCESS_DIED" else "Inference connection lost"))
        }
        currentServiceGeneration = generationCounter.incrementAndGet()
        requests.values.toList().forEach { state ->
            if (state.generation != generation) return@forEach
            terminal(state, if (processDied) GenerationEvent.ProcessDied else GenerationEvent.Failed("Inference connection lost"))
        }
        failAllPendingOneShots(
            generation = generation,
            reason = if (processDied) "INFERENCE_PROCESS_DIED" else "INFERENCE_CONNECTION_LOST",
            sendCancel = false,
        )
    }

    /**
     * Terminally drains every one-shot waiting on a generation. Dropping a continuation from
     * the map is not enough: the suspended caller must be resumed/cancelled and its watchdog
     * removed. This is the key invariant for disconnect/rebind races.
     */
    private fun failAllPendingOneShots(
        generation: Long? = null,
        reason: String,
        sendCancel: Boolean,
    ) {
        oneShots.entries.toList().forEach { (requestId, state) ->
            if (generation != null && state.generation != generation) return@forEach
            if (!state.done.compareAndSet(false, true)) {
                oneShots.remove(requestId, state)
                return@forEach
            }
            oneShots.remove(requestId, state)
            state.timeout?.let(requestTimeoutHandler::removeCallbacks)
            state.timeout = null
            if (sendCancel) {
                remote?.let { sendCancelBestEffort(it, requestId, state.generation) }
            }
            runCatching { state.failure?.invoke(reason) }
            // The public one-shot APIs already communicate failure through their nullable result
            // plus onFailure callback. Resume immediately so disconnect/trim can never leave a
            // caller suspended until the long diagnostic watchdog fires.
            runCatching { state.continuation.resume(null) }
        }
    }

    fun trim() {
        requests.values.toList().forEach { state ->
            if (state.terminal.compareAndSet(false, true)) {
                remote?.let { sendCancelBestEffort(it, state.id, state.generation) }
                requests.remove(state.id, state)
                state.watchdog?.let(requestTimeoutHandler::removeCallbacks)
                state.watchdog = null
                state.sink.trySend(GenerationEvent.Cancelled)
                state.sink.close()
            }
        }
        failAllPendingOneShots(reason = "INFERENCE_CLIENT_TRIMMED", sendCancel = true)
        synchronized(this) {
            deathRecipient?.let { dr -> serviceBinder?.let { b -> runCatching { b.unlinkToDeath(dr, 0) } } }
            unbindIfBound(connection)
            if (pendingBindConnection !== connection) unbindIfBound(pendingBindConnection)
            connection = null
            pendingBindConnection = null
            pendingBindGeneration = 0L
            pendingBindContinuation = null
            remote = null
            serviceBinder = null
            deathRecipient = null
            currentServiceGeneration = generationCounter.incrementAndGet()
            warm = false
            lifecycle.markDead()
            // The worker is disposable; the foreground lifecycle is owned by the main-process
            // BenForegroundService, never by the inference worker process.
            runCatching { app.stopService(Intent(app, BenInferenceProcessService::class.java)) }
        }
        releaseForegroundLease()
    }

    companion object {
        private const val GENERATION_CLIENT_TIMEOUT_MS = 45_000L
        private const val ONE_SHOT_CLIENT_TIMEOUT_MS = 30_000L
        private const val DIAGNOSTIC_CLIENT_TIMEOUT_MS = 150_000L
        private const val ISOLATED_EMBEDDING_DIAGNOSTIC_TIMEOUT_MS = 60_000L
        private const val BIND_TIMEOUT_MS = 8_000L
        private const val MAX_IPC_PAYLOAD_BYTES = 128 * 1024
        private const val FGS_READY_TIMEOUT_MS = 2_500L
    }

    override fun close() {
        closed = true
        lifecycle.closeConnection()
        requests.values.toList().forEach { terminal(it, GenerationEvent.Cancelled) }
        failAllPendingOneShots(reason = "INFERENCE_CLIENT_CLOSED", sendCancel = true)
        trim()
        callbackThread.quitSafely()
        fgsCallbackThread.quitSafely()
    }
}
