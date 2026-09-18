package com.localqbank.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.Locale
import kotlin.math.min

/**
 * Local RR/reference-PDF corpus for Ben. Original PDFs stay private to the app; OCR text is
 * stored as bounded page records so deterministic retrieval can feed Ben without cloud upload.
 */
class BenPdfResearchStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "ben_pdf_corpus").apply { mkdirs() }

    data class PageHit(val document: String, val page: Int, val text: String, val score: Int)

    fun importDocument(source: File, displayName: String, pages: List<String>): File {
        val safe = sanitize(displayName).ifBlank { "reference" }
        val id = "${System.currentTimeMillis()}_${safe.take(80)}"
        val pdf = File(root, "$id.pdf")
        source.copyTo(pdf, overwrite = true)
        val txt = File(root, "$id.txt")
        txt.bufferedWriter().use { out ->
            pages.forEachIndexed { i, page ->
                out.append("--- PAGE ${i + 1} ---\n")
                out.append(page.take(60_000).replace('\u0000', ' ')).append('\n')
            }
        }
        return pdf
    }

    fun search(query: String, limit: Int = 8): List<PageHit> {
        val tokens = Regex("[A-Za-z0-9][A-Za-z0-9-]{2,}").findAll(query.lowercase(Locale.US))
            .map { it.value }.distinct().take(12).toList()
        if (tokens.isEmpty()) return emptyList()
        val hits = mutableListOf<PageHit>()
        root.listFiles { f -> f.extension.equals("txt", true) }?.forEach { txt ->
            val lines = txt.readText().split("--- PAGE ").drop(1)
            lines.forEach { block ->
                val page = Regex("^(\\d+)").find(block)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val body = block.substringAfter("---", "").trim()
                val low = body.lowercase(Locale.US)
                val score = tokens.sumOf { token ->
                    var n = 0; var at = 0
                    while (true) { val i = low.indexOf(token, at); if (i < 0) break; n++; at = i + token.length }
                    min(n, 6)
                }
                if (score > 0) hits += PageHit(txt.name.removeSuffix(".txt"), page, body.take(12_000), score)
            }
        }
        return hits.sortedByDescending { it.score }.take(limit)
    }

    fun renderPage(hit: PageHit, width: Int = 1200): Bitmap? = runCatching {
        val pdf = File(root, "${hit.document}.pdf")
        if (!pdf.exists()) return@runCatching null
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (hit.page !in 1..renderer.pageCount) return@runCatching null
                renderer.openPage(hit.page - 1).use { page ->
                    val w = width.coerceIn(600, 1800)
                    val h = (w * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }.getOrNull()

    fun describe(): String {
        val count = root.listFiles { f -> f.extension.equals("pdf", true) }?.size ?: 0
        return if (count == 0) "No reference PDFs imported" else "$count reference PDF${if (count == 1) "" else "s"} available to Ben"
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
