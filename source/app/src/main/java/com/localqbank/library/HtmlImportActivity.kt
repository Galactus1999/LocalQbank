package com.localqbank.library

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import java.io.*
import java.nio.charset.Charset
import java.util.LinkedHashMap
import java.util.regex.Pattern

class HtmlImportActivity : Activity() {
    private companion object { const val LARGE_FILE_LIMIT = 40L * 1024L * 1024L }
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressPercent: TextView
    private lateinit var sectionList: LinearLayout
    private lateinit var web: WebView
    private val files = mutableListOf<Uri>()
    private var index = 0
    private var lastSrcdocCount = 0

    override fun onDestroy() {
        // WebView owns Chromium resources outside the Activity heap; explicitly tear it down
        // so repeated large imports cannot retain a renderer/client reference after navigation.
        if (::web.isInitialized) {
            runCatching { web.stopLoading() }
            runCatching { web.clearHistory() }
            runCatching { (web.parent as? ViewGroup)?.removeView(web) }
            runCatching { web.destroy() }
        }
        super.onDestroy()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        SystemUi.immersive(this)
        AppManagers.initialize(applicationContext)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 18) }
        val title = RovexWaveTextView(this).apply { text = "Import HTML QBank"; textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        SystemUi.padTopInset(title, 18, 8, 0, 0)
        root.addView(title)
        status = TextView(this).apply { setPadding(0, 12, 0, 8); textSize=13.5f; setTextColor(ThemeManager.text(this@HtmlImportActivity)) }
        root.addView(status)
        val progressRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        progress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; progress=0 }
        progressPercent=TextView(this).apply{text="0%";textSize=12f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(this@HtmlImportActivity));gravity=Gravity.CENTER}
        progressRow.addView(progress,LinearLayout.LayoutParams(0,dp(10),1f).apply{setMargins(0,0,dp(10),0)})
        progressRow.addView(progressPercent,LinearLayout.LayoutParams(dp(44),dp(28)))
        root.addView(progressRow)
        sectionList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(8),0,dp(8))}
        val sectionScroll=ScrollView(this).apply{isFillViewport=false;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
        sectionScroll.addView(sectionList)
        root.addView(sectionScroll,LinearLayout.LayoutParams(-1,dp(180)))
        val frame = FrameLayout(this)
        web = WebView(this).apply { layoutParams = FrameLayout.LayoutParams(1, 1) }
        frame.addView(web)
        root.addView(frame, LinearLayout.LayoutParams(1,1))
        setContentView(root)
        TransitionCoordinator.install(this)
        val incoming = intent.getParcelableArrayListExtra<Uri>("uris")
        if (incoming != null) files.addAll(incoming)
        // Also accept HTML files opened directly from a file manager/browser via ACTION_VIEW.
        intent.data?.let { if (!files.contains(it)) files.add(it) }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                val u = clip.getItemAt(i).uri
                if (!files.contains(u)) files.add(u)
            }
        }
        if (files.isEmpty()) { finish(); return }
        // Keep access to selected document URIs after this Activity is destroyed/recreated.
        // Without this, persisted question images can become unreadable later, especially
        // for DAMS/QBank files selected through the Storage Access Framework.
        files.forEach { u ->
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { /* Provider may not support persistable permissions. */ }
        }
        configureWeb()
        processNext()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWeb() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.settings.allowFileAccessFromFileURLs = false
        web.settings.allowUniversalAccessFromFileURLs = false
        web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) web.settings.safeBrowsingEnabled = true
        android.webkit.CookieManager.getInstance().setAcceptCookie(false)
        web.addJavascriptInterface(Bridge(), "AndroidImport")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, request: android.webkit.WebResourceRequest?): Boolean = true
            override fun onPageFinished(v: WebView?, url: String?) { v?.evaluateJavascript(HtmlImportWebExtractor.EXTRACT_JS, null) }
        }
    }


    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    private fun setImportProgress(value:Int, label:String?=null){
        runOnUiThread{
            if(isFinishing||isDestroyed)return@runOnUiThread
            val p=value.coerceIn(0,100);progress.progress=p;progressPercent.text="$p%";if(!label.isNullOrBlank())status.text=label
        }
    }

    private fun addSectionProgress(title:String, percent:Int, detail:String){
        runOnUiThread{
            if(isFinishing||isDestroyed)return@runOnUiThread
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@HtmlImportActivity));cornerRadius=dp(12).toFloat()}}
            row.addView(TextView(this@HtmlImportActivity).apply{text=title;textSize=13f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@HtmlImportActivity));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
            val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val bar=ProgressBar(this@HtmlImportActivity,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=percent.coerceIn(0,100)}
            line.addView(bar,LinearLayout.LayoutParams(0,dp(7),1f).apply{setMargins(0,dp(5),dp(8),0)})
            line.addView(TextView(this@HtmlImportActivity).apply{text="${percent.coerceIn(0,100)}%";textSize=10.5f;setTextColor(ThemeManager.accent(this@HtmlImportActivity));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(40),dp(24)))
            row.addView(line)
            row.addView(TextView(this@HtmlImportActivity).apply{text=detail;textSize=10.5f;setTextColor(ThemeManager.text(this@HtmlImportActivity));setPadding(0,dp(3),0,0)})
            sectionList.addView(row,0,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(3),0,dp(3))})
        }
    }

    private fun addImportEvent(title:String, detail:String){
        addSectionProgress(title, 100, detail)
    }

    private fun updateImportDatabaseProgress(done:Int,total:Int){
        val pct = 45 + ((done.toDouble() / total.coerceAtLeast(1).toDouble()) * 50.0).toInt().coerceIn(0,50)
        setImportProgress(pct, "Writing questions to local database • $done/$total")
        if(done == 1 || done % 100 == 0 || done == total) addImportEvent("Database import", "$done/$total questions committed safely")
    }

    private fun commitBundle(
        fileName: String,
        provider: String,
        tests: List<ImportedTest>
    ): Int {
        val repository = (application as LocalQBankApplication).appContainer.newHtmlImportRepository()
        return try {
            repository.importBundle(fileName, provider, tests) { done, total ->
                updateImportDatabaseProgress(done, total)
            }
        } finally {
            repository.close()
        }
    }

    private fun processNext() {
        if (index >= files.size) {
            progress.visibility = View.GONE
            status.text = "Import complete"
            Toast.makeText(this, "Imported ${files.size} HTML file(s)", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
            return
        }
        val uri = files[index]
        if (!ImportSecurityPolicy.acceptsImportUri(uri.toString())) {
            status.text = "Skipped: unsupported import URI scheme."
            index++
            processNext()
            return
        }
        val size = fileSize(uri)
        if (!ImportSecurityPolicy.acceptsSize(size)) {
            status.text = "Skipped: file exceeds the safe import limit (${ImportSecurityPolicy.MAX_HTML_BYTES / (1024L * 1024L)} MB)."
            index++
            processNext()
            return
        }
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress=0;progressPercent.text="0%";sectionList.removeAllViews()
        status.text = "Preparing ${index + 1}/${files.size}…"
        val accepted = AppManagers.importer.enqueue(displayName(uri), size, work = {
            try {
                // Marrow/offline combined exports are streamed directly from the content
                // URI. This avoids loading a 20–40+ MB outer HTML document into RAM and,
                // more importantly, avoids scanning thousands of unrelated JS assignments.
                if (looksLikeSrcdocExport(uri)) {
                    setImportProgress(8, "Scanning embedded QBank sections…")
                    val marrowTests = parseMarrowSrcdocsFromUri(uri) { sectionNo, title, questionCount ->
                        addSectionProgress(title, 100, "Section $sectionNo • $questionCount questions parsed")
                        setImportProgress((8 + (lastSrcdocCount.coerceAtLeast(1) * 2)).coerceAtMost(40), "Parsed section $sectionNo • $questionCount questions")
                    }
                    val marrowCount = marrowTests.sumOf { it.questions.size }
                    if (marrowCount > 0) {
                        val sourceName = displayName(uri)
                        val sectionCount = marrowTests.size
                        val expectedSections = lastSrcdocCount
                        val emptySections = marrowTests.count { it.questions.isEmpty() }
                        if (sectionCount >= 1 && emptySections == 0 && (expectedSections == 0 || sectionCount >= expectedSections)) {
                            setImportProgress(42, "Parsed $marrowCount questions • preparing database import…")
                            val count = commitBundle(sourceName, HtmlImportParser.detectProvider(marrowTests), marrowTests)
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    setImportProgress(100, "Imported $count questions in $sectionCount section(s)")
                                    index++
                                }
                            }
                            return@enqueue
                        }
                    }
                }

                if (size >= LARGE_FILE_LIMIT) {
                    importLargeFile(uri, size)
                } else {
                    setImportProgress(12, "Reading QBank into bounded parser…")
                    val text = readSmall(uri)
                    setImportProgress(28, "Parsing QBank structure…")
                    val nativeTests = HtmlImportParser.parseCombinedHtmlNative(text, uri.toString()).map { test ->
                        test.copy(questions = test.questions.map { q -> HtmlImportParser.resolveQuestionMedia(q, uri.toString()) })
                    }
                    val nativeCount = nativeTests.sumOf { it.questions.size }
                    setImportProgress(45, "Parsed $nativeCount questions in ${nativeTests.size} section(s) • validating import…")
                    addImportEvent("Parser", "Structure parsed: $nativeCount questions across ${nativeTests.size} section(s)")
                    val suspiciousPartial = size >= 128L * 1024L && nativeCount in 1..4
                    if (nativeCount > 4 || (nativeCount > 0 && !suspiciousPartial)) {
                        setImportProgress(48, "Writing $nativeCount parsed questions to local database…")
                        val count = commitBundle(displayName(uri), HtmlImportParser.detectProvider(nativeTests), nativeTests)
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                status.text = "Imported $count questions in ${nativeTests.size} section(s)"
                                index++
                            }
                        }
                    } else {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                progress.isIndeterminate=true;progressPercent.text="…"
                                status.text = if (suspiciousPartial) "Native parser found only $nativeCount questions — switching to full HTML/JavaScript extraction…" else "Extracting ${index + 1}/${files.size}…"
                                web.loadDataWithBaseURL(uri.toString(), text, "text/html", "UTF-8", null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }, onSuccess = {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) processNext()
            }
        }, onFailure = { error ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    status.text = "Import failed: ${error.message ?: "unknown error"}"
                    Toast.makeText(this@HtmlImportActivity, error.message ?: "Import failed", Toast.LENGTH_LONG).show()
                    index++
                    processNext()
                }
            }
        })
        if (!accepted) {
            status.text = "Another import is already running. Please wait…"
        }
    }

    private fun importLargeFile(uri: Uri, size: Long) {
        runOnUiThread { status.text = "Large file (${formatMb(size)} MB) — importing without loading it into memory…" }
        val tmp = File(cacheDir, "q_import_${System.currentTimeMillis()}.html")
        copyToFile(uri, tmp) { done -> setImportProgress(5 + done * 20 / 100, "Staging large QBank safely… $done%") }
        setImportProgress(28, "Streaming questions from staged QBank…")
        addImportEvent("Streaming engine", "Large-file mode active: parsing and writing incrementally")
        val repository = (application as LocalQBankApplication).appContainer.newHtmlImportRepository()
        val session = repository.beginStreamingImport(displayName(uri), "HTML QBank")
        var imported = 0
        var currentSection: String? = null
        try {
            LargeHtmlScanner(tmp) { section, q ->
                if (currentSection != null && currentSection != section) session.commitSection()
                currentSection = section
                session.addQuestion(section, HtmlImportParser.resolveQuestionMedia(q, uri.toString()))
                imported++
                if (imported % 50 == 0) setImportProgress((30 + imported / 50).coerceAtMost(92), "Streaming/importing questions • $imported imported")
            }.scan()
            session.finish(true)
            runOnUiThread {
                setImportProgress(100, "Imported $imported questions")
                index++
            }
        } catch (t: Exception) {
            session.abort()
            throw t
        } finally {
            tmp.delete()
            repository.close()
        }
    }

    private fun looksLikeSrcdocExport(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(1024 * 1024)
                val n = input.read(buf)
                if (n <= 0) false else {
                    val probe = String(buf, 0, n, Charsets.UTF_8)
                    Pattern.compile("srcdoc\\s*=", Pattern.CASE_INSENSITIVE).matcher(probe).find()
                }
            } ?: false
        } catch (_: Exception) { false }
    }

    /**
     * Streaming srcdoc extractor. It reads the outer HTML line-by-line and keeps only
     * one embedded document in memory at a time. Marrow encodes quotes inside srcdoc
     * as &quot;, so the attribute delimiter is safe to use as the boundary.
     */
    private class CountingInputStream(input:InputStream, private val total:Long, private val onProgress:(Int)->Unit):FilterInputStream(input){
        private var done=0L;private var last=-1
        override fun read():Int{val n=super.read();if(n>=0)tick(1);return n}
        override fun read(b:ByteArray,off:Int,len:Int):Int{val n=super.read(b,off,len);if(n>0)tick(n.toLong());return n}
        private fun tick(n:Long){done+=n;if(total>0){val p=(done*100/total).toInt().coerceIn(0,100);if(p!=last){last=p;onProgress(p)}}}
    }

    private fun parseMarrowSrcdocsFromUri(uri: Uri, onSection: (Int,String,Int)->Unit = {_,_,_->}): List<ImportedTest> {
        val out = mutableListOf<ImportedTest>()
        lastSrcdocCount = 0
        val total=fileSize(uri)
        contentResolver.openInputStream(uri)?.use { raw ->
            val input=CountingInputStream(raw,total){p->setImportProgress(5 + (p * 30 / 100),"Reading QBank structure • $p%") }
            BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024).use { reader ->
                val attr = Pattern.compile("srcdoc\\s*=\\s*([\\\"'])", Pattern.CASE_INSENSITIVE)
                while (true) {
                    val line = reader.readLine() ?: break
                    val m = attr.matcher(line)
                    if (!m.find()) continue
                    lastSrcdocCount++
                    val quote = m.group(1)?.firstOrNull() ?: continue
                    val content = StringBuilder()
                    var tail = line.substring(m.end())
                    var closed = false
                    while (true) {
                        val close = tail.indexOf(quote)
                        if (close >= 0) {
                            content.append(tail, 0, close)
                            closed = true
                            break
                        }
                        content.append(tail).append('\n')
                        tail = reader.readLine() ?: break
                    }
                    if (!closed || content.isEmpty()) continue

                    val decoded = HtmlImportParser.decodeHtmlEntitiesPreserveMarkup(content.toString())
                    val title = Regex("<title\\s*>\\s*(.*?)\\s*</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(decoded)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
                        ?.takeIf { it.isNotBlank() } ?: "Marrow Section ${out.size + 1}"
                    val parsed=HtmlImportParser.parseNamedQuestionsAssignments(decoded, title)
                    val questions=parsed.sumOf{it.questions.size}
                    if(questions>0) onSection(lastSrcdocCount,title,questions)
                    out.addAll(parsed)
                }
            }
        } ?: error("Cannot open $uri")
        return HtmlImportParser.dedupeImportedTests(out).map { test ->
            test.copy(questions = test.questions.map { q -> HtmlImportParser.resolveQuestionMedia(q, uri.toString()) })
        }
    }

    private fun fileSize(uri: Uri): Long {
        try { contentResolver.openFileDescriptor(uri, "r")?.use { if (it.statSize > 0) return it.statSize } } catch (_: Exception) {}
        return 0L
    }

    private fun displayName(uri: Uri): String {
        var name: String? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        return name ?: uri.lastPathSegment ?: "Imported QBank.html"
    }

    private fun readSmall(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size >= 3 && bytes[0].toInt()==0xEF && bytes[1].toInt()==0xBB && bytes[2].toInt()==0xBF)
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            if (bytes.size >= 2 && bytes[0].toInt()==0xFF && bytes[1].toInt()==0xFE)
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (bytes.size >= 2 && bytes[0].toInt()==0xFE && bytes[1].toInt()==0xFF)
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            // Most QBank exports are UTF-8; WebView can then resolve relative image URLs
            // against the selected document URI through loadDataWithBaseURL().
            return String(bytes, Charsets.UTF_8)
        } ?: error("Cannot open $uri")
    }

    private fun copyToFile(uri: Uri, dest: File, onProgress: (Int) -> Unit) {
        val total = fileSize(uri)
        var done = 0L
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                }
                out.fd.sync()
            }
        } ?: error("Cannot open $uri")
    }

    private fun formatMb(v: Long) = String.format(java.util.Locale.US, "%.0f", v / 1048576.0)

    inner class Bridge {
        @JavascriptInterface
        fun receive(json: String) {
            try {
                val root = org.json.JSONObject(json)
                val tests = HtmlImportParser.parseTests(root.optJSONArray("tests") ?: org.json.JSONArray())
                val name = root.optString("fileName", "Imported QBank.html")
                val provider = HtmlImportParser.detectProvider(tests)
                if (tests.isEmpty()) throw IllegalArgumentException("No QBank sections/questions detected in $name.")
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        progress.isIndeterminate=false
                        setImportProgress(48, "Writing extracted questions to local database…")
                    }
                }
                val currentUri = files.getOrNull(index) ?: throw IllegalStateException("No active import file")
                val accepted = AppManagers.importer.enqueue(name, fileSize(currentUri), work = {
                    val n = commitBundle(name, provider, tests)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            status.text = "Imported $n questions"
                            index++
                            web.stopLoading()
                        }
                    }
                }, onSuccess = {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) processNext()
                    }
                }, onFailure = { error ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            status.text = "Import failed: ${error.message ?: "unknown error"}"
                            Toast.makeText(this@HtmlImportActivity, error.message ?: "Import failed", Toast.LENGTH_LONG).show()
                            index++
                            processNext()
                        }
                    }
                })
                if (!accepted) {
                    status.text = "Another import is already running. Please wait…"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        status.text = "Import failed: ${e.message}"
                        Toast.makeText(this@HtmlImportActivity, e.message ?: "Import failed", Toast.LENGTH_LONG).show()
                        index++
                        processNext()
                    }
                }
            }
        }
    }

}
