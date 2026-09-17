package com.localqbank.library

/** Pure presentation policy; deliberately does not cap session counts at 100. */
object FlashcardProgressLabel {
    fun counter(position: Int, total: Int): String {
        val safeTotal = total.coerceAtLeast(0)
        if (safeTotal == 0) return "0 / 0"
        val safePosition = position.coerceIn(0, safeTotal - 1)
        return "${safePosition + 1} / $safeTotal"
    }

    fun percentage(position: Int, total: Int): Int {
        if (total <= 0) return 0
        return (((position + 1).coerceIn(0, total).toDouble() / total) * 100.0).toInt().coerceIn(0, 100)
    }
}
