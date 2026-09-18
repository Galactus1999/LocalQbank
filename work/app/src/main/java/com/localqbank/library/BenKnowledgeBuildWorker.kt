package com.localqbank.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Builds Ben's local knowledge substrate from the already-imported QBank.
 *
 * Safety properties:
 * - WorkManager/background execution only; never part of AppManagers startup.
 * - Keyset pagination keeps RAM bounded.
 * - The source QBank is read-only; only Ben's separate knowledge DB is mutated.
 * - Progress is resumable through the persisted last-row cursor.
 * - Cancellation is cooperative and leaves the partial index valid.
 */
class BenKnowledgeBuildWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val index = BenLocalKnowledgeIndex(app)
        val db = QBankDb(app)
        val clinical = ClinicalKnowledgeLayer(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            val total = runCatching { db.aiIndexQuestionCount() }.getOrDefault(0)
            if (total <= 0) {
                prefs.edit().putString("state", "EMPTY").putInt("progress", 0).apply()
                return@withContext Result.success()
            }

            var cursor = prefs.getLong("last_question_id", 0L)
            var processed = prefs.getInt("processed", 0).coerceAtLeast(0)
            prefs.edit().putString("state", "RUNNING").apply()

            while (true) {
                coroutineContext.ensureActive()
                val rows = db.aiIndexBatch(cursor, BATCH_SIZE)
                if (rows.isEmpty()) break

                val observations = rows.mapNotNull { row ->
                    val text = buildString {
                        append(row.text.take(MAX_TEXT))
                        if (row.explanation.isNotBlank()) append(" ").append(row.explanation.take(MAX_EXPLANATION))
                        if (row.testTitle.isNotBlank()) append(" ").append(row.testTitle.take(160))
                        if (row.path.isNotBlank()) append(" ").append(row.path.take(160))
                        if (row.sourceName.isNotBlank()) append(" ").append(row.sourceName.take(160))
                    }
                    val understanding = runCatching { clinical.understand(text) }.getOrNull() ?: return@mapNotNull null
                    BenLocalKnowledgeIndex.Observation(
                        questionId = row.id,
                        concepts = understanding.concepts.map { it.canonical }.take(8),
                        domains = understanding.domains
                    )
                }
                index.observeBatch(observations)
                cursor = rows.last().id
                processed += rows.size
                val percent = ((processed.toLong() * 100L) / total.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                prefs.edit().putLong("last_question_id", cursor).putInt("processed", processed).putInt("progress", percent).apply()
                setProgress(workDataOf("processed" to processed, "total" to total, "progress" to percent))
            }

            prefs.edit().putString("state", "COMPLETE").putInt("progress", 100).apply()
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            prefs.edit().putString("state", "PAUSED").apply()
            throw cancelled
        } catch (_: Exception) {
            // Ben indexing must never crash the app/process. The partial index remains valid.
            prefs.edit().putString("state", "FAILED").apply()
            Result.retry()
        } finally {
            db.close()
        }
    }

    companion object {
        const val UNIQUE_WORK = "ben_qbank_knowledge_build"
        private const val PREFS = "ben_knowledge_build"
        private const val BATCH_SIZE = 48
        private const val MAX_TEXT = 5000
        private const val MAX_EXPLANATION = 3500
    }
}


object BenKnowledgeBuildCoordinator {
    private const val PREFS = "ben_knowledge_build"

    fun start(context: Context, rebuild: Boolean = false) {
        val app = context.applicationContext
        val index = BenLocalKnowledgeIndex(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (rebuild) {
            index.reset()
            prefs.edit().clear().apply()
        }
        val request = androidx.work.OneTimeWorkRequestBuilder<BenKnowledgeBuildWorker>()
            .addTag(BenKnowledgeBuildWorker.UNIQUE_WORK)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val policy = if (rebuild) androidx.work.ExistingWorkPolicy.REPLACE else androidx.work.ExistingWorkPolicy.KEEP
        androidx.work.WorkManager.getInstance(app).enqueueUniqueWork(BenKnowledgeBuildWorker.UNIQUE_WORK, policy, request)
    }

    data class Status(val state: String, val progress: Int, val processed: Int)

    fun status(context: Context): Status {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Status(p.getString("state", "IDLE") ?: "IDLE", p.getInt("progress", 0).coerceIn(0, 100), p.getInt("processed", 0).coerceAtLeast(0))
    }
}
