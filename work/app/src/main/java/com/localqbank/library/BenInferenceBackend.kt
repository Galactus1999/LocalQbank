package com.localqbank.library

/**
 * Narrow adapter boundary for Ben's future on-device inference runtime.
 * Implementations must remain offline unless a future product decision explicitly
 * permits another transport. The stability build ships only the unavailable backend.
 */
interface BenInferenceBackend {
    val isAvailable: Boolean

    /** Approximate resident model size in MB; 0 means the backend has no safe estimate. */
    val estimatedModelMb: Int get() = 0

    fun answer(prompt: String): String?

    /** Optional native/runtime cleanup hook. Default is a no-op for lightweight backends. */
    fun close() {}
}
