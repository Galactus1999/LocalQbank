package com.localqbank.library

/** Deterministic no-model backend used until a validated local model is installed. */
object UnavailableBenInferenceBackend : BenInferenceBackend {
    override val isAvailable: Boolean = false
    override fun answer(prompt: String): String? = null
}
