package com.localqbank.library

import android.content.Context

/**
 * Presentation-facing persistence seam for Home.
 * QBankDb/ProgressStore remain the authoritative storage implementations.
 */
class MainRepository(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val db = QBankDb(app)
    private val progress = ProgressStore(app)

    data class ResumeTarget(val testId: String, val position: Int, val title: String)

    fun questionExists(id: Long): Boolean = db.questionExists(id)
    fun progressSnapshot(): ProgressSnapshot = PerformanceManager.progress(app)
    fun testById(id: String): Test? = db.testById(id)
    fun sourceResume(sourceId: Long): String? = progress.sourceResume(sourceId)
    fun flushBackup() = BackupManager(app).flushCloudBackup()
    fun sourceResumeByName(sourceName: String): String? = progress.sourceResumeByName(sourceName)

    fun resumeTarget(source: Source): ResumeTarget? {
        val raw = sourceResumeByName(source.fileName) ?: sourceResume(source.id) ?: return null
        val parts = raw.split("|", limit = 2)
        val testId = parts.getOrNull(0).orEmpty()
        if (testId.isBlank()) return null
        val test = db.testById(testId) ?: return null
        return ResumeTarget(test.id, parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, (test.count - 1).coerceAtLeast(0)) ?: 0, test.title)
    }

    fun resumeTargets(sources: List<Source>): Map<Long, ResumeTarget> = buildMap {
        sources.forEach { source -> resumeTarget(source)?.let { put(source.id, it) } }
    }

    fun deleteSource(sourceId: Long) = db.deleteSource(sourceId)

    fun updateSourceMetadata(sourceId: Long, name: String, series: String): Boolean =
        db.updateSourceMetadata(sourceId, name, series)

    override fun close() {
        db.close()
    }
}
