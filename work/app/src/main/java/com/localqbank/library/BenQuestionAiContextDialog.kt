package com.localqbank.library

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.localqbank.library.ai.context.ContextPackBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Full-screen AI handoff surface. The current question is already visible in QuizActivity; this panel avoids duplicating it and lets Free AI lead after Ben builds bounded local context. */
class BenQuestionAiContextDialog {
    fun show(activity: QuizActivity, q: Question, selectedOption: String?) {
        ProductionCrashReporter.breadcrumb(activity, "AI_CONTEXT_OPENED")
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
            background = UiDrawableUtils.roundedDrawable(activity, ThemeManager.dialogBg(activity), 26f)
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(activity).apply {
            text = "BEN + AI"
            textSize = 23f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(activity))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(activity).apply {
            text = "EXAM INTELLIGENCE"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(ThemeManager.accent(activity)); background = rounded(activity, ThemeManager.explanationBg(activity), 12f)
            setPadding(dp(activity, 10), dp(activity, 7), dp(activity, 10), dp(activity, 7))
        })
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = "${currentBenExamProfile(activity).label}  •  Local QBank evidence first  •  Free AI / Google optional"
            textSize = 11.5f; setTextColor(ThemeManager.muted(activity)); setPadding(0, dp(activity, 2), 0, dp(activity, 9))
        })

        val modeRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val modes = listOf(BenQuestionAiMode.PYQ_CONTEXT, BenQuestionAiMode.PYT_CONTEXT, BenQuestionAiMode.FUTURE_RELATED, BenQuestionAiMode.OTHER_OPTIONS)
        var activeMode = BenQuestionAiMode.PYQ_CONTEXT
        val chips = linkedMapOf<BenQuestionAiMode, TextView>()
        fun chip(mode: BenQuestionAiMode) = TextView(activity).apply {
            text = mode.label; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(activity, 4), 0, dp(activity, 4), 0); isClickable = true; isFocusable = true
        }
        modes.forEach { mode ->
            val c = chip(mode); chips[mode] = c
            modeRow.addView(c, LinearLayout.LayoutParams(0, dp(activity, 40), 1f).apply { setMargins(dp(activity, 2), 0, dp(activity, 2), 0) })
        }
        root.addView(modeRow)

        val status = TextView(activity).apply {
            text = "CURRENT QUESTION → local evidence → structured explanation"
            textSize = 10.5f; setTextColor(ThemeManager.muted(activity)); setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 4))
        }
        root.addView(status)

        val web = WebView(activity).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url.toString(); if (u.startsWith("https://")) activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)); return true
                }
            }
            setBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(activity, 8), 0, 0) }
        fun action(label: String, click: () -> Unit) = TextView(activity).apply {
            text = label; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(activity)); background = rounded(activity, ThemeManager.elevated(activity), 13f); setOnClickListener { click() }
        }
        val save = action("SAVE TO NOTES") {
            val text = web.contentDescription?.toString().orEmpty().ifBlank { "${activeMode.label} • ${q.text}" }
            val ok = AppManagers.knowledge.saveRenderedViewWithNote(web, q.id, "Ben + AI • ${activeMode.label}", text)
            status.text = if (ok) "SAVED TO NOTES ✓" else "Could not save this surface"
        }
        val free = action("RUN FREE AI") { renderFree(activity, web, activeMode, q, selectedOption) }
        val pdf = action("UPLOAD PDF") { activity.startActivity(android.content.Intent(activity, BenPdfImportActivity::class.java)) }
        val google = action("SEARCH") {
            val query = "${currentBenExamProfile(activity).label} ${q.text} ${activeMode.prompt} Options: ${q.options.joinToString(" | ") { it.label + " " + it.text.take(300) }}"
            activity.startActivity(android.content.Intent(activity, GoogleSearchActivity::class.java).putExtra("query", query).putExtra("questionId", q.id))
        }
        val close = action("CLOSE") { dialog.dismiss() }
        listOf(save, free, pdf, google, close).forEachIndexed { i, v -> actions.addView(v, LinearLayout.LayoutParams(0, dp(activity, 44), 1f).apply { setMargins(if (i == 0) 0 else dp(activity, 3), 0, if (i == 4) 0 else dp(activity, 3), 0) }) }
        root.addView(actions)

        fun renderMode() { chips.forEach { (m, v) -> val on = m == activeMode; v.background = rounded(activity, if (on) ThemeManager.accent(activity) else ThemeManager.elevated(activity), 13f); v.setTextColor(if (on) Color.WHITE else ThemeManager.text(activity)) }; renderLocal(activity, web, status, activeMode, q, selectedOption) }
        chips.forEach { (m, v) -> v.setOnClickListener { activeMode = m; renderMode() } }

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        renderMode()
    }

    private fun renderLocal(activity: QuizActivity, web: WebView, status: TextView, mode: BenQuestionAiMode, q: Question, selectedOption: String?) {
        status.text = "BEN • COLLECTING LOCAL CONTEXT → FREE AI"
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val context = runCatching { AppManagers.frankensteinContext.buildEnhanced(q) }.getOrNull()
            val noLocal = context?.relatedQuestions.isNullOrEmpty() && context?.pdfEvidence.isNullOrEmpty()
            val localSummary = buildCompactContextHtml(activity, mode, context)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                web.loadDataWithBaseURL(null, localSummary, "text/html", "UTF-8", null)
                web.contentDescription = stripForNote(localSummary)
                status.text = if (noLocal) "BEN • NO LOCAL CONTEXT • FREE AI TAKES THE LEAD" else "BEN • CONTEXT READY • FREE AI TAKES THE LEAD"
                // Free AI is the answerer in this surface. Ben only retrieves and packages context.
                renderFree(activity, web, mode, q, selectedOption, context)
            }
        }
    }

    private fun renderFree(
        activity: QuizActivity,
        web: WebView,
        mode: BenQuestionAiMode,
        q: Question,
        selectedOption: String?,
        prebuiltContext: FrankensteinContextEngine.ContextBundle? = null
    ) {
        web.loadDataWithBaseURL(null, styledHtml("<div class='card'><div class='label'>FREE AI</div><h2>Preparing answer…</h2><p>Ben is supplying only the small amount of local context needed for this question.</p></div>", activity), "text/html", "UTF-8", null)
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val context = prebuiltContext ?: runCatching { AppManagers.frankensteinContext.buildEnhanced(q) }.getOrNull()
            val builder = ContextPackBuilder(activity)
            val packet = builder.forQuestion(q, selectedOption, learnerNote = mode.prompt).toPromptBlock() +
                "\n\n" + builder.questionAiEvidence(q, mode) +
                "\n\n" + compactContextPrompt(context)
            val system = "You are the primary Free AI answerer inside Rovex. Ben is only a context/retrieval assistant here. Answer the CURRENT QUESTION directly; the user already sees the stem and options, so do not repeat them. Use supplied QBank key/explanation as authoritative local truth when present, use related questions and PDF excerpts only as supporting context, never invent PYQ/PYT provenance, and clearly separate general knowledge from local evidence. Return concise exam-oriented Markdown with headings, short paragraphs, useful comparison tables, and bold key takeaways."
            val result = runCatching { AppManagers.cloudAi.generate(packet, system, 2200) }.getOrNull()
            val answer = result?.let { "## Free AI\n\n${it.text}\n\n*${it.provider.label} • ${it.model}*" }
                ?: "## Free AI unavailable\n\nNo configured Free AI provider responded. Ben's local context is still available from the Ben button."
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val rendered = styledHtml(rovexMarkdownToHtmlBody(answer), activity)
                    web.loadDataWithBaseURL(null, rendered, "text/html", "UTF-8", null)
                    web.contentDescription = stripForNote(rendered)
                }
            }
        }
    }

    private fun compactContextPrompt(context: FrankensteinContextEngine.ContextBundle?): String = buildString {
        append("BEN LOCAL CONTEXT — COMPACT HANDOFF\n")
        if (context == null) {
            append("No local retrieval was available. Do not invent local evidence.\n")
            return@buildString
        }
        append("CONCEPTS: ").append(context.concepts.take(6).joinToString(", ").ifBlank { "none" }).append('\n')
        context.weakestConcept?.let { append("LEARNER WEAKNESS: ").append(it).append(" (").append(context.weakestMastery ?: 0).append("%)\n") }
        append("RELATED LOCAL QUESTIONS: ").append(context.relatedQuestions.size).append('\n')
        context.relatedQuestions.take(4).forEachIndexed { i, r ->
            append("[Q${i+1}] ").append(r.exam.ifBlank { "Unknown" }).append(" / ").append(r.source.ifBlank { "Unknown" }).append(" • ").append(r.text.take(420)).append(" • KEY: ").append(r.answer.take(180)).append('\n')
        }
        append("REFERENCE PDF PAGES: ").append(context.pdfEvidence.size).append('\n')
        context.pdfEvidence.take(3).forEachIndexed { i, hit ->
            append("[PDF${i+1}] page ").append(hit.page).append(" • ").append(hit.text.take(900)).append('\n')
        }
    }.take(7000)

    private fun buildCompactContextHtml(activity: QuizActivity, mode: BenQuestionAiMode, context: FrankensteinContextEngine.ContextBundle?): String {
        val body = buildString {
            append("<div class='card'><div class='label'>BEN CONTEXT • ${esc(mode.label)}</div>")
            if (context == null) {
                append("<h2>Free AI takes the lead</h2><p>No local context was available. Ben will not invent evidence.</p>")
            } else {
                append("<h2>Free AI takes the lead</h2><p>Ben collected a bounded local evidence pack. The question and options remain in the quiz screen and are not repeated here.</p>")
                append("<p><b>${context.relatedQuestions.size}</b> related QBank questions • <b>${context.pdfEvidence.size}</b> reference-PDF pages")
                if (context.concepts.isNotEmpty()) append(" • ${esc(context.concepts.take(5).joinToString(", "))}")
                append("</p>")
                context.weakestConcept?.let { append("<p><b>Learner focus:</b> ${esc(it)} (${context.weakestMastery ?: 0}% local estimate)</p>") }
            }
            append("</div>")
        }
        return styledHtml(body, activity)
    }

    private fun rovexMarkdownToHtmlBody(raw: String): String = rovexMarkdownToHtml(raw).substringAfter("<body>").substringBeforeLast("</body>")

    private fun styledHtml(body: String, activity: QuizActivity): String {
        val dark = ThemeManager.isDark(activity)
        val bg = colorHex(ThemeManager.dialogBg(activity)); val text = colorHex(ThemeManager.text(activity)); val muted = colorHex(ThemeManager.muted(activity)); val accent = colorHex(ThemeManager.accent(activity))
        return """<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;font-size:16px;line-height:1.55;margin:0;padding:5px 2px;color:$text;background:$bg}h1{font-size:25px;line-height:1.2;margin:8px 0 10px}h2{font-size:21px;line-height:1.28;margin:10px 0 9px}h3{font-size:17px;line-height:1.3;margin:15px 0 7px}p{margin:8px 0;color:$text}b,strong{color:${if(dark)"#F4F8FB" else "#17212B"}}blockquote{margin:10px 0;padding:9px 12px;border-left:4px solid $accent;background:${if(dark)"#1B2D39" else "#FFF6D9"};border-radius:8px}code,pre{background:${if(dark)"#182731" else "#EEF3F6"};border-radius:8px}pre{padding:10px;overflow:auto}table{width:100%;border-collapse:separate;border-spacing:0;margin:12px 0;background:${if(dark)"#13212B" else "#FFFFFF"};border:1px solid ${if(dark)"#3A5260" else "#CBD5DB"};border-radius:10px;overflow:hidden}th,td{border-right:1px solid ${if(dark)"#3A5260" else "#D5DDE2"};border-bottom:1px solid ${if(dark)"#3A5260" else "#D5DDE2"};padding:9px 8px;text-align:left;vertical-align:top;word-break:normal}th:last-child,td:last-child{border-right:0}tr:last-child td{border-bottom:0}th{background:${if(dark)"#1D3646" else "#EAF1F5"};color:$accent;font-weight:800}a{color:${if(dark)"#8ED2FF" else "#126B9A"}}.card{background:${if(dark)"#14222D" else "#F8F5EE"};border:1px solid ${if(dark)"#304958" else "#D7CFBF"};border-radius:16px;padding:14px;margin:9px 0}.label{font-size:10px;color:$accent;font-weight:800;letter-spacing:.09em}.answer{font-size:19px;font-weight:800;color:#36B77C}img{max-width:100%;height:auto;border-radius:10px;margin:8px 0}</style></head><body>$body</body></html>"""
    }

    private fun stripForNote(html: String): String = android.text.Html.fromHtml(html.replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), "[image]"), android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\n{3,}"), "\n\n").trim().take(24_000)
    private fun esc(v: String): String = android.text.TextUtils.htmlEncode(v)
    private fun colorHex(c: Int): String = String.format("#%06X", 0xFFFFFF and c)
    private fun rounded(a: QuizActivity, color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius * a.resources.displayMetrics.density }
    private fun dp(a: QuizActivity, v: Int) = (v * a.resources.displayMetrics.density).toInt()
}
