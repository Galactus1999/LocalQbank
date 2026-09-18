package com.localqbank.library

import android.content.Context

/**
 * Import persistence seam. HtmlImportActivity must not own QBankDb directly.
 * The importer coordinator owns scheduling; this class owns one operation's DB handle.
 */
class HtmlImportRepository(context: Context) : AutoCloseable {
    private val db = QBankDb(context.applicationContext)

    fun importBundle(
        fileName: String,
        provider: String,
        tests: List<ImportedTest>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = db.importBundle(fileName, provider, tests, onProgress)

    fun beginStreamingImport(fileName: String, provider: String) =
        db.beginStreamingImport(fileName, provider)

    override fun close() = db.close()
}
