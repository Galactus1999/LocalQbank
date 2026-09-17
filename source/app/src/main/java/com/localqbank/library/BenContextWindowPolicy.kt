package com.localqbank.library

/**
 * Bounded context policy for Ben's edge inference path.
 *
 * This is intentionally a prompt-level sliding window. LiteRT-LM owns the actual KV cache;
 * Rovex must not pretend it can quantize or mutate that private cache unless the runtime exposes
 * a supported API. We therefore bound the text entering the runtime and preserve the highest-value
 * evidence items first.
 */
object BenContextWindowPolicy {
    data class Window(
        val text: String,
        val estimatedChars: Int,
        val evidenceKept: Int,
        val truncated: Boolean
    )

    private const val MAX_CHARS = 8_000
    private const val EVIDENCE_ITEM_MAX = 900

    fun build(
        query: String,
        examContext: String,
        evidence: List<String>,
        maxChars: Int = MAX_CHARS
    ): Window {
        val cap = maxChars.coerceIn(2_000, MAX_CHARS)
        val out = StringBuilder(cap)
        out.append("Clinical study query:\n").append(query.trim()).append("\n\n")
        out.append("Exam-language context:\n").append(examContext.trim().take(1_200)).append("\n\n")
        out.append("Local QBank evidence:\n")
        var kept = 0
        for (item in evidence) {
            val line = item.trim().take(EVIDENCE_ITEM_MAX)
            if (line.isBlank()) continue
            val candidate = "${line}\n"
            if (out.length + candidate.length > cap) break
            out.append(candidate)
            kept++
        }
        return Window(out.toString(), out.length, kept, kept < evidence.size)
    }
}
