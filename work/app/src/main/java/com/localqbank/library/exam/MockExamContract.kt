package com.localqbank.library.exam

/** Pure domain contract for the future mock-examination engine; no UI or database ownership. */
data class MockExamBlueprint(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val questionIds: LongArray,
    val sections: List<Section>
) {
    data class Section(val id: String, val label: String, val startIndex: Int, val endExclusive: Int)
}

data class MockExamResult(
    val examId: String,
    val attempted: Int,
    val correct: Int,
    val scorePercent: Int,
    val elapsedMs: Long,
    val sectionResults: List<SectionResult>
) {
    data class SectionResult(val sectionId: String, val attempted: Int, val correct: Int)
}
