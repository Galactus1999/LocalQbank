package com.localqbank.library

/**
 * Central feature policy for edge inference optimizations. Unsupported optimizations remain
 * disabled rather than being simulated by application code.
 */
object BenInferenceEfficiencyPolicy {
    data class Snapshot(
        val speculativeDecoding: Boolean,
        val promptWindowChars: Int,
        val zeroCopyTensorIpc: Boolean,
        val runtimeOwnsKvCache: Boolean
    )

    fun snapshot(): Snapshot = Snapshot(
        // LiteRT-LM speculative/MTP is model-format dependent. Rovex enables it only after a
        // model capability probe; the current Gemma 3 270M artifact is therefore left off.
        speculativeDecoding = false,
        promptWindowChars = 8_000,
        // AHardwareBuffer is reserved for large tensor payloads. Our 768-float embedding is only
        // ~3 KB, so Binder copy avoidance is not worth native complexity on the hot path.
        zeroCopyTensorIpc = false,
        runtimeOwnsKvCache = true
    )
}
