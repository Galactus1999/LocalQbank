package com.localqbank.library

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.util.concurrent.Executors

/** Imports an RR/reference PDF and performs bounded on-device OCR off the UI thread. */
class BenPdfImportActivity : AppCompatActivity() {
    private val picker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(uri)
    }
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ben-pdf-ocr") }
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); setBackgroundColor(ThemeManager.bg(this@BenPdfImportActivity)) }
        root.addView(TextView(this).apply { text = "BEN • REFERENCE PDF LAB"; textSize = 23f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(ThemeManager.text(this@BenPdfImportActivity)) })
        root.addView(TextView(this).apply { text = "Import RR / reference PDFs. Ben OCRs them locally and can retrieve relevant pages while teaching."; textSize = 13f; setTextColor(ThemeManager.muted(this@BenPdfImportActivity)); setPadding(0, dp(6), 0, dp(16)) })
        val pick = TextView(this).apply { text = "＋ IMPORT PDF"; gravity = android.view.Gravity.CENTER; textSize = 15f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(android.graphics.Color.WHITE); background = UiDrawableUtils.roundedDrawable(this@BenPdfImportActivity, ThemeManager.accent(this@BenPdfImportActivity), 16f); setPadding(0, dp(14), 0, dp(14)); setOnClickListener { picker.launch(arrayOf("application/pdf")) } }
        root.addView(pick)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; isIndeterminate = false }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { setMargins(0, dp(18), 0, dp(10)) })
        status = TextView(this).apply { text = "Ready • bundled OCR • local only"; textSize = 12f; setTextColor(ThemeManager.muted(this@BenPdfImportActivity)) }
        root.addView(status)
        root.addView(TextView(this).apply { text = "OCR is bounded page-by-page to avoid large PDF RAM spikes. Original PDF remains private to Rovex."; textSize = 11f; setTextColor(ThemeManager.muted(this@BenPdfImportActivity)); setPadding(0, dp(16), 0, 0) })
        setContentView(root)
    }

    private fun importPdf(uri: android.net.Uri) {
        status.text = "Preparing PDF…"; progress.progress = 0
        executor.execute {
            try {
                val input = File(cacheDir, "ben_import_${System.currentTimeMillis()}.pdf")
                contentResolver.openInputStream(uri)?.use { ins -> input.outputStream().use { out -> ins.copyTo(out) } } ?: throw IllegalStateException("Could not read PDF")
                ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val total = renderer.pageCount.coerceAtMost(400)
                        val pages = ArrayList<String>(total)
                        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                        try {
                            for (i in 0 until total) {
                                val page = renderer.openPage(i)
                                val width = 1700
                                val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                val image = InputImage.fromBitmap(bitmap, 0)
                                val latinText = runCatching { Tasks.await(latin.process(image))?.text.orEmpty() }.getOrDefault("")
                                val devText = if (latinText.length < 20) runCatching { Tasks.await(devanagari.process(image))?.text.orEmpty() }.getOrDefault("") else ""
                                pages += (if (devText.length > latinText.length) devText else latinText).trim()
                                bitmap.recycle()
                                val pct = ((i + 1) * 100 / total.coerceAtLeast(1))
                                runOnUiThread { if (!isFinishing && !isDestroyed) { progress.progress = pct; status.text = "OCR page ${i + 1}/$total • $pct%" } }
                            }
                        } finally { latin.close(); devanagari.close() }
                        val display = queryDisplayName(uri)
                        BenPdfResearchStore(this).importDocument(input, display, pages)
                    }
                }
                input.delete()
                runOnUiThread { if (!isFinishing && !isDestroyed) { status.text = "Imported successfully • Ben can now retrieve this PDF"; setResult(Activity.RESULT_OK); Toast.makeText(this, "Reference PDF imported for Ben", Toast.LENGTH_LONG).show() } }
            } catch (t: Exception) {
                inputSafeCleanup()
                runOnUiThread { if (!isFinishing && !isDestroyed) { status.text = "Import failed safely: ${t.message?.take(180) ?: "unknown error"}"; Toast.makeText(this, "PDF OCR failed; no partial corpus was published", Toast.LENGTH_LONG).show() } }
            }
        }
    }
    private fun queryDisplayName(uri: android.net.Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else "reference.pdf" } ?: "reference.pdf"
    }.getOrDefault("reference.pdf")
    private fun inputSafeCleanup() { cacheDir.listFiles()?.filter { it.name.startsWith("ben_import_") }?.forEach { runCatching { it.delete() } } }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
