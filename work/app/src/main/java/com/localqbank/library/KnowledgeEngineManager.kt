package com.localqbank.library

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors

/**
 * Local knowledge capture engine. Generated notes are derived artifacts; original
 * QBank content is never modified. Tables are stored as source HTML/text and images
 * are copied into app-private storage with a small metadata index.
 */
class KnowledgeEngineManager(context: Context) {
    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "knowledge-engine") }
    private val root = File(app.filesDir, "knowledge").apply { mkdirs() }
    private val notesDir = File(root, "notes").apply { mkdirs() }
    private val tablesDir = File(root, "tables").apply { mkdirs() }
    private val imagesDir = File(root, "images").apply { mkdirs() }
    private val noteImagesDir = File(root, "notes/images").apply { mkdirs() }

    data class Result(val noteCreated: Boolean, val tablesSaved: Int, val imagesSaved: Int)

    fun captureQuestionAsync(question: Question, reason: String, onDone: ((Result) -> Unit)? = null) {
        executor.execute {
            val result = captureQuestion(question, reason)
            if (onDone != null) android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(result) }
        }
    }

    fun captureQuestion(question: Question, reason: String): Result = runCatching {
        val safeId = question.id.toString()
        val explanation = question.explanation.orEmpty()
        val answer = question.correctAnswer ?: question.options.firstOrNull { it.correct }?.label.orEmpty()
        val note = buildString {
            append("# ${plain(question.text).take(220)}\n\n")
            append("Source: Rovex • Question ${question.id}\n")
            append("Capture: ${reason.take(40)}\n\n")
            if (answer.isNotBlank()) append("## Answer\n${plain(answer)}\n\n")
            val exp = plain(explanation)
            val points = RenCognitiveEngine(app).extractKeyPoints(exp)
            if (points.isNotEmpty()) {
                append("## Key points\n")
                points.take(7).forEach { append("• ").append(it).append('\n') }
            } else if (exp.isNotBlank()) {
                append("## Key explanation\n${exp.take(1200)}\n")
            }
            append("\n## Exam cue\nRevisit the original question and explanation before relying on this generated summary.\n")
        }
        val noteFile = File(notesDir, "q_$safeId.txt")
        val old = if (noteFile.exists()) noteFile.readText() else ""
        if (old != note) noteFile.writeText(note)
        // Surface the generated summary through the existing Notes screen without
        // overwriting a user's manual note. Re-running the engine replaces only its own block.
        val qdb = QBankDb(app)
        try {
            val manual = qdb.note(question.id).orEmpty()
            val marker = "[Rovex Auto Summary]"
            val generatedBlock = "$marker\n${note.substringAfter("\n\n", note).trim()}"
            val merged = if (manual.isBlank()) generatedBlock else if (manual.contains(marker)) manual.substringBefore(marker).trimEnd() + "\n\n" + generatedBlock else manual.trimEnd() + "\n\n" + generatedBlock
            qdb.saveNote(question.id, merged)
        } finally { qdb.close() }

        var tables = 0
        val tableRx = Regex("<table\\b[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        tableRx.findAll(explanation).take(8).forEachIndexed { index, m ->
            File(tablesDir, "q_${safeId}_$index.html").writeText(m.value)
            tables++
        }

        var images = 0
        (question.questionImages + question.explanationImages).distinct().take(8).forEachIndexed { index, src ->
            if (copyImage(src, File(imagesDir, "q_${safeId}_$index"))) images++
        }
        Result(true, tables, images)
    }.getOrDefault(Result(false, 0, 0))




    fun saveTextNote(questionId: Long, title: String, text: String): Boolean = runCatching {
        val clean = text.replace("\u0000", " ").trim().take(24_000)
        if (clean.isBlank()) return@runCatching false
        val qdb = QBankDb(app)
        try {
            val old = qdb.note(questionId).orEmpty().trim()
            val block = "[Rovex Saved • ${title.take(80)}]\n$clean"
            qdb.saveNote(questionId, if (old.isBlank()) block else old + "\n\n" + block)
        } finally { qdb.close() }
        true
    }.getOrDefault(false)

    fun saveRenderedViewWithNote(view: android.view.View, questionId: Long, title: String, text: String): Boolean = runCatching {
        if (!saveTextNote(questionId, title, text)) return@runCatching false
        val file = captureExplanationView(view, questionId) ?: return@runCatching true
        val qdb = QBankDb(app)
        try { qdb.addNoteImage(questionId, file.absolutePath) } finally { qdb.close() }
        true
    }.getOrDefault(false)

    fun saveImageAsync(raw:String, questionId:Long, onDone:((Boolean)->Unit)?=null) {
        executor.execute {
            val ok = saveImage(raw, questionId)
            if (onDone != null) android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(ok) }
        }
    }

    fun saveImage(raw:String, questionId:Long):Boolean = runCatching {
        val safe=questionId.toString()
        val existing=imagesDir.listFiles { f -> f.name.startsWith("q_${safe}_") }?.size ?: 0
        copyImage(raw, File(imagesDir, "q_${safe}_${System.currentTimeMillis()}_${existing}"))
    }.getOrDefault(false)

    /** Save an image and bind it to the question's Notes entry. The original QBank image is untouched. */
    fun saveImageWithNote(raw:String, questionId:Long, note:String):Boolean = runCatching {
        val safe=questionId.toString()
        val existing=noteImagesDir.listFiles { f -> f.name.startsWith("q_${safe}_") }?.size ?: 0
        val base=File(noteImagesDir, "q_${safe}_${System.currentTimeMillis()}_${existing}")
        if (!copyImage(raw, base)) return@runCatching false
        val saved = File(base.parentFile, base.name + imageExtension(raw.trim().replace("\\/", "/").replace("&amp;", "&")))
        val qdb=QBankDb(app)
        try {
            val old=qdb.note(questionId).orEmpty().trim()
            val cleanNote = note.trim()
            // Explicitly mark this as a user image note so NotesActivity renders the
            // attachment as part of the note, never as a Knowledge Vault-only artifact.
            val noteBlock = if (cleanNote.isBlank()) "[Image note]" else "[Image note]\n$cleanNote"
            qdb.saveNote(questionId, if(old.isBlank()) noteBlock else old + "\n\n" + noteBlock)
            if (saved.exists()) qdb.addNoteImage(questionId, saved.absolutePath) else return@runCatching false
        } finally { qdb.close() }
        true
    }.getOrDefault(false)

    fun saveImageWithNoteAsync(raw:String, questionId:Long, note:String, onDone:((Boolean)->Unit)?=null) {
        executor.execute {
            val ok=saveImageWithNote(raw, questionId, note)
            if(onDone!=null) android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(ok) }
        }
    }

    fun deleteDerivedFile(file:File):Boolean = runCatching {
        file.canonicalPath.startsWith(root.canonicalPath + File.separator) && (!file.exists() || file.delete())
    }.getOrDefault(false)

    data class EngineStats(val notes:Int, val tables:Int, val images:Int)
    fun stats(): EngineStats = runCatching {
        EngineStats(notesDir.listFiles()?.count { it.isFile } ?: 0, tablesDir.listFiles()?.count { it.isFile } ?: 0, imagesDir.listFiles()?.count { it.isFile } ?: 0)
    }.getOrDefault(EngineStats(0,0,0))

    /** Captures only the currently rendered explanation surface. The view is never persisted beyond the derived image file. */
    fun captureExplanationView(view: android.view.View, questionId: Long): File? = runCatching {
        if (view.width <= 0 || view.height <= 0) return@runCatching null
        val maxW = 2400
        val scale = if (view.width > maxW) maxW.toFloat() / view.width else 1f
        val w = (view.width * scale).toInt().coerceAtLeast(1)
        val h = (view.height * scale).toInt().coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(scale, scale)
        view.draw(canvas)
        val file = File(imagesDir, "explanation_${questionId}_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
    }.getOrNull()

    fun noteFile(questionId: Long): File = File(notesDir, "q_${questionId}.txt")
    fun tablesFor(questionId: Long): List<File> = tablesDir.listFiles { f -> f.name.startsWith("q_${questionId}_") }?.sortedBy { it.name } ?: emptyList()
    fun imagesFor(questionId: Long): List<File> = imagesDir.listFiles { f -> f.name.startsWith("q_${questionId}_") }?.sortedBy { it.name } ?: emptyList()

    private fun copyImage(raw: String, base: File): Boolean = runCatching {
        val uri = raw.trim().replace("\\/", "/").replace("&amp;", "&")
        val out = File(base.parentFile, base.name + imageExtension(uri))
        val input: InputStream = when {
            uri.startsWith("data:image", true) -> {
                val comma = uri.indexOf(',')
                if (comma < 0) return@runCatching false
                android.util.Base64InputStream(uri.substring(comma + 1).byteInputStream(Charsets.US_ASCII), android.util.Base64.DEFAULT)
            }
            uri.startsWith("https://", true) -> URL(uri).openConnection().apply { connectTimeout = 8000; readTimeout = 12000 }.getInputStream()
            uri.startsWith("content:", true) || uri.startsWith("file:", true) -> app.contentResolver.openInputStream(Uri.parse(uri)) ?: return@runCatching false
            else -> return@runCatching false
        }
        input.use { ins ->
            out.outputStream().use { os ->
                val buffer = ByteArray(16 * 1024); var total = 0L; var n: Int
                while (ins.read(buffer).also { n = it } > 0) {
                    total += n
                    if (total > 8L * 1024L * 1024L) return@runCatching false
                    os.write(buffer, 0, n)
                }
            }
        }
        true
    }.getOrDefault(false)

    private fun imageExtension(uri: String): String = when {
        uri.contains(".png", true) -> ".png"
        uri.contains(".webp", true) -> ".webp"
        uri.contains(".gif", true) -> ".gif"
        else -> ".jpg"
    }

    private fun plain(html: String): String = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
}
