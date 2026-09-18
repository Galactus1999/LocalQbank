package com.localqbank.library

import android.content.Context

/** Durable, bounded diagnostic history. Stores runtime metadata/reports only; never study content. */
object RovexDiagnosticsStore {
    private const val PREFS = "rovex_diagnostics"
    private const val LATEST = "latest_report"
    private const val COUNT = "history_count"
    private const val MAX_HISTORY = 5
    private const val MAX_REPORT_CHARS = 12000
    private const val LATEST_STATUS = "latest_status"
    private const val LATEST_IPC = "latest_ipc_report"

    fun latest(context: Context): String? = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LATEST, null)

    fun latestStatus(context: Context): String? = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LATEST_STATUS, null)

    @Synchronized
    fun publish(context: Context, report: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bounded = report.take(MAX_REPORT_CHARS)
        val oldCount = prefs.getInt(COUNT, 0).coerceIn(0, MAX_HISTORY)
        val edit = prefs.edit()
        for (i in minOf(oldCount, MAX_HISTORY - 1) downTo 0) {
            prefs.getString("report_$i", null)?.let { edit.putString("report_${i + 1}", it) }
        }
        edit.putString(LATEST, bounded)
        edit.putString(LATEST_STATUS, "COMPLETE")
        edit.putString("report_0", bounded)
        edit.putInt(COUNT, minOf(oldCount + 1, MAX_HISTORY))
        edit.commit()
    }

    @Synchronized
    fun publishIpc(context: Context, report: String) {
        val bounded = report.take(MAX_REPORT_CHARS)
        val result = bounded.lineSequence()
            .firstOrNull { it.startsWith("result=") }
            ?.substringAfter('=')
            ?.trim()
            ?.uppercase()
        val status = when (result) {
            "PASS" -> "IPC_COMPLETE"
            "PARTIAL_PASS" -> "IPC_PARTIAL"
            "CANCELLED" -> "IPC_CANCELLED"
            "INTERRUPTED" -> "IPC_INTERRUPTED"
            "FAIL", "FAILED" -> "IPC_FAILED"
            else -> "IPC_REVIEW"
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LATEST_IPC, bounded)
            .putString(LATEST, bounded)
            .putString(LATEST_STATUS, status)
            .commit()
    }

    fun latestIpc(context: Context): String? = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LATEST_IPC, null)

    @Synchronized
    fun publishFailure(context: Context, reason: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LATEST_STATUS, "FAILED: ${reason.take(500)}")
            .apply()
    }

    fun history(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(COUNT, 0).coerceIn(0, MAX_HISTORY)
        return (0 until count).mapNotNull { prefs.getString("report_$it", null) }
    }
}
