package com.localqbank.library

/** Deterministic bounds and hygiene for text returned by an optional local Ben backend. */
object BenResponsePolicy {
    const val MAX_RESPONSE_CHARS = 16_384

    fun normalize(raw: String?): String? = BoundedTextPolicy.normalize(raw, MAX_RESPONSE_CHARS)
}
