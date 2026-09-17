package com.localqbank.library

data class BenEmbeddingGemmaDiagnosticReport(
        val timestampMs: Long,
        val appVersion: String,
        val android: String,
        val device: String,
        val abi: String,
        val modelName: String,
        val modelBytes: Long,
        val modelSha256: String,
        val tokenizerBytes: Long,
        val tokenizerSha256: String,
        val tokenizerParseOk: Boolean,
        val tokenizerSampleTokens: Int,
        val tokenizerSpecialTokens: String,
        val modelGraphHints: String,
        val qnnAttempted: Boolean,
        val qnnAvailable: Boolean,
        val qnnFallback: Boolean,
        val qnnFailure: String?,
        val backendDetails: String,
        val backend: String,
        val inputs: String,
        val outputs: String,
        val sequenceLength: Int,
        val embeddingDimension: Int,
        val embeddingType: String,
        val embeddingFinite: Boolean,
        val embeddingNorm: Double,
        val semanticSimilarCosine: Double,
        val semanticUnrelatedCosine: Double,
        val semanticSeparation: Double,
        val runtimeInitMs: Long,
        val inferenceMs: Long,
        val totalMs: Long,
        val availableRamBeforeMb: Int,
        val availableRamAfterMb: Int,
        val javaHeapBeforeMb: Long,
        val javaHeapAfterMb: Long,
        val nativeHeapBeforeMb: Long,
        val nativeHeapAfterMb: Long,
        val thermalBefore: Int,
        val thermalAfter: Int,
        val powerSaveBefore: Boolean,
        val powerSaveAfter: Boolean,
        val contractPassed: Boolean,
        val inferencePassed: Boolean,
        val semanticSmokePassed: Boolean,
        val overallPassed: Boolean,
        val failure: String?
    ) {
        fun toText(): String = buildString {
            appendLine("BEN / EMBEDDINGGEMMA DIAGNOSTIC REPORT")
            appendLine("Generated: ${java.util.Date(timestampMs)}")
            appendLine("Overall: ${if (overallPassed) "PASS" else "FAIL / REVIEW"}")
            appendLine()
            appendLine("1. DEVICE / RUNTIME")
            appendLine("App: $appVersion")
            appendLine("Android: $android")
            appendLine("Device: $device")
            appendLine("ABI: $abi")
            appendLine("RAM available: ${availableRamBeforeMb} MB -> ${availableRamAfterMb} MB")
            appendLine("Java heap: ${javaHeapBeforeMb} MB -> ${javaHeapAfterMb} MB")
            appendLine("Native heap: ${nativeHeapBeforeMb} MB -> ${nativeHeapAfterMb} MB")
            appendLine("Thermal: $thermalBefore -> $thermalAfter")
            appendLine("Power save: $powerSaveBefore -> $powerSaveAfter")
            appendLine()
            appendLine("2. ARTIFACTS")
            appendLine("Model: $modelName (${modelBytes / (1024 * 1024)} MB)")
            appendLine("Model SHA-256: $modelSha256")
            appendLine("SentencePiece: ${tokenizerBytes / (1024 * 1024)} MB")
            appendLine("Tokenizer SHA-256: $tokenizerSha256")
            appendLine("Tokenizer parse: ${if (tokenizerParseOk) "PASS" else "FAIL"}")
            appendLine("Tokenizer sample tokens: $tokenizerSampleTokens")
            appendLine("Special tokens: $tokenizerSpecialTokens")
            appendLine("Graph hints: $modelGraphHints")
            appendLine()
            appendLine("3. MODEL GRAPH / TENSOR CONTRACT")
            appendLine("Inputs: $inputs")
            appendLine("Outputs: $outputs")
            appendLine("Sequence length: $sequenceLength")
            appendLine("Embedding dimension: $embeddingDimension")
            appendLine("Embedding type: $embeddingType")
            appendLine("Contract: ${if (contractPassed) "PASS" else "FAIL"}")
            appendLine()
            appendLine("4. BACKEND")
            appendLine("Backend selected: $backend")
            appendLine("QNN attempted: $qnnAttempted")
            appendLine("QNN available: $qnnAvailable")
            appendLine("QNN fallback to CPU/XNNPACK: $qnnFallback")
            appendLine("QNN failure: ${qnnFailure ?: "none"}")
            appendLine("Backend details: ${backendDetails.ifBlank { "none" }}")
            appendLine()
            appendLine("5. INFERENCE")
            appendLine("Inference: ${if (inferencePassed) "PASS" else "FAIL"}")
            appendLine("Finite embedding: $embeddingFinite")
            appendLine("Embedding L2 norm: ${"%.5f".format(embeddingNorm)}")
            appendLine("Runtime initialization: ${runtimeInitMs} ms")
            appendLine("Inference time: ${inferenceMs} ms")
            appendLine("Total diagnostic time: ${totalMs} ms")
            appendLine()
            appendLine("6. SEMANTIC SMOKE TEST")
            appendLine("Related pair cosine: ${"%.5f".format(semanticSimilarCosine)}")
            appendLine("Unrelated pair cosine: ${"%.5f".format(semanticUnrelatedCosine)}")
            appendLine("Separation: ${"%.5f".format(semanticSeparation)}")
            appendLine("Semantic smoke test: ${if (semanticSmokePassed) "PASS" else "REVIEW"}")
            appendLine()
            appendLine("7. FAILURE / FALLBACK")
            appendLine("Failure: ${failure ?: "none"}")
            appendLine("Deterministic Ben/RAG remains authoritative if neural inference is unavailable.")
        }
    }
