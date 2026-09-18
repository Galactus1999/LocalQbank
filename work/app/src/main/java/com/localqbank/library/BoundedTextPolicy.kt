package com.localqbank.library

/**
 * Shared bounded text normalisation used at AI/input boundaries.
 *
 * It deliberately bounds the amount of text scanned, removes unsafe control
 * characters, canonicalises whitespace, and never emits an isolated UTF-16
 * surrogate. Keeping this policy pure makes it easy to test without Android.
 */
object BoundedTextPolicy {
    fun normalize(raw: String?, maxChars: Int): String? {
        if (raw.isNullOrEmpty() || maxChars <= 0) return null
        val bounded = truncateSafely(raw, maxChars)
        val clean = buildString(bounded.length) {
            var pendingSpace = false
            var index = 0
            while (index < bounded.length) {
                val ch = bounded[index]
                when {
                    Character.isHighSurrogate(ch) -> {
                        if (index + 1 < bounded.length && Character.isLowSurrogate(bounded[index + 1])) {
                            if (pendingSpace && isNotEmpty()) append(' ')
                            pendingSpace = false
                            append(ch)
                            append(bounded[index + 1])
                            index += 2
                            continue
                        }
                        // Drop an isolated high surrogate rather than emitting malformed UTF-16.
                    }
                    Character.isLowSurrogate(ch) -> {
                        // Drop an isolated low surrogate.
                    }
                    ch.isISOControl() && ch != '\n' && ch != '\t' -> pendingSpace = true
                    ch.isWhitespace() -> pendingSpace = true
                    else -> {
                        if (pendingSpace && isNotEmpty()) append(' ')
                        pendingSpace = false
                        append(ch)
                    }
                }
                index++
            }
        }.trim()
        return truncateSafely(clean, maxChars).trimEnd().takeIf { it.isNotEmpty() }
    }

    fun truncateSafely(value: String, maxChars: Int): String {
        require(maxChars >= 0) { "maxChars must be non-negative" }
        if (value.length <= maxChars) return value
        var end = maxChars
        if (end > 0 && end < value.length &&
            Character.isHighSurrogate(value[end - 1]) && Character.isLowSurrogate(value[end])) {
            end--
        }
        return value.substring(0, end)
    }
}
