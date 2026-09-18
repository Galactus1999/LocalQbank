package com.localqbank.library

/**
 * Pure presentation-state calculation for a single sub-QBank row.
 *
 * This is deliberately independent of Android Views, colors, resources, and database access.
 * TestAdapter remains responsible only for rendering this state. Keeping the calculation here
 * makes the progress/display rules testable without changing layout IDs or visual resources.
 */
data class TestRowPresentation(
    val title: String,
    val subtitle: String,
    val showActions: Boolean,
    val resumePosition: Int,
    val inProgress: Boolean
)

object TestListPresentation {
    fun present(
        index: Int,
        test: Test,
        progress: ProgressSummary,
        storedPosition: Int
    ): TestRowPresentation {
        val safeTotal = progress.total.coerceAtLeast(0)
        val safeSolved = progress.solved.coerceIn(0, safeTotal)
        val timing = if (test.duration > 0) "  •  ${test.duration / 60} min" else ""
        val series = if (test.seriesNumber.isBlank()) "" else "  •  Series ${test.seriesNumber}"
        val subtitle = when {
            safeTotal == 0 -> "No questions"
            safeSolved >= safeTotal -> "✓ Completed  •  $safeSolved/$safeTotal solved$timing$series"
            safeSolved == 0 -> "Not started  •  $safeTotal questions$timing$series"
            else -> "↻ In progress  •  $safeSolved/$safeTotal solved$timing$series"
        }
        val inProgress = safeSolved > 0 && safeSolved < safeTotal
        return TestRowPresentation(
            title = "${index + 1}. ${test.title}",
            subtitle = subtitle,
            showActions = safeTotal > 0,
            resumePosition = if (safeSolved > 0) storedPosition else progress.resumePosition,
            inProgress = inProgress
        )
    }
}
