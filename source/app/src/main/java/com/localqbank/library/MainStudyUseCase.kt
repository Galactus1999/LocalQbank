package com.localqbank.library

/**
 * Home study-queue policy. It computes collections/queues but never starts UI activities.
 * PerformanceManager/AppManagers remain the authoritative progress/adaptive owners.
 */
class MainStudyUseCase(private val repository: MainRepository) {
    private fun due(state: ProgressRecord?, now: Long): Boolean {
        return state?.status != null && (
            (state.nextDue > 0L && now >= state.nextDue) ||
                (state.nextDue == 0L && state.lastAttempted > 0L && now - state.lastAttempted >= when (state.attempts) {
                    1 -> 86_400_000L
                    2 -> 3 * 86_400_000L
                    3 -> 7 * 86_400_000L
                    4 -> 14 * 86_400_000L
                    else -> 30 * 86_400_000L
                })
        )
    }

    fun currentQBankRefs(allRefs: List<QuestionRef>): List<QuestionRef> {
        if (allRefs.isEmpty()) return emptyList()
        val session = AppManagers.sessionStore.current()
        val currentSourceId = session?.testId?.let { id -> allRefs.firstOrNull { it.testId == id }?.sourceId } ?: 0L
        val sourceId = if (currentSourceId > 0L) currentSourceId else allRefs.firstOrNull()?.sourceId ?: return allRefs
        return allRefs.filter { it.sourceId == sourceId }
    }

    fun wrongIds(refs: List<QuestionRef>): LongArray {
        val progress = repository.progressSnapshot()
        return refs.filter { progress.record(it.stableKey)?.status == "wrong" }
            .sortedByDescending { progress.record(it.stableKey)?.attempts ?: 0 }
            .take(100).map { it.id }.toLongArray()
    }

    fun todaySolvedIds(refs: List<QuestionRef>): LongArray =
        AppManagers.todaySolved.ids(refs, 200).filter { runCatching { repository.questionExists(it) }.getOrDefault(false) }.toLongArray()

    fun todayRevisionIds(refs: List<QuestionRef>): LongArray {
        val progress = repository.progressSnapshot()
        val now = System.currentTimeMillis()
        fun candidate(r: QuestionRef): Boolean {
            val state = progress.record(r.stableKey)
            return due(state, now) || state?.status == "wrong" || !state?.bookmark.isNullOrBlank()
        }
        val all = refs.filter(::candidate).distinctBy { it.id }
        if (all.isEmpty()) return LongArray(0)
        val duePool = all.filter { due(progress.record(it.stableKey), now) }
        val pool = if (duePool.isNotEmpty()) duePool else all
        val day = java.time.LocalDate.now().toEpochDay()
        return pool.shuffled(java.util.Random(day xor 0x52_4F_56_45_58L)).take(100).map { it.id }.toLongArray()
    }

    fun todayRevisionCount(refs: List<QuestionRef>): Int {
        val progress = repository.progressSnapshot()
        val now = System.currentTimeMillis()
        return refs.count { r ->
            val state = progress.record(r.stableKey)
            due(state, now) || state?.status == "wrong" || !state?.bookmark.isNullOrBlank()
        }
    }
}
