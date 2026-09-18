package com.localqbank.library

/** Deterministic, allocation-bounded input policy for Ben's local research gateway. */
object BenResearchInputPolicy {
    const val MAX_QUERY_CHARS = 4096

    fun normalize(raw: String): String? = BoundedTextPolicy.normalize(raw, MAX_QUERY_CHARS)
}
