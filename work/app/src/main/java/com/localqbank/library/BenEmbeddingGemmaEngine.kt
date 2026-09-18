package com.localqbank.library

import android.content.Context
import android.os.Build
import android.util.Log
import com.sentencepiece.Model
import com.sentencepiece.Scoring
import com.sentencepiece.SentencePieceAlgorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorType
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Direct EmbeddingGemma execution boundary.
 *
 * The user-selected EmbeddingGemma .tflite graph is executed directly by LiteRT.
 * No higher-level embedding wrapper is allowed to reinterpret the model tensor contract.
 *
 * SentencePiece is used only for tokenization. Retrieval policy, learner logic and study logic
 * remain outside this class. Any failure falls back to deterministic retrieval.
 */
class BenEmbeddingGemmaEngine(context: Context) {
    private val app = context.applicationContext
    private val models = BenNeuralModelManager(app)
    private val profile = BenNeuralModelRegistry.embeddingGemma300m
    private val policy = BenAiRuntimePolicy(app)
    private val governor = BenAiResourceGovernor(app)
    private val artifactInspector = BenEmbeddingGemmaArtifactInspector()
    private val runtimeMutex = Mutex()
    @Volatile private var activeRuntime: Runtime? = null
    private var activeModelPath: String? = null
    private var activeTokenizerPath: String? = null
    @Volatile private var activeRuntimeAtMs: Long = 0L

    private fun appVersion(): String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    data class SemanticHit<T>(val item: T, val similarity: Double)
    data class Result<T>(val hits: List<SemanticHit<T>>, val elapsedMs: Long, val usedModel: Boolean)

    /** True only when a usable native runtime is already warm; callers must not cold-start this path on the UI thread. */
    fun isWarm(): Boolean =
        activeRuntime != null &&
            activeRuntimeAtMs > 0L &&
            System.currentTimeMillis() - activeRuntimeAtMs <= RUNTIME_IDLE_TIMEOUT_MS


    suspend fun <T> rerank(
        query: String,
        candidates: List<T>,
        textOf: (T) -> String,
        limit: Int = 8
    ): Result<T> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val installed = models.installed(profile)
        val tokenizer = models.installedEmbeddingGemmaTokenizer()
        if (installed == null) {
            fail("EmbeddingGemma model is not installed")
            return@withContext fallback(candidates, limit, started)
        }
        if (tokenizer == null) {
            fail("EmbeddingGemma sentencepiece.model is not installed")
            return@withContext fallback(candidates, limit, started)
        }
        val blockReason = policy.blockReason(profile.estimatedModelMb, profile.estimatedRuntimeMb, governor.snapshot(true))
        if (blockReason != null) {
            fail("EmbeddingGemma blocked: $blockReason")
            BenNeuralTelemetry.blocked(blockReason)
            return@withContext fallback(candidates, limit, started)
        }
        val cleanQuery = clean(query)
        if (cleanQuery.length < 2 || candidates.isEmpty()) {
            return@withContext fallback(candidates, limit, started)
        }
        try {
            runtimeMutex.withLock {
                val runtime = acquireRuntime(installed.file, tokenizer)
                BenNeuralTelemetry.stage(
                    BenNeuralTelemetry.Stage.SEMANTIC_RERANK,
                    "Semantic reranking via ${runtime.backend()}",
                    "EmbeddingGemma 300M",
                    runtime.backend()
                )
                val queryVector = runtime.embed(cleanQuery, Task.RETRIEVAL_QUERY)
                val selected = candidates.take(8)
                val scored = selected.map { candidate ->
                    val text = clean(textOf(candidate))
                    SemanticHit(candidate, cosine(queryVector, runtime.embed(text, Task.RETRIEVAL_DOCUMENT)))
                }.sortedByDescending { it.similarity }.take(limit.coerceIn(1, 8))
                activeRuntimeAtMs = System.currentTimeMillis()
                policy.recordBackendSuccess()
                Result(scored, max(0L, System.currentTimeMillis() - started), true)
            }
        } catch (error: LinkageError) {
            fail("EmbeddingGemma native/runtime linkage: ${error.javaClass.simpleName}: ${error.message?.take(240) ?: "no message"}")
            policy.recordBackendFailure()
            fallback(candidates, limit, started)
        } catch (error: Exception) {
            fail(runtimeMessage(error))
            policy.recordBackendFailure()
            fallback(candidates, limit, started)
        }
    }

    suspend fun compare(first: String, second: String): Double? = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val installed = models.installed(profile) ?: run {
            fail("EmbeddingGemma model is not installed")
            return@withContext null
        }
        val tokenizer = models.installedEmbeddingGemmaTokenizer() ?: run {
            fail("EmbeddingGemma sentencepiece.model is not installed")
            return@withContext null
        }
        val blockReason = policy.blockReason(profile.estimatedModelMb, profile.estimatedRuntimeMb, governor.snapshot(true))
        if (blockReason != null) {
            fail("EmbeddingGemma blocked: $blockReason")
            BenNeuralTelemetry.blocked(blockReason)
            return@withContext null
        }
        try {
            runtimeMutex.withLock {
                val runtime = acquireRuntime(installed.file, tokenizer)
                BenNeuralTelemetry.stage(
                    BenNeuralTelemetry.Stage.SEMANTIC_RERANK,
                    "EmbeddingGemma inference via ${runtime.backend()}",
                    "EmbeddingGemma 300M",
                    runtime.backend()
                )
                val a = runtime.embed(clean(first), Task.SENTENCE_SIMILARITY)
                val b = runtime.embed(clean(second), Task.SENTENCE_SIMILARITY)
                if (a.size != b.size) throw IllegalStateException("Embedding dimension mismatch: ${a.size} vs ${b.size}")
                if (a.size < 128) throw IllegalStateException("Unexpected embedding dimension: ${a.size}")
                val score = cosine(a, b)
                activeRuntimeAtMs = System.currentTimeMillis()
                policy.recordBackendSuccess()
                BenNeuralTelemetry.semantic(max(0L, System.currentTimeMillis() - started), true)
                score
            }
        } catch (error: LinkageError) {
            fail("EmbeddingGemma native/runtime linkage: ${error.javaClass.simpleName}: ${error.message?.take(240) ?: "no message"}")
            policy.recordBackendFailure()
            null
        } catch (error: Exception) {
            fail(runtimeMessage(error))
            policy.recordBackendFailure()
            null
        }
    }

    /**
     * Non-destructive diagnostic for Adaptive Engine. It validates the installed artifact,
     * tokenizer, tensor contract, backend selection and a small semantic-similarity pair.
     * It never writes study data.
     */
    suspend fun diagnostic(onProgress: ((String) -> Unit)? = null): String? = diagnosticReport(onProgress).toText()

    suspend fun diagnosticReport(onProgress: ((String) -> Unit)? = null): BenEmbeddingGemmaDiagnosticReport = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val before = governor.snapshot(true)
        onProgress?.invoke("Checking model + tokenizer artifacts")
        val runtimeBefore = artifactInspector.runtimeMemory()
        val installed = models.installed(profile)
        val tokenizerFile = models.installedEmbeddingGemmaTokenizer()
        val modelBytes = installed?.bytes ?: 0L
        val tokenizerBytes = tokenizerFile?.length() ?: 0L
        onProgress?.invoke("Hashing model + tokenizer")
        val modelHash = installed?.file?.let { runCatching { artifactInspector.sha256(it) }.getOrNull() } ?: "not available"
        val tokenizerHash = tokenizerFile?.let { runCatching { artifactInspector.sha256(it) }.getOrNull() } ?: "not available"
        onProgress?.invoke("Inspecting model graph contract")
        val modelGraphHints = installed?.file?.let { runCatching { artifactInspector.graphHints(it) }.getOrDefault("inspection failed") } ?: "not inspected"
        fun publish(report: BenEmbeddingGemmaDiagnosticReport): BenEmbeddingGemmaDiagnosticReport {
            BenNeuralTelemetry.setDiagnosticReport(report.toText())
            return report
        }
        if (installed == null || tokenizerFile == null) {
            val failure = if (installed == null && tokenizerFile == null) "Model and SentencePiece tokenizer are not installed" else if (installed == null) "EmbeddingGemma model is not installed" else "EmbeddingGemma sentencepiece.model is not installed"
            fail(failure)
            return@withContext publish(failureReport(started, before, runtimeBefore, modelBytes, tokenizerBytes, modelHash, tokenizerHash, modelGraphHints, tokenizerFile, failure))
        }
        val blockReason = policy.blockReason(profile.estimatedModelMb, profile.estimatedRuntimeMb, before)
        if (blockReason != null) {
            fail("EmbeddingGemma blocked: $blockReason")
            BenNeuralTelemetry.blocked(blockReason)
            return@withContext publish(failureReport(started, before, runtimeBefore, modelBytes, tokenizerBytes, modelHash, tokenizerHash, modelGraphHints, tokenizerFile, "Governor blocked: $blockReason"))
        }
        var diagnosticRuntimeForFailure: Runtime? = null
        try {
            val runtimeStarted = System.currentTimeMillis()
            onProgress?.invoke("Creating LiteRT runtime • Qualcomm/NPU dispatch may take time")
            Runtime(installed.file, tokenizerFile, onProgress).use { runtime ->
                diagnosticRuntimeForFailure = runtime
                val runtimeInitMs = System.currentTimeMillis() - runtimeStarted
                val relatedA = "nephrotic syndrome causes edema due to urinary protein loss and reduced plasma oncotic pressure"
                val relatedB = "protein loss in urine lowers plasma oncotic pressure and produces edema"
                val unrelated = "renal calculi commonly cause severe colicky flank pain with hematuria"
                onProgress?.invoke("Running semantic smoke test • 3 embeddings")
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INFERENCE_BEGIN")
                val inferenceStarted = System.currentTimeMillis()
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INFERENCE_1_BEGIN")
                val a = runtime.embed(relatedA, Task.SENTENCE_SIMILARITY)
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INFERENCE_1_PASS", "dim=${a.size}")
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INFERENCE_2_BEGIN")
                val b = runtime.embed(relatedB, Task.SENTENCE_SIMILARITY)
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INFERENCE_2_PASS", "dim=${b.size}")
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INFERENCE_3_BEGIN")
                val c = runtime.embed(unrelated, Task.SENTENCE_SIMILARITY)
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INFERENCE_3_PASS", "dim=${c.size}")
                val inferenceMs = System.currentTimeMillis() - inferenceStarted
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_POST_INFERENCE_BEGIN", "inferenceMs=$inferenceMs")
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_OUTPUT_VALIDATION_BEGIN")
                val finite = a.all { it.isFinite() } && b.all { it.isFinite() } && c.all { it.isFinite() }
                val dimensionsOk = a.size == 768 && b.size == 768 && c.size == 768
                if (finite && dimensionsOk) {
                    BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_OUTPUT_VALIDATION_PASS", "finite=true dims=768/768/768")
                } else {
                    BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_OUTPUT_VALIDATION_FAIL", "finite=$finite dims=${a.size}/${b.size}/${c.size}")
                }
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_SEMANTIC_SCORE_BEGIN")
                val similar = cosine(a, b)
                val unrelatedScore = cosine(a, c)
                val separation = similar - unrelatedScore
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_SEMANTIC_SCORE_PASS", "related=$similar unrelated=$unrelatedScore separation=$separation")
                val norm = sqrt(a.sumOf { it.toDouble() * it.toDouble() })
                val after = governor.snapshot(true)
                val runtimeAfter = artifactInspector.runtimeMemory()
                val contractPassed = runtime.contractPassed()
                val inferencePassed = finite && a.size == 768 && b.size == 768 && c.size == 768
                val semanticPassed = finite && separation > 0.0
                val overall = contractPassed && inferencePassed && semanticPassed
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_REPORT_BUILD_BEGIN", "overall=$overall")
                val report = BenEmbeddingGemmaDiagnosticReport(
                    timestampMs = System.currentTimeMillis(), appVersion = appVersion(),
                    android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    device = artifactInspector.deviceDescription(),
                    abi = Build.SUPPORTED_ABIS.joinToString(", "), modelName = profile.name, modelBytes = modelBytes, modelSha256 = modelHash,
                    tokenizerBytes = tokenizerBytes, tokenizerSha256 = tokenizerHash, tokenizerParseOk = runtime.tokenizerParsed(),
                    tokenizerSampleTokens = runtime.lastTokenCount(), tokenizerSpecialTokens = "BOS=2, EOS=1, PAD=0; padded=${runtime.sequenceLengthValue()}",
                    modelGraphHints = modelGraphHints,
                    qnnAttempted = runtime.qnnAttempted(), qnnAvailable = runtime.qnnAvailable(), qnnFallback = runtime.qnnFallback(), qnnFailure = runtime.qnnFailure(), backendDetails = runtime.backendDetails(),
                    backend = runtime.backend(), inputs = runtime.inputsDescription(), outputs = runtime.outputsDescription(), sequenceLength = runtime.sequenceLengthValue(),
                    embeddingDimension = a.size, embeddingType = "FLOAT32", embeddingFinite = finite, embeddingNorm = norm,
                    semanticSimilarCosine = similar, semanticUnrelatedCosine = unrelatedScore, semanticSeparation = separation,
                    runtimeInitMs = runtimeInitMs, inferenceMs = inferenceMs, totalMs = System.currentTimeMillis() - started,
                    availableRamBeforeMb = before.availableMemoryMb, availableRamAfterMb = after.availableMemoryMb,
                    javaHeapBeforeMb = runtimeBefore.javaHeapMb, javaHeapAfterMb = runtimeAfter.javaHeapMb,
                    nativeHeapBeforeMb = runtimeBefore.nativeHeapMb, nativeHeapAfterMb = runtimeAfter.nativeHeapMb,
                    thermalBefore = before.thermalStatus, thermalAfter = after.thermalStatus, powerSaveBefore = before.powerSave, powerSaveAfter = after.powerSave,
                    contractPassed = contractPassed, inferencePassed = inferencePassed, semanticSmokePassed = semanticPassed, overallPassed = overall,
                    failure = if (overall) null else "One or more diagnostic gates failed or require review"
                )
                if (overall) { policy.recordBackendSuccess(); BenNeuralTelemetry.semantic(inferenceMs, true) } else { policy.recordBackendFailure(); BenNeuralTelemetry.error(report.failure ?: "Diagnostic review required") }
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_REPORT_BUILD_PASS", "overall=$overall")
                return@withContext publish(report)
            }
        } catch (error: LinkageError) {
            val message = "EmbeddingGemma diagnostic native/runtime linkage: ${detailedThrowable(error)}"
            fail(message); policy.recordBackendFailure()
            return@withContext publish(failureReport(
                started, before, runtimeBefore, modelBytes, tokenizerBytes, modelHash, tokenizerHash,
                modelGraphHints, tokenizerFile, message, diagnosticRuntimeForFailure
            ))
        } catch (error: Exception) {
            val message = runtimeMessage(error)
            fail(message); policy.recordBackendFailure()
            return@withContext publish(failureReport(
                started, before, runtimeBefore, modelBytes, tokenizerBytes, modelHash, tokenizerHash,
                modelGraphHints, tokenizerFile, message, diagnosticRuntimeForFailure
            ))
        }
    }

    private fun acquireRuntime(modelFile: File, tokenizerFile: File): Runtime {
        val now = System.currentTimeMillis()
        val stale = now - activeRuntimeAtMs > RUNTIME_IDLE_TIMEOUT_MS
        if (activeRuntime == null || activeModelPath != modelFile.absolutePath || activeTokenizerPath != tokenizerFile.absolutePath || stale) {
            runCatching { activeRuntime?.close() }
            activeRuntime = Runtime(modelFile, tokenizerFile)
            activeModelPath = modelFile.absolutePath
            activeTokenizerPath = tokenizerFile.absolutePath
        }
        activeRuntimeAtMs = now
        return activeRuntime!!
    }

    fun trim() {
        // Never block the foreground/UI thread behind an active native inference. If the
        // runtime is currently in use, the idle timeout will reclaim it after the request.
        if (!runtimeMutex.tryLock()) return
        try {
            activeRuntime?.close()
            activeRuntime = null
            activeModelPath = null
            activeTokenizerPath = null
            activeRuntimeAtMs = 0L
        } finally {
            runtimeMutex.unlock()
        }
    }

    fun close() = trim()

    private fun failureReport(
        started: Long,
        before: BenAiResourceGovernor.Snapshot,
        memory: BenEmbeddingGemmaArtifactInspector.RuntimeMemory,
        modelBytes: Long,
        tokenizerBytes: Long,
        modelHash: String,
        tokenizerHash: String,
        modelGraphHints: String,
        tokenizerFile: File?,
        failure: String,
        runtime: Runtime? = null
    ): BenEmbeddingGemmaDiagnosticReport {
        val dispatchGraph = modelGraphHints.contains("DISPATCH_OP") && modelGraphHints.contains("Qualcomm")
        val qnnPreflight = if (dispatchGraph && Build.VERSION.SDK_INT >= 31) {
            runCatching {
                val provider = BuiltinNpuAcceleratorProvider(app)
                val supported = provider.isDeviceSupported()
                val ready = provider.isLibraryReady()
                val failure = when {
                    !supported -> "Qualcomm NPU provider reports this device unsupported"
                    !ready -> "Qualcomm NPU runtime library is not packaged/ready"
                    else -> null
                }
                Triple(true, supported && ready, failure)
            }.getOrElse { Triple(true, false, "Qualcomm NPU provider preflight failed: ${it.javaClass.simpleName}: ${it.message?.take(220) ?: "no message"}") }
        } else {
            Triple(false, false, null)
        }
        val runtimeSnapshot = runtime
        val runtimeInputs = runtimeSnapshot?.inputsDescription() ?: "not inspected"
        val runtimeOutputs = runtimeSnapshot?.outputsDescription() ?: "not inspected"
        val runtimeSequence = runtimeSnapshot?.sequenceLengthValue() ?: 0
        val runtimeEmbeddingDimension = 0
        val runtimeBackend = runtimeSnapshot?.backend() ?: if (dispatchGraph) "Qualcomm NPU not started" else "not started"
        val runtimeQnnAttempted = runtimeSnapshot?.qnnAttempted() ?: qnnPreflight.first
        val runtimeQnnAvailable = runtimeSnapshot?.qnnAvailable() ?: qnnPreflight.second
        val runtimeQnnFailure = runtimeSnapshot?.qnnFailure() ?: qnnPreflight.third
        val runtimeBackendDetails = runtimeSnapshot?.backendDetails() ?: "preflight only; runtime was not created"
        return BenEmbeddingGemmaDiagnosticReport(
            timestampMs = System.currentTimeMillis(), appVersion = appVersion(),
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = artifactInspector.deviceDescription(),
            abi = Build.SUPPORTED_ABIS.joinToString(", "), modelName = profile.name, modelBytes = modelBytes, modelSha256 = modelHash,
            tokenizerBytes = tokenizerBytes, tokenizerSha256 = tokenizerHash, tokenizerParseOk = tokenizerFile?.let { runCatching { Model.parseFrom(it.toPath()) }.isSuccess } == true, tokenizerSampleTokens = tokenizerFile?.let { runCatching { Model.parseFrom(it.toPath()).encodeNormalized("diagnostic", SentencePieceAlgorithm(true, Scoring.HIGHEST_SCORE)).size }.getOrDefault(0) } ?: 0, tokenizerSpecialTokens = "BOS=2, EOS=1, PAD=0", modelGraphHints = modelGraphHints,
            qnnAttempted = runtimeQnnAttempted, qnnAvailable = runtimeQnnAvailable, qnnFallback = false, qnnFailure = runtimeQnnFailure, backendDetails = runtimeBackendDetails, backend = runtimeBackend, inputs = runtimeInputs, outputs = runtimeOutputs,
            sequenceLength = runtimeSequence, embeddingDimension = runtimeEmbeddingDimension, embeddingType = "unknown", embeddingFinite = false, embeddingNorm = 0.0, semanticSimilarCosine = 0.0, semanticUnrelatedCosine = 0.0, semanticSeparation = 0.0,
            runtimeInitMs = runtimeSnapshot?.runtimeInitMs() ?: 0L, inferenceMs = runtimeSnapshot?.lastInferenceMs() ?: 0L, totalMs = System.currentTimeMillis() - started, availableRamBeforeMb = before.availableMemoryMb, availableRamAfterMb = before.availableMemoryMb,
            javaHeapBeforeMb = memory.javaHeapMb, javaHeapAfterMb = memory.javaHeapMb, nativeHeapBeforeMb = memory.nativeHeapMb, nativeHeapAfterMb = memory.nativeHeapMb,
            thermalBefore = before.thermalStatus, thermalAfter = before.thermalStatus, powerSaveBefore = before.powerSave, powerSaveAfter = before.powerSave,
            contractPassed = runtimeSnapshot?.contractPassed() == true, inferencePassed = false, semanticSmokePassed = false, overallPassed = false, failure = failure
        )
    }

    private fun modelGraphHints(file: File): String = artifactInspector.graphHints(file)

    /**
     * Direct model contract + inference implementation.
     *
     * EmbeddingGemma is now executed through LiteRT's modern CompiledModel API.
     * The legacy Interpreter + QNN-delegate path is deliberately removed here: the
     * installed EmbeddingGemma artifact can contain LiteRT dispatch/AOT constructs that
     * the legacy interpreter cannot resolve (DISPATCH_OP). CompiledModel is the supported
     * accelerator-first Android API for current LiteRT models.
     *
     * Portable models use CPU/XNNPACK. Qualcomm DISPATCH_OP artifacts are treated as
     * hardware-specific and are executed only through the matching Qualcomm NPU runtime;
     * they are never incorrectly sent to CPU. A missing/mismatched accelerator always
     * falls back to deterministic Ben/RAG rather than risking a native crash.
     */
    private inner class Runtime(model: File, tokenizerFile: File, private val onProgress: ((String) -> Unit)? = null) : AutoCloseable {
        // Must be initialized before init{}: Qualcomm runtime preparation is invoked from
        // init{}, and Kotlin initializes properties in source order. Keeping the lock here
        // above init prevents a null monitor during first runtime construction.
        private val qualcommRuntimeLock = Any()
        // Same reasoning applies here: ensureQualcommRuntimeDirectory() (called from init{})
        // reads this list. Declaring it after init{} would leave its backing field null the
        // first time a Qualcomm DISPATCH_OP model is loaded, throwing a NullPointerException
        // instead of the intended IllegalStateException/diagnostic path.
        private val qualcommRuntimeLibraryNames = listOf(
            "libLiteRtDispatch_Qualcomm.so",
            "libQnnSystem.so",
            "libQnnHtp.so",
            "libQnnHtpPrepare.so",
            "libQnnIr.so",
            "libQnnSaver.so",
            "libQnnHtpV75Stub.so",
            "libQnnHtpV75Skel.so"
        )
        private lateinit var compiledModel: CompiledModel
        private var environment: Environment? = null
        private lateinit var tokenizer: Model
        private val tokenizerAlgorithm = SentencePieceAlgorithm(true, Scoring.HIGHEST_SCORE)
        private var backendName: String = "LiteRT CompiledModel CPU/XNNPACK"
        private var qnnAttemptedValue = false
        private var qnnAvailableValue = false
        private var qnnFallbackValue = false
        private var qnnFailureValue: String? = null
        private var backendDetailsValue: String = ""
        private var invocationFailureValue: String? = null
        private var tokenizerParsedValue = false
        private var lastTokenCountValue = 0
        private var inputsDescriptionValue = ""
        private var outputsDescriptionValue = ""
        private var inputIdsType: TensorType.ElementType = TensorType.ElementType.INT
        private var attentionMaskType: TensorType.ElementType = TensorType.ElementType.INT
        private var sequenceLengthValue = 0
        private var outputDimensionValue = 0
        private var runtimeInitMsValue = 0L
        private var lastInferenceMsValue = 0L
        private var contractDescription = ""
        private var inputBuffers: List<com.google.ai.edge.litert.TensorBuffer> = emptyList()
        private var outputBuffers: List<com.google.ai.edge.litert.TensorBuffer> = emptyList()

        init {
            try {
                // SentencePiece4J is pure Java and therefore safe for offline Android tokenization.
            onProgress?.invoke("Parsing SentencePiece tokenizer")
            tokenizer = Model.parseFrom(tokenizerFile.toPath())
            tokenizerParsedValue = true

            val dispatchGraph = modelGraphHints(model).let {
                it.contains("DISPATCH_OP") && it.contains("Qualcomm")
            }
            val compileStarted = System.currentTimeMillis()

            if (dispatchGraph) {
                if (Build.VERSION.SDK_INT < 31) {
                    qnnAttemptedValue = true
                    qnnFailureValue = "Qualcomm LiteRT NPU path requires Android API 31+"
                    throw IllegalStateException(qnnFailureValue)
                }
                // The installed ~181 MiB artifact is a Qualcomm/SM8650 dispatch graph, not
                // the portable CPU model. DISPATCH_OP cannot be executed by CPU/XNNPACK.
                // Request Qualcomm NPU explicitly and fail closed if the matching vendor
                // runtime is not packaged/available. This DISPATCH_OP artifact is NPU-only;
                // do not silently mix in CPU fallback for the real-device diagnostic.
                qnnAttemptedValue = true
                val nativeDir = File(app.applicationInfo.nativeLibraryDir)
                // Android package/install layouts can place packaged JNI libraries outside
                // ApplicationInfo.nativeLibraryDir (notably split/legacy extraction paths).
                // For a precompiled Qualcomm DISPATCH_OP graph this is not safe to guess.
                // Locate the exact bundled runtime first; if Android did not expose it in
                // nativeLibraryDir, extract the signed APK's own arm64 entries into an
                // app-private runtime directory and use that directory as the dispatch/QNN
                // library root. No external/downloaded binary is accepted at runtime.
                onProgress?.invoke("Preparing verified Qualcomm runtime libraries")
                val qualcommRuntimeDir = ensureQualcommRuntimeDirectory(nativeDir)
                // Qualcomm HTP/Hexagon loads its DSP-side skel libraries through
                // ADSP_LIBRARY_PATH. The Android linker can find the host .so files in
                // nativeLibraryDir, but the DSP RPC loader is not guaranteed to search that
                // directory unless it is explicitly exported. This is used by Google's
                // Qualcomm LiteRT examples and is safe for this app-scoped process.
                runCatching { android.system.Os.setenv("ADSP_LIBRARY_PATH", qualcommRuntimeDir.absolutePath, true) }
                onProgress?.invoke("Checking Qualcomm NPU provider")
                val npuProvider = BuiltinNpuAcceleratorProvider(app)
                val providerDir = qualcommRuntimeDir.absolutePath
                val supported = runCatching { npuProvider.isDeviceSupported() }.getOrDefault(false)
                backendDetailsValue = buildString {
                    append("nativeLibraryDir=${nativeDir.absolutePath}; runtimeDir=${qualcommRuntimeDir.absolutePath}; providerLibraryDir=$providerDir; ")
                    append("deviceSupported=$supported; ")
                    append("runtimeLibs=${qualcommRuntimeInventory(qualcommRuntimeDir)}")
                }
                if (!supported) {
                    qnnFailureValue = "Qualcomm NPU provider reports this device unsupported"
                    throw IllegalStateException(qnnFailureValue)
                }
                val missingRuntime = qualcommRequiredRuntimeLibraries(qualcommRuntimeDir)
                if (missingRuntime.isNotEmpty()) {
                    qnnFailureValue = "Qualcomm AOT runtime libraries missing from verified runtime directory: ${missingRuntime.joinToString()}; DISPATCH_OP invocation is unsafe"
                    backendDetailsValue += "; runtimeGate=FAIL"
                    throw IllegalStateException(qnnFailureValue)
                }
                backendDetailsValue += "; runtimeGate=PASS"
                // This is a precompiled Qualcomm DISPATCH_OP/AOT artifact. For AOT execution
                // we deliberately expose only the DispatchLibraryDir to the LiteRT environment.
                // The convenience Environment.create(context, provider, ...) also supplies a
                // CompilerPluginLibraryDir; that directory is intended for JIT compilation.
                // Keeping the AOT path dispatch-only removes an unnecessary compiler-plugin
                // variable from the execution contract and prevents accidental JIT/plugin
                // interaction with an already compiled context binary.
                val env = Environment.create(
                    app,
                    mapOf(Environment.Option.DispatchLibraryDir to providerDir),
                    true
                )
                val available = env.getAvailableAccelerators()
                qnnAvailableValue = Accelerator.NPU in available
                backendDetailsValue += "; environmentAccelerators=${available.joinToString()}; environmentMode=AOT_DISPATCH_ONLY"
                if (!qnnAvailableValue) {
                    qnnFailureValue = "Qualcomm environment does not expose Accelerator.NPU; available=${available.joinToString()}"
                    throw IllegalStateException(qnnFailureValue)
                }
                try {
                    val options = CompiledModel.Options(Accelerator.NPU).apply {
                        // Verbose logging is diagnostic-only and helps surface Qualcomm/QNN
                        // dispatch errors that the Kotlin exception otherwise collapses to
                        // 'Failed to invoke the compiled model'. Burst is bounded to the
                        // diagnostic/runtime call and is not used as Ben's scheduling policy.
                        qualcommOptions = CompiledModel.QualcommOptions(
                            logLevel = CompiledModel.QualcommOptions.LogLevel.VERBOSE,
                            htpPerformanceMode = CompiledModel.QualcommOptions.HtpPerformanceMode.BURST
                        )
                    }
                    onProgress?.invoke("Compiling/attaching LiteRT Qualcomm NPU model")
                    BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_COMPILED_MODEL_CREATE_BEGIN", "accelerator=NPU")
                    compiledModel = CompiledModel.create(
                        model.absolutePath,
                        options,
                        env
                    )
                    BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_COMPILED_MODEL_CREATE_PASS")
                    environment = env
                    backendDetailsValue += "; compiledModelCreate=PASS; qualcommLog=VERBOSE; htpPerformance=BURST"
                } catch (error: Exception) {
                    runCatching { env.close() }
                    throw error
                }
                backendName = "LiteRT CompiledModel Qualcomm NPU/HTP"
            } else {
                environment = null
                onProgress?.invoke("Creating LiteRT CPU runtime")
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_COMPILED_MODEL_CREATE_BEGIN", "accelerator=CPU")
                compiledModel = CompiledModel.create(
                    model.absolutePath,
                    CompiledModel.Options(Accelerator.CPU),
                    null
                )
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_COMPILED_MODEL_CREATE_PASS")
                backendName = "LiteRT CompiledModel CPU/XNNPACK"
            }

            onProgress?.invoke("Inspecting input/output tensor buffers")
            outputBuffers = compiledModel.createOutputBuffers()
            if (outputBuffers.isEmpty()) {
                throw IllegalStateException("EmbeddingGemma returned no output buffers")
            }

            // IMPORTANT: use the signature-ordered bulk buffers as the source of truth.
            // Qualcomm AOT/dispatch graphs may not expose the original tensor names through
            // LiteRT's Kotlin name lookup even though the buffers are valid and executable.
            // The previous implementation incorrectly treated that metadata limitation as a
            // missing input tensor and aborted before inference.
            val bulkInputs = runCatching { compiledModel.createInputBuffers() }.getOrElse { error ->
                throw IllegalStateException("Could not create EmbeddingGemma input buffers: ${error.message ?: error.javaClass.simpleName}")
            }
            if (bulkInputs.isEmpty()) {
                throw IllegalStateException("EmbeddingGemma returned no input buffers")
            }
            inputBuffers = bulkInputs

            // EmbeddingGemma token tensors are integer buffers. The official exported model
            // contract uses input_ids plus attention_mask, but hardware-specialized AOT variants
            // can fold the mask into the compiled graph and expose only input_ids. Therefore:
            //   2 buffers -> input_ids + attention_mask
            //   1 buffer  -> input_ids with attention masking internal to the graph
            // Never require tensor names to establish this contract.
            inputIdsType = detectIntegerType(inputBuffers[0])
            attentionMaskType = if (inputBuffers.size >= 2) detectIntegerType(inputBuffers[1]) else inputIdsType
            validateInputTypes()

            sequenceLengthValue = inferSequenceLengthFromBuffer(inputBuffers[0], inputIdsType)
            if (sequenceLengthValue <= 0) {
                throw IllegalStateException("Unable to determine EmbeddingGemma input sequence length from buffer")
            }
            val firstBytes = runCatching { modelInputBufferSize(inputBuffers[0], inputIdsType) }.getOrDefault(0)
            val secondBytes = if (inputBuffers.size >= 2) runCatching { modelInputBufferSize(inputBuffers[1], attentionMaskType) }.getOrDefault(0) else 0
            val firstShape = "[1,$sequenceLengthValue]"
            val secondShape = if (inputBuffers.size >= 2) "[1,${secondBytes / Int.SIZE_BYTES}]" else null
            inputsDescriptionValue = buildString {
                append("input#0:INT:$firstShape:bytes=$firstBytes")
                if (inputBuffers.size >= 2) {
                    append(" | input#1:INT:$secondShape:bytes=$secondBytes")
                    append(" | contract=input_ids+attention_mask")
                } else {
                    append(" | attention_mask=internal_or_compiled")
                }
            }
            outputsDescriptionValue = "out#0:bufferCount=${outputBuffers.size}"
            runtimeInitMsValue = System.currentTimeMillis() - compileStarted
            contractDescription = "backend=$backendName; init=${System.currentTimeMillis() - compileStarted}ms; inputBuffers=${inputBuffers.size}; inputs=$inputsDescriptionValue; outputs=$outputsDescriptionValue"
            onProgress?.invoke("Tensor contract ready • ${inputBuffers.size} input buffer(s), ${outputBuffers.size} output buffer(s), sequence=$sequenceLengthValue")
            } catch (error: Exception) {
                cleanupAfterInitFailure()
                throw error
            } catch (error: LinkageError) {
                cleanupAfterInitFailure()
                throw error
            }
        }

        private fun ensureQualcommRuntimeDirectory(nativeDir: File): File {
            val required = qualcommRuntimeLibraryNames
            if (qualcommRequiredRuntimeLibraries(nativeDir).isEmpty()) return nativeDir

            val runtimeDir = File(app.filesDir, "litert_qualcomm_runtime/arm64-v8a")
            val lock = File(app.filesDir, "litert_qualcomm_runtime/.lock")
            synchronized(qualcommRuntimeLock) {
                if (qualcommRequiredRuntimeLibraries(runtimeDir).isEmpty()) return runtimeDir
                runtimeDir.mkdirs()
                val sourceApks = buildList {
                    add(File(app.applicationInfo.sourceDir))
                    if (Build.VERSION.SDK_INT >= 21) {
                        app.applicationInfo.splitSourceDirs?.mapTo(this) { File(it) }
                    }
                }.filter { it.isFile }
                require(sourceApks.isNotEmpty()) { "No installed APK source available for Qualcomm runtime extraction" }

                lock.parentFile?.mkdirs()
                lock.createNewFile()
                try {
                    for (name in required) {
                        if (File(runtimeDir, name).isFile && File(runtimeDir, name).length() > 0L) continue
                        val target = File(runtimeDir, name)
                        val temp = File(runtimeDir, "$name.tmp")
                        var copied = false
                        for (apk in sourceApks) {
                            ZipFile(apk).use { zip ->
                                val entry = zip.getEntry("lib/arm64-v8a/$name") ?: return@use
                                require(entry.size >= 0L && entry.size <= 256L * 1024L * 1024L) {
                                    "Refusing unexpected Qualcomm runtime size for $name"
                                }
                                zip.getInputStream(entry).use { input ->
                                    temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                                }
                                if (temp.length() != entry.size) {
                                    temp.delete()
                                    throw IllegalStateException("Incomplete Qualcomm runtime extraction for $name")
                                }
                                if (!temp.renameTo(target)) {
                                    temp.delete()
                                    throw IllegalStateException("Could not finalize Qualcomm runtime $name")
                                }
                                copied = true
                            }
                            if (copied) break
                        }
                        if (!copied) throw IllegalStateException("Bundled Qualcomm runtime library not found in installed APKs: $name")
                    }
                } finally {
                    lock.delete()
                }
            }
            val missing = qualcommRequiredRuntimeLibraries(runtimeDir)
            if (missing.isNotEmpty()) throw IllegalStateException("Qualcomm runtime extraction incomplete: ${missing.joinToString()}")
            return runtimeDir
        }


        // Single source of truth for every Qualcomm library the AOT/DISPATCH_OP path needs
        // (declared above, before init{} — see the comment next to qualcommRuntimeLock).
        // Previously ensureQualcommRuntimeDirectory() extracted 8 files (including
        // libQnnHtpPrepare.so, libQnnIr.so, libQnnSaver.so) but qualcommRequiredRuntimeLibraries()
        // and qualcommRuntimeInventory() only ever checked/reported 5 of them. That meant a
        // build or device missing just those 3 files would still report runtimeGate=PASS and a
        // "complete" runtime inventory right up until CompiledModel.create() failed with an
        // opaque native error. All three functions now check the same full list.
        private fun qualcommRequiredRuntimeLibraries(nativeDir: File): List<String> {
            return qualcommRuntimeLibraryNames.filter { name ->
                val file = File(nativeDir, name)
                !file.isFile || file.length() <= 0L
            }
        }

        private fun qualcommRuntimeInventory(nativeDir: File): String {
            return qualcommRuntimeLibraryNames.joinToString(",") { name ->
                val file = File(nativeDir, name)
                if (!file.isFile || file.length() <= 0L) {
                    "$name=missing"
                } else {
                    val hash = runCatching { artifactInspector.sha256(file) }.getOrDefault("hash-error")
                    "$name=${file.length()}B:$hash"
                }
            }
        }

        private fun cleanupAfterInitFailure() {
            // Constructor failure happens before AutoCloseable.use can own this Runtime.
            // Release every native object we may already have created before propagating the
            // original failure; repeated diagnostics must not accumulate native handles.
            inputBuffers.forEach { runCatching { it.close() } }
            outputBuffers.forEach { runCatching { it.close() } }
            runCatching { if (::compiledModel.isInitialized) compiledModel.close() }
            runCatching { environment?.close() }
        }

        fun embed(text: String, task: Task): List<Float> {
            val ids = tokenize(task.prefix + text)
            val padded = IntArray(sequenceLengthValue)
            val count = minOf(ids.size, sequenceLengthValue)
            for (i in 0 until count) padded[i] = ids[i]
            val mask = IntArray(sequenceLengthValue) { if (it < count) 1 else 0 }

            // Input buffers are returned by LiteRT in signature order. For the normal exported
            // EmbeddingGemma graph that is input_ids, attention_mask. Qualcomm AOT variants may
            // expose only input_ids because masking was compiled into the graph.
            BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INPUT_WRITE_BEGIN", "tokenCount=$count sequence=$sequenceLengthValue buffers=${inputBuffers.size}")
            writeTokenData(inputBuffers[0], padded, inputIdsType)
            if (inputBuffers.size >= 2) writeTokenData(inputBuffers[1], mask, attentionMaskType)
            BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_INPUT_WRITE_PASS")
            val inferenceStarted = System.currentTimeMillis()
            BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_NATIVE_RUN_BEGIN", "task=${task.name}")
            try {
                compiledModel.run(inputBuffers, outputBuffers)
                lastInferenceMsValue = System.currentTimeMillis() - inferenceStarted
                BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_NATIVE_RUN_PASS", "ms=$lastInferenceMsValue")
            } catch (error: LinkageError) {
                lastInferenceMsValue = System.currentTimeMillis() - inferenceStarted
                invocationFailureValue = detailedThrowable(error)
                qnnFailureValue = invocationFailureValue
                Log.e(TAG, "EmbeddingGemma Qualcomm/NPU invocation failed", error)
                throw error
            } catch (error: Exception) {
                lastInferenceMsValue = System.currentTimeMillis() - inferenceStarted
                invocationFailureValue = detailedThrowable(error)
                qnnFailureValue = invocationFailureValue
                Log.e(TAG, "EmbeddingGemma Qualcomm/NPU invocation failed", error)
                throw error
            }
            if (outputBuffers.isEmpty()) throw IllegalStateException("EmbeddingGemma produced no output buffers")
            BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_OUTPUT_READ_BEGIN")
            val candidates = outputBuffers.mapIndexedNotNull { index, buffer ->
                runCatching { index to buffer.readFloat() }.getOrNull()
            }
            BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_OUTPUT_READ_PASS", "buffers=${candidates.size}")
            val selected = candidates.firstOrNull { it.second.size == 768 }
                ?: candidates.firstOrNull()
                ?: throw IllegalStateException("EmbeddingGemma output buffers could not be read as FLOAT32")
            val output = selected.second
            outputDimensionValue = output.size
            if (output.size != 768) {
                throw IllegalStateException("EmbeddingGemma produced ${output.size} float values; expected 768")
            }
            return output.toList()
        }

        private fun tokenize(text: String): IntArray {
            val ids = tokenizer.encodeNormalized(text, tokenizerAlgorithm).map { it }.toIntArray()
            // EmbeddingGemma/Gemma tokenizer uses BOS=2, EOS=1, PAD=0.
            val withSpecial = IntArray(ids.size + 2)
            withSpecial[0] = 2
            ids.copyInto(withSpecial, destinationOffset = 1)
            withSpecial[withSpecial.lastIndex] = 1
            lastTokenCountValue = withSpecial.size
            return withSpecial
        }

        private fun validateInputTypes() {
            val valid = TensorType.ElementType.INT
            if (inputIdsType != valid || attentionMaskType != valid) {
                throw IllegalStateException(
                    "EmbeddingGemma Android path currently requires INT32 token inputs; got input_ids=$inputIdsType attention_mask=$attentionMaskType"
                )
            }
        }

        private fun resolveTensorType(
            model: CompiledModel,
            candidates: List<String>,
            fallback: TensorType.ElementType
        ): TensorType.ElementType {
            for (name in candidates) {
                val type = runCatching { model.getInputTensorType(name).elementType }.getOrNull()
                if (type != null) return type
            }
            return fallback
        }

        private fun inferSequenceLength(model: CompiledModel, name: String, type: TensorType.ElementType): Int {
            val bytes = runCatching { model.getInputBufferRequirements(name).bufferSize }.getOrNull() ?: return 0
            val bytesLong = bytes.toLong()
            val bytesPerElement = Int.SIZE_BYTES.toLong()
            if (bytesLong <= 0L || bytesLong % bytesPerElement != 0L) return 0
            return (bytesLong / bytesPerElement).toInt()
        }

        private fun inferSequenceLengthFromBytes(type: TensorType.ElementType): Int {
            val bytes = runCatching { modelInputBufferSize(inputBuffers[0], type) }.getOrNull() ?: return 0
            val bytesPerElement = bytesPerElement(type)
            if (bytes <= 0 || bytes % bytesPerElement != 0) return 0
            return bytes / bytesPerElement
        }

        private fun inferSequenceLengthFromBuffer(buffer: com.google.ai.edge.litert.TensorBuffer, type: TensorType.ElementType): Int {
            val bytes = runCatching { modelInputBufferSize(buffer, type) }.getOrNull() ?: return 0
            val bytesPerElement = bytesPerElement(type)
            if (bytes <= 0 || bytes % bytesPerElement != 0) return 0
            return bytes / bytesPerElement
        }

        private fun bytesPerElement(type: TensorType.ElementType): Int = when (type) {
            TensorType.ElementType.INT -> Int.SIZE_BYTES
            TensorType.ElementType.INT64 -> Long.SIZE_BYTES
            else -> Int.SIZE_BYTES
        }

        private fun detectIntegerType(buffer: com.google.ai.edge.litert.TensorBuffer): TensorType.ElementType {
            // LiteRT's public Kotlin API does not expose a positional tensor type query. Probe
            // the supported integer reads instead; this is read-only and works for both INT32
            // and INT64 EmbeddingGemma exports without relying on private JNI APIs.
            if (runCatching { buffer.readInt() }.isSuccess) return TensorType.ElementType.INT
            if (runCatching { buffer.readLong() }.isSuccess) return TensorType.ElementType.INT64
            throw IllegalStateException("EmbeddingGemma input buffer is neither INT32 nor INT64")
        }

        private fun modelInputBufferSize(
            buffer: com.google.ai.edge.litert.TensorBuffer,
            type: TensorType.ElementType
        ): Int {
            return when (type) {
                TensorType.ElementType.INT -> buffer.readInt().size * Int.SIZE_BYTES
                TensorType.ElementType.INT64 -> buffer.readLong().size * Long.SIZE_BYTES
                else -> throw IllegalStateException("Unsupported EmbeddingGemma integer input type: $type")
            }
        }

        private fun describeInput(name: String, type: TensorType.ElementType, sequence: Int): String {
            val bytes = runCatching { compiledModel.getInputBufferRequirements(name).bufferSize }.getOrNull()
            val strides = runCatching { compiledModel.getInputBufferRequirements(name).strides }.getOrNull()
            return "$name:$type:[1,$sequence]" +
                (bytes?.let { ":bytes=$it" } ?: "") +
                (strides?.let { ":strides=$it" } ?: "")
        }

        private fun writeTokenData(
            buffer: com.google.ai.edge.litert.TensorBuffer,
            values: IntArray,
            type: TensorType.ElementType
        ) {
            when (type) {
                TensorType.ElementType.INT -> buffer.writeInt(values)
                TensorType.ElementType.INT64 -> buffer.writeLong(values.map(Int::toLong).toLongArray())
                else -> throw IllegalStateException("Unsupported token input type: $type")
            }
        }

        fun backend(): String = backendName
        fun contract(): String = contractDescription
        fun contractPassed(): Boolean = sequenceLengthValue > 0 && outputDimensionValue == 768 && inputBuffers.isNotEmpty() && inputBuffers.size <= 2 && inputIdsType in setOf(TensorType.ElementType.INT, TensorType.ElementType.INT64)
        fun tokenizerParsed(): Boolean = tokenizerParsedValue
        fun lastTokenCount(): Int = lastTokenCountValue
        fun sequenceLengthValue(): Int = sequenceLengthValue
        fun qnnAttempted(): Boolean = qnnAttemptedValue
        fun qnnAvailable(): Boolean = qnnAvailableValue
        fun qnnFallback(): Boolean = qnnFallbackValue
        fun qnnFailure(): String? = qnnFailureValue
        fun backendDetails(): String = backendDetailsValue + (invocationFailureValue?.let { "; invocationFailure=$it" } ?: "")
        fun runtimeInitMs(): Long = runtimeInitMsValue
        fun lastInferenceMs(): Long = lastInferenceMsValue
        fun inputsDescription(): String = inputsDescriptionValue
        fun outputsDescription(): String = if (outputDimensionValue > 0) "$outputsDescriptionValue | out#0:FLOAT:${outputDimensionValue} elements" else outputsDescriptionValue

        override fun close() {
            BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_RUNTIME_CLOSE_BEGIN", "backend=$backendName")
            inputBuffers.forEach { runCatching { it.close() } }
            outputBuffers.forEach { runCatching { it.close() } }
            runCatching { compiledModel.close() }
            runCatching { environment?.close() }
            BenIpcDiagnosticRecorder.event(app, null, "WORKER_EMBEDDING_RUNTIME_CLOSE_PASS")
        }
    }

    private enum class Task(val prefix: String) {
        RETRIEVAL_QUERY("task: search result | query: "),
        RETRIEVAL_DOCUMENT("title: none | text: "),
        SENTENCE_SIMILARITY("task: sentence similarity | query: ")
    }

    private fun runtimeMessage(error: Throwable): String =
        "EmbeddingGemma runtime: ${detailedThrowable(error)}"

    private fun detailedThrowable(error: Throwable): String {
        val chain = buildList {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < 6) {
                add("${current.javaClass.name}: ${current.message?.take(700) ?: "no message"}")
                current = current.cause
                depth++
            }
        }
        return chain.joinToString(" <- ")
    }

    private companion object { const val TAG = "BenEmbeddingGemma"; const val RUNTIME_IDLE_TIMEOUT_MS = 120_000L }

    private fun fail(message: String) {
        BenNeuralTelemetry.error(message)
    }

    private fun cosine(a: List<Float>, b: List<Float>): Double {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0.0
        var dot = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in 0 until n) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            aa += x * x
            bb += y * y
        }
        return if (aa <= 0.0 || bb <= 0.0) 0.0 else
            (dot / (sqrt(aa) * sqrt(bb))).coerceIn(-1.0, 1.0)
    }

    private fun clean(value: String) = value.replace(Regex("\\s+"), " ").trim().take(1800)

    private fun <T> fallback(candidates: List<T>, limit: Int, started: Long) =
        Result(candidates.take(limit.coerceAtLeast(0)).map { SemanticHit(it, 0.0) }, max(0L, System.currentTimeMillis() - started), false)
}
