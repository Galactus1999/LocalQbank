package com.localqbank.library

import android.app.Activity
import android.app.AlertDialog
import        fun action(label:String, click:()->Unit)=TextView(this).apply{
    text=label; textSize=11.5f; gravity=Gravity.CENTER
    setTextColor(ThemeManager.text(this@RenActivity))
    background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),11f)
    setOnClickListener{click()}
}
val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
row.addView(action("Dr.F"){respond(input,answer,modelPill)},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(0,0,dp(5),0)})
row.addView(action("Dr.F + AI"){runCombinedCore(input,answer,modelPill)},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(0,0,dp(5),0)})
row.addView(action("G.search"){startActivity(Intent(this@RenActivity,GoogleSearchActivity::class.java))},LinearLayout.LayoutParams(0,dp(38),1f))
root.addView(row)
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Chat-first Dr. Frankenstein workspace. Detailed diagnostics live in Model Lab / Adaptive Engine. */
class RenActivity : Activity() {
    private var openMatchButton: TextView? = null
    private var liveStatus: TextView? = null
    private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var liveJob: Job? = null
    private var cloudJob: Job? = null
    private var contextCard: LinearLayout? = null
    private var promptInput: EditText? = null
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        AppManagers.initialize(applicationContext)
        SystemUi.immersive(this)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(build())
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background=ThemeManager.backgroundDrawable(this@RenActivity)
        }
        val header = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
header.addView(TextView(this).apply { text="‹"; textSize=30f; gravity=Gravity.CENTER; setTextColor(ThemeManager.text(this@RenActivity)); setOnClickListener{finish()} }, LinearLayout.LayoutParams(dp(40),dp(42)))
header.addView(Space(this), LinearLayout.LayoutParams(0,1,1f))
header.addView(TextView(this).apply {
    text="FREE AI"; textSize=10f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER
    setTextColor(ThemeManager.text(this@RenActivity))
    background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),10f)
    setPadding(dp(9),0,dp(9),0); setOnClickListener{showFreeAiMenu()}
},LinearLayout.LayoutParams(dp(78),dp(42)).apply{setMargins(0,0,dp(5),0)})
header.addView(TextView(this).apply {
    text="MODEL LAB"; textSize=10f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER
    setTextColor(ThemeManager.text(this@RenActivity))
    background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),10f)
    setPadding(dp(9),0,dp(9),0); setOnClickListener{startActivity(Intent(this@RenActivity,BenModelLabActivity::class.java))}
},LinearLayout.LayoutParams(dp(94),dp(42)))
root.addView(header)

        val modelPill = TextView(this).apply {
    text = modelStatus(); textSize=9f
    setTextColor(ThemeManager.muted(this@RenActivity))
    visibility = View.GONE
}

        val answerScroll = ScrollView(this).apply { isFillViewport=true; clipToPadding=false }
        val chatSurface = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(2),dp(2),dp(2),dp(8)) }
        val answer = TextView(this).apply {
            text="Ready"
            textSize=17f; setTextColor(ThemeManager.text(this@RenActivity)); setPadding(dp(14),dp(14),dp(14),dp(16))
            background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@RenActivity));cornerRadius=22f*resources.displayMetrics.density;setStroke(dp(1),ThemeManager.accent(this@RenActivity))}; gravity=Gravity.TOP; setLineSpacing(0f,1.22f)
        }
        chatSurface.addView(answer, LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(8))})
        answerScroll.addView(chatSurface, android.widget.FrameLayout.LayoutParams(-1,-2))
        root.addView(answerScroll, LinearLayout.LayoutParams(-1,0,1f))
        liveStatus = TextView(this).apply {
            text="BEN • READY"; textSize=10.5f; setTextColor(ThemeManager.muted(this@RenActivity)); setPadding(dp(4),dp(5),dp(4),dp(5))
        }
        liveStatus.visibility=View.GONE

        val input = EditText(this).apply {
            hint="Ask Dr. Frankenstein…"; textSize=15f; setTextColor(ThemeManager.text(this@RenActivity)); setHintTextColor(ThemeManager.muted(this@RenActivity))
            setSingleLine(false); minLines=2; maxLines=4; gravity=Gravity.TOP; setPadding(dp(16),dp(12),dp(16),dp(12))
            background=UiDrawableUtils.roundedDrawable(this@RenActivity, ThemeManager.elevated(this@RenActivity),16f)
        }
        promptInput = input
        root.addView(input, LinearLayout.LayoutParams(-1,dp(62)).apply{setMargins(0,dp(2),0,dp(5))})

        val core=TextView(this).apply{
        fun action(label:String, click:()->Unit)=TextView(this).apply{
    text=label; textSize=11.5f; gravity=Gravity.CENTER
    setTextColor(ThemeManager.text(this@RenActivity))
    background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),11f)
    setOnClickListener{click()}
}
val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
row.addView(action("Dr.F"){respond(input,answer,modelPill)},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(0,0,dp(5),0)})
row.addView(action("Dr.F + AI"){runCombinedCore(input,answer,modelPill)},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(0,0,dp(5),0)})
row.addView(action("G.search"){startActivity(Intent(this@RenActivity,GoogleSearchActivity::class.java))},LinearLayout.LayoutParams(0,dp(38),1f))
root.addView(row)

        // Android 15+/API 36 edge-to-edge can ignore the legacy resize hint. Apply the IME
        // inset directly to the chat surface so the composer and its typed text always remain
        // above the keyboard. This also keeps the last lines visible while the user types.
        val baseLeft=root.paddingLeft;val baseTop=root.paddingTop;val baseRight=root.paddingRight;val baseBottom=root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root){_,insets->
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottom=baseBottom+(if(ime>bars)ime else bars)
            root.setPadding(baseLeft,baseTop,baseRight,bottom)
            if(ime>0){
                input.post{
                    val r=Rect(0,0,input.width,input.height)
                    input.requestRectangleOnScreen(r,true)
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
        input.setOnFocusChangeListener{_,hasFocus->
            if(hasFocus) input.post{input.requestRectangleOnScreen(Rect(0,0,input.width,input.height),true)}
        }
        return root
    }

    private fun memoryStatusLabel(): String {
        val chat = if (AppManagers.renMemory.chatContext().isBlank()) "EMPTY" else "READY"
        return "CHAT MEMORY • $chat • CLEAR"
    }


    private fun buildFrankensteinContextCard(): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    fun chip(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 9f; gravity = Gravity.CENTER
        setTextColor(ThemeManager.text(this@RenActivity))
        background = UiDrawableUtils.roundedDrawable(this@RenActivity, ThemeManager.elevated(this@RenActivity), 9f)
        setPadding(dp(4), 0, dp(4), 0)
        minHeight = 0
        setOnClickListener { action() }
    }
    val inspect = chip("INSPECT") {
        val prompt = currentPromptForContext()
        if (prompt.isBlank()) Toast.makeText(this, "Enter a topic first", Toast.LENGTH_SHORT).show()
        else showFrankensteinContext(prompt)
    }
    val local = chip("LOCAL") { showFrankensteinContext(currentPromptForContext()) }
    val weak = chip("WEAKNESS") { showWeaknessContext() }
    row.addView(inspect, LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(0, 0, dp(4), 0) })
    row.addView(local, LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(0, 0, dp(4), 0) })
    row.addView(weak, LinearLayout.LayoutParams(0, dp(30), 1f))
    return row
}

    private fun cleanAiAnswer(raw:String):String{
    var x=raw.replace(Regex("(?is)<style[^>]*>.*?</style>"),"").replace(Regex("(?is)<script[^>]*>.*?</script>"),"")
    val css=Regex("(?s)(?:body|h[1-6]|p|blockquote|code,pre|pre|table|th,td)\s*\{[^}]*\}")
    x=css.replace(x,"")
    x=x.replace(Regex("(?m)^\s*(?:font-family|font-size|line-height|margin|padding|color|background|border(?:-[a-z]+)?)\s*:[^\n}]+[;}]?\s*$"),"")
    return x.replace(Regex("\n{3,}"),"\n\n").trim()
}

    private fun currentPromptForContext(): String = promptInput?.text?.toString()?.trim().orEmpty()

    private fun showFrankensteinContext(query: String) {
        if (query.isBlank()) { Toast.makeText(this, "Enter a topic or question first", Toast.LENGTH_SHORT).show(); return }
        liveScope.launch(Dispatchers.IO) {
            val context = try {
                AppManagers.frankensteinContext.buildEnhanced(
                    Question(0L, "context", 0, null, query, query, null, null, null, null, null, emptyList(), emptyList(), emptyList())
                )
            } catch (t: CancellationException) {
                throw t
            } catch (_: Exception) { null }
            val local = try { AppManagers.benBrain.answer(query) } catch (_: Exception) { null }
            val concepts = context?.concepts.orEmpty().ifEmpty { local?.plan?.concepts.orEmpty() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val message = buildString {
                    append("LOCAL CONTEXT\n\n")
                    append("Concepts: ").append(concepts.take(6).joinToString(", ").ifBlank { "not confidently mapped" }).append("\n")
                    append("Related questions: ").append(context?.relatedQuestions?.size ?: 0).append("\n")
                    append("Source clusters: ").append(context?.relatedSources?.joinToString(" • ").ifNullOrBlank("none found")).append("\n")
                    local?.plan?.specialist?.let { append("Specialist: ").append(it).append("\n") }
                    local?.confidence?.let { append("Local confidence: ").append(it).append("/100\n") }
                    append("\nThis is the bounded context Ben can hand to the selected Free AI accelerator. Your QBank answer and study state remain authoritative.")
                }
                AlertDialog.Builder(this@RenActivity).setTitle("Frankenstein Context").setMessage(message).setPositiveButton("OK", null).show()
            }
        }
    }

    private fun String?.ifNullOrBlank(fallback: String): String = if (this.isNullOrBlank()) fallback else this

    private fun showWeaknessContext() {
        val text = currentPromptForContext()
        if (text.isBlank()) { Toast.makeText(this, "Ask Ben a topic first so I can inspect your weak concepts", Toast.LENGTH_SHORT).show(); return }
        liveScope.launch(Dispatchers.IO) {
            val local = runCatching { AppManagers.benBrain.answer(text) }.getOrNull()
            val concepts = local?.plan?.concepts.orEmpty()
            val model = AppManagers.benBrain.learner()
            val rows = concepts.map { it to model.stats(it) }.sortedBy { it.second.mastery }.take(5)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                AlertDialog.Builder(this@RenActivity)
                    .setTitle("Your local weakness map")
                    .setMessage(rows.joinToString("\n") { (c, s) -> "${s.mastery}% • $c • ${s.wrong} wrong / ${s.seen} seen" }.ifBlank { "No weakness data yet." })
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    private fun modelStatus(): String {
        val m=BenNeuralModelManager(this)
        val e=m.installed(BenNeuralModelRegistry.embeddingGemma300m)!=null
        val g=m.installed(BenNeuralModelRegistry.gemma3_270m)!=null
        return when { e&&g -> "Neural: EmbeddingGemma + Gemma 3 270M ready"; e -> "Neural: EmbeddingGemma installed • generation fallback"; g -> "Neural: Gemma 3 270M installed • semantic fallback"; else -> "Neural: models not installed • deterministic Ben ready" }
    }

    private fun respond(input:EditText,answer:TextView,modelPill:TextView){
        val cmd=input.text?.toString().orEmpty().trim()
        openMatchButton?.visibility=View.GONE
        if(cmd.isBlank()){answer.text="Ask Ben a question first.";return}
        AppManagers.renMemory.putWorking(cmd)
        AppManagers.renMemory.appendChatTurn("USER", cmd)
        answer.text="Ben is working…"
        liveJob?.cancel()
        liveJob=liveScope.launch{
            while(isActive){
                val t=BenNeuralTelemetry.snapshot()
                val gov=BenAiResourceGovernor(this@RenActivity).snapshot(foreground=true)
                liveStatus?.text="BEN • ${t.stage.name.replace('_',' ')} • ${t.stageDetail}"
                modelPill.text="${t.activeModel} • RAM ${gov.availableMemoryMb} MB • thermal ${gov.thermalStatus}"
                if(t.stage==BenNeuralTelemetry.Stage.COMPLETE||t.stage==BenNeuralTelemetry.Stage.FALLBACK||t.stage==BenNeuralTelemetry.Stage.ERROR||t.stage==BenNeuralTelemetry.Stage.BLOCKED) break
                delay(180L)
            }
        }
        AppManagers.frankensteinSupport.respond(cmd){ insight, ids ->
            runOnUiThread{
                liveJob?.cancel()
                val t=BenNeuralTelemetry.snapshot()
                liveStatus?.text="BEN • ${if(t.lastGeneratorUsed||t.lastSemanticUsed) "NEURAL" else "DETERMINISTIC FALLBACK"} • ${t.lastElapsedMs} ms"
                modelPill.text="${t.activeModel} • evidence ${t.lastEvidenceCount} • verifier ${if(t.lastVerified) "PASS" else "REVIEW"}"
                if(isFinishing||isDestroyed)return@runOnUiThread
                answer.text=rovexMarkdownToSpanned(cleanAiAnswer(insight.body))
                if(insight.actions.contains("Open matching questions")&&ids.isNotEmpty()){
                    openMatchButton?.visibility=View.VISIBLE
                    val query=cmd.replace(Regex("(?i)\\b(find|search|show|give|open|questions?|qbank|topic|subject|about)\\b")," ").replace(Regex("\\s+")," ").trim()
                    openMatchButton?.setOnClickListener{startActivity(Intent(this,QuizActivity::class.java).putExtra("collectionMode",true).putExtra("sessionIds",ids.joinToString(",")).putExtra("sessionLabel","Dr. Frankenstein • $query").putExtra("practiceMode",true).putExtra("title","Dr. Frankenstein • $query"))}
                }
                AppManagers.renMemory.putStudy(answer.text.toString())
                AppManagers.renMemory.appendChatTurn("BEN", insight.body)
            }
        }
    }

    private fun runCombinedCore(input:EditText, answer:TextView, modelPill:TextView){
        val cmd=input.text?.toString().orEmpty().trim()
        if(cmd.isBlank()){answer.text="Ask a clinical/study question first.";return}
        cloudJob?.cancel()
        answer.text="Ben is collecting local context… Free AI will answer."
        liveStatus?.text="BEN • CONTEXT → FREE AI"
        cloudJob=liveScope.launch(Dispatchers.IO){
            val context=runCatching{
                AppManagers.frankensteinContext.buildEnhanced(
                    Question(0L,"context",0,null,cmd,cmd,null,null,null,null,null,emptyList(),emptyList(),emptyList())
                )
            }.getOrNull()
            val compact=buildString{
                append("BEN LOCAL CONTEXT — COMPACT HANDOFF\n")
                append("CONCEPTS: ").append(context?.concepts?.take(6)?.joinToString(", ").orEmpty().ifBlank{"none"}).append('\n')
                append("RELATED QUESTIONS: ").append(context?.relatedQuestions?.size ?: 0).append('\n')
                context?.relatedQuestions?.take(4)?.forEachIndexed{i,r->append("[Q${i+1}] ").append(r.exam).append(" / ").append(r.source).append(" • ").append(r.text.take(420)).append(" • KEY: ").append(r.answer.take(180)).append('\n')}
                append("REFERENCE PDF PAGES: ").append(context?.pdfEvidence?.size ?: 0).append('\n')
                context?.pdfEvidence?.take(3)?.forEachIndexed{i,h->append("[PDF${i+1}] page ").append(h.page).append(" • ").append(h.text.take(900)).append('\n')}
            }.take(7000)
            val prompt=buildString{
                append(benExamPromptContext(applicationContext)).append("\n\n")
                append("USER REQUEST:\n").append(cmd.take(5000)).append("\n\n")
                append(compact)
                append("\nDo not repeat the user's request verbatim. Use the local evidence only as supporting context; do not invent PYQ provenance. If local evidence conflicts with a keyed QBank answer, explicitly flag the conflict instead of silently changing it.")
            }
            val result=runCatching{AppManagers.cloudAi.generate(prompt=prompt,system="You are the primary Free AI answerer inside Rovex. Ben is only a context collector in this mode. Answer the user's study request directly and concisely. Use local QBank/PDF context when supplied, but do not claim web search or invent provenance. Use readable Markdown and tables when useful.",maxTokens=900,timeoutMs=9_000L)}.getOrNull()
            runOnUiThread{
                if(isFinishing||isDestroyed)return@runOnUiThread
                if(result!=null){
                    answer.text=rovexMarkdownToSpanned(cleanAiAnswer(result.text))
                    modelPill.text="${result.provider.label} • ${result.model}"
                    liveStatus?.text="FREE AI • BEN CONTEXT SUPPLIED"
                }else{
                    answer.text="No Free AI provider is connected. Tap FREE AI to connect a free provider."
                    modelPill.text="FREE AI unavailable"
                    liveStatus?.text="BEN • CONTEXT READY • NO FREE AI"
                }
            }
        }
    }

    private fun showFreeAiMenu() { BenFreeAiDialog(this, liveScope).show() }

    private fun openWebSearch(query:String){
        val clean=query.trim().ifBlank{"medical study question"}
        val q="${currentBenExamProfile(this).label} medical exam context: $clean"
        startActivity(Intent(this,GoogleSearchActivity::class.java).putExtra("query",q))
    }

    override fun onDestroy(){liveJob?.cancel();cloudJob?.cancel();liveScope.cancel();super.onDestroy()}
}
