package com.localqbank.library.exam

/** Deterministic mock-exam invariants; no persistence or UI side effects. */
object MockExamPolicy {
    fun clampDurationMinutes(value: Int): Int = value.coerceIn(1, 600)
    fun scorePercent(correct: Int, attempted: Int): Int = if (attempted <= 0) 0 else ((correct.coerceAtLeast(0).coerceAtMost(attempted) * 100.0) / attempted).toInt()
    fun validBlueprint(blueprint: MockExamBlueprint): Boolean =
        blueprint.id.isNotBlank() && blueprint.title.isNotBlank() &&
            blueprint.durationMinutes in 1..600 && blueprint.questionIds.isNotEmpty() &&
            blueprint.sections.all { it.startIndex >= 0 && it.endExclusive <= blueprint.questionIds.size && it.startIndex < it.endExclusive }
}

class LocalMockExamPlanner(private val context: android.content.Context) {
    fun build(title: String, questionCount: Int, durationMinutes: Int, seed: Long = System.currentTimeMillis()): MockExamBlueprint {
        val refs = com.localqbank.library.PerformanceManager.lightRefs(context.applicationContext)
        val ids = refs.map { it.id }.distinct().shuffled(kotlin.random.Random(seed)).take(questionCount.coerceIn(1, refs.size.coerceAtLeast(1))).toLongArray()
        val duration = MockExamPolicy.clampDurationMinutes(durationMinutes)
        val sectionSize = (ids.size / 4).coerceAtLeast(1)
        val sections = ids.indices.chunked(sectionSize).take(4).mapIndexed { index, range ->
            MockExamBlueprint.Section("S${index + 1}", "Section ${index + 1}", range.first(), range.last() + 1)
        }
        return MockExamBlueprint("mock-${seed.toString(16)}", title.ifBlank { "Mock Examination" }, duration, ids, sections)
    }
}
