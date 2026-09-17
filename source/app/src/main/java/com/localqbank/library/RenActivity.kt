package com.localqbank.library

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.view.View
import android.widget.*
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
    private var geminiJob: Job? = null
    private var geminiSearchEntry: WebView? = null
    private var chatMedia: ImageView? = null
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
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"; textSize = 32f; gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(this@RenActivity)); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6),0,dp(6),0) }
        titleBox.addView(TextView(this).apply { text="Dr. Frankenstein"; textSize=22f; typeface=Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(this@RenActivity)) })
        titleBox.addView(TextView(this).apply { text="Local study intelligence"; textSize=11f; setTextColor(ThemeManager.muted(this@RenActivity)) })
        header.addView(titleBox, LinearLayout.LayoutParams(0,-2,1f))
        val lab = TextView(this).apply {
            text = "MODEL LAB"; textSize=10.5f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER
            setTextColor(ThemeManager.text(this@RenActivity)); background=rounded(ThemeManager.elevated(this@RenActivity),12)
            setPadding(dp(10),0,dp(10),0); setOnClickListener { startActivity(Intent(this@RenActivity, BenModelLabActivity::class.java)) }
        }
        header.addView(lab, LinearLayout.LayoutParams(dp(96),dp(48)))
        root.addView(header)

        val modelPill = TextView(this).apply {
            text = modelStatus(); textSize=11f; gravity=Gravity.CENTER_VERTICAL
            setTextColor(ThemeManager.muted(this@RenActivity)); setPadding(dp(10),0,dp(10),0)
            background=rounded(ThemeManager.elevated(this@RenActivity),11)
        }
        root.addView(modelPill, LinearLayout.LayoutParams(-1,dp(34)).apply{setMargins(dp(4),dp(4),dp(4),dp(6))})

        val brainMode = TextView(this).apply {
            text = BenModelRouter(applicationContext).uiSummary()
            textSize = 10.5f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ThemeManager.muted(this@RenActivity))
            setPadding(dp(10),0,dp(10),0)
            background = rounded(ThemeManager.bg(this@RenActivity),11)
            contentDescription = "Ben model router status"
        }
        root.addView(brainMode, LinearLayout.LayoutParams(-1,dp(30)).apply{setMargins(dp(4),0,dp(4),dp(6))})

        contextCard = buildFrankensteinContextCard()
        root.addView(contextCard, LinearLayout.LayoutParams(-1, dp(104)).apply { setMargins(dp(4), 0, dp(4), dp(6)) })

        val answerScroll = ScrollView(this).apply { isFillViewport=true; clipToPadding=false }
        val chatSurface = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(2),dp(2),dp(2),dp(8)) }
        val answer = TextView(this).apply {
            text="Start with one focused question. Ben handles local reasoning first; Gemini is added only when you ask for current/external context."
            textSize=16f; setTextColor(ThemeManager.text(this@RenActivity)); setPadding(dp(18),dp(18),dp(18),dp(20))
            background=rounded(ThemeManager.elevated(this@RenActivity),22); gravity=Gravity.TOP; setLineSpacing(0f,1.18f)
        }
        chatSurface.addView(answer, LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(8))})
        chatMedia=ImageView(this).apply{visibility=View.GONE;adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(dp(4),dp(4),dp(4),dp(8));contentDescription="Gemini study visual"}
        chatSurface.addView(chatMedia,LinearLayout.LayoutParams(-1,dp(300)))
        answerScroll.addView(chatSurface, android.widget.FrameLayout.LayoutParams(-1,-2))
        root.addView(answerScroll, LinearLayout.LayoutParams(-1,0,1f))
        geminiSearchEntry=WebView(this).apply{
            visibility=View.GONE
            setBackgroundColor(ThemeManager.elevated(this@RenActivity))
            settings.javaScriptEnabled=false
            settings.domStorageEnabled=false
            setPadding(dp(4),dp(2),dp(4),dp(2))
        }
        root.addView(geminiSearchEntry,LinearLayout.LayoutParams(-1,dp(92)).apply{setMargins(dp(4),0,dp(4),dp(4))})

        liveStatus = TextView(this).apply {
            text="BEN • READY"; textSize=10.5f; setTextColor(ThemeManager.muted(this@RenActivity)); setPadding(dp(4),dp(5),dp(4),dp(5))
        }
        root.addView(liveStatus, LinearLayout.LayoutParams(-1,dp(28)))

        val input = EditText(this).apply {
            hint="Ask Dr. Frankenstein…"; textSize=15f; setTextColor(ThemeManager.text(this@RenActivity)); setHintTextColor(ThemeManager.muted(this@RenActivity))
            setSingleLine(false); minLines=2; maxLines=4; gravity=Gravity.TOP; setPadding(dp(16),dp(12),dp(16),dp(12))
            background=rounded(ThemeManager.elevated(this@RenActivity),16)
        }
        promptInput = input
        root.addView(input, LinearLayout.LayoutParams(-1,dp(78)).apply{setMargins(0,dp(2),0,dp(8))})

        val core=TextView(this).apply{
            text="BEN + GEMINI CORE"; textSize=13f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER
            setTextColor(Color.WHITE); background=rounded(ThemeManager.accent(this@RenActivity),16)
            contentDescription="Run the combined Ben and Gemini core"
            setOnClickListener{runCombinedCore(input,answer,modelPill)}
        }
        root.addView(core,LinearLayout.LayoutParams(-1,dp(52)).apply{setMargins(0,dp(2),0,dp(7))})
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        openMatchButton=TextView(this).apply{ text="MATCH"; textSize=11f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(Color.WHITE); visibility=View.GONE; background=rounded(ThemeManager.accent(this@RenActivity),13) }
        row.addView(openMatchButton,LinearLayout.LayoutParams(dp(78),dp(46)).apply{setMargins(0,0,dp(6),0)})
        val ask=TextView(this).apply{ text="ASK BEN"; textSize=12f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(Color.WHITE); background=rounded(ThemeManager.accent(this@RenActivity),14); setOnClickListener{respond(input,answer,modelPill)} }
        row.addView(ask,LinearLayout.LayoutParams(0,dp(46),1f).apply{weight=1f})
        val gemini=TextView(this).apply{
            text="GEMINI"; textSize=11f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER
            setTextColor(ThemeManager.text(this@RenActivity)); background=rounded(ThemeManager.elevated(this@RenActivity),14)
            setOnClickListener{researchWithGemini(input,answer,modelPill)}
        }
        row.addView(gemini,LinearLayout.LayoutParams(dp(82),dp(46)).apply{setMargins(dp(6),0,dp(4),0)})
        val visual=TextView(this).apply{
            text="VISUAL"; textSize=10.5f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER
            setTextColor(ThemeManager.text(this@RenActivity)); background=rounded(ThemeManager.elevated(this@RenActivity),14)
            contentDescription="Generate a Gemini study visual"; setOnClickListener{generateChatVisual(input,answer,modelPill)}
        }
        row.addView(visual,LinearLayout.LayoutParams(dp(70),dp(46)).apply{setMargins(0,0,dp(4),0)})
        val account=TextView(this).apply{
            text="G"; textSize=15f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER
            setTextColor(ThemeManager.text(this@RenActivity)); background=rounded(ThemeManager.elevated(this@RenActivity),14)
            contentDescription="Google account and Gemini setup"
            setOnClickListener{showGoogleAccountDialog()}
        }
        row.addView(account,LinearLayout.LayoutParams(dp(46),dp(46)))
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

    private fun buildFrankensteinContextCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(ThemeManager.elevated(this@RenActivity), 18)
        }
        val title = TextView(this).apply {
            text = "🧠 FRANKENSTEIN CONTEXT"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(this@RenActivity))
        }
        card.addView(title)
        val summary = TextView(this).apply {
            text = "Ready • local QBank + learner memory + notes"
            textSize = 11f
            setTextColor(ThemeManager.muted(this@RenActivity))
            setPadding(0, dp(3), 0, dp(5))
        }
        card.addView(summary)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun chip(label: String, action: () -> Unit): TextView = TextView(this).apply {
            text = label; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(this@RenActivity)); setBackground(rounded(ThemeManager.bg(this@RenActivity), 12))
            setPadding(dp(9), 0, dp(9), 0); setOnClickListener { action() }
        }
        val inspect = chip("INSPECT") {
            val prompt = "Build my Frankenstein Context for this study request: ${currentPromptForContext().take(2500)}"
            if (prompt.endsWith(":")) Toast.makeText(this, "Enter a topic first", Toast.LENGTH_SHORT).show()
            else showFrankensteinContext(prompt.removePrefix("Build my Frankenstein Context for this study request: "))
        }
        val local = chip("LOCAL") { showFrankensteinContext(currentPromptForContext()) }
        val weak = chip("WEAKNESS") { showWeaknessContext() }
        chips.addView(inspect, LinearLayout.LayoutParams(0, dp(32), 1f).apply { setMargins(0, 0, dp(5), 0) })
        chips.addView(local, LinearLayout.LayoutParams(0, dp(32), 1f).apply { setMargins(0, 0, dp(5), 0) })
        chips.addView(weak, LinearLayout.LayoutParams(0, dp(32), 1f))
        card.addView(chips)
        return card
    }

    private fun currentPromptForContext(): String = promptInput?.text?.toString()?.trim().orEmpty()

    private fun showFrankensteinContext(query: String) {
        if (query.isBlank()) { Toast.makeText(this, "Enter a topic or question first", Toast.LENGTH_SHORT).show(); return }
        liveScope.launch(Dispatchers.IO) {
            val context = runCatching { FrankensteinContextEngine(applicationContext).build(
                Question(0L, "context", 0, null, query, query, null, null, null, null, null, emptyList(), emptyList(), emptyList())
            ) }.getOrNull()
            val local = runCatching { AppManagers.benBrain.answer(query) }.getOrNull()
            val concepts = context?.concepts.orEmpty().ifEmpty { local?.plan?.concepts.orEmpty() }
            val noteCount = runCatching {
                val db = QBankDb(applicationContext)
                try { db.notes().count { (_, note) -> note.contains(query, true) || concepts.any { c -> note.contains(c, true) } } }
                finally { db.close() }
            }.getOrDefault(0)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val message = buildString {
                    append("LOCAL CONTEXT\n\n")
                    append("Concepts: ").append(concepts.take(6).joinToString(", ").ifBlank { "not confidently mapped" }).append("\n")
                    append("Related questions: ").append(context?.relatedQuestions?.size ?: 0).append("\n")
                    append("Source clusters: ").append(context?.relatedSources?.joinToString(" • ").ifNullOrBlank("none found")).append("\n")
                    append("Relevant saved notes: ").append(noteCount).append("\n")
                    local?.plan?.specialist?.let { append("Specialist: ").append(it).append("\n") }
                    local?.confidence?.let { append("Local confidence: ").append(it).append("/100\n") }
                    append("\nThis is the bounded context Ben can hand to Gemini. Your QBank answer and study state remain authoritative.")
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
            val model = BenLearnerModel(applicationContext)
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
                answer.text=insight.body
                if(insight.actions.contains("Open matching questions")&&ids.isNotEmpty()){
                    openMatchButton?.visibility=View.VISIBLE
                    val query=cmd.replace(Regex("(?i)\\b(find|search|show|give|open|questions?|qbank|topic|subject|about)\\b")," ").replace(Regex("\\s+")," ").trim()
                    openMatchButton?.setOnClickListener{startActivity(Intent(this,QuizActivity::class.java).putExtra("collectionMode",true).putExtra("sessionIds",ids.joinToString(",")).putExtra("sessionLabel","Dr. Frankenstein • $query").putExtra("practiceMode",true).putExtra("title","Dr. Frankenstein • $query"))}
                }
                AppManagers.renMemory.putStudy(answer.text.toString())
            }
        }
    }

    private fun runCombinedCore(input:EditText, answer:TextView, modelPill:TextView){
        val cmd=input.text?.toString().orEmpty().trim()
        if(cmd.isBlank()){answer.text="Ask a clinical/study question first.";return}
        geminiJob?.cancel()
        answer.text="Frankenstein is routing your question…"
        liveStatus?.text="BEN • MODEL ROUTER"
        geminiJob=liveScope.launch{
            val router = BenModelRouter(applicationContext)
            val route = router.route(cmd, GoogleAccountManager(this@RenActivity).currentEmail() != null)
            modelPill.text = route.label
            val local=runCatching{AppManagers.benBrain.answer(cmd)}.getOrNull()
            if(route.mode == BenModelRouter.Mode.LOCAL){
                if(!isFinishing&&!isDestroyed){
                    answer.text = local?.answer?.takeIf{it.isNotBlank()} ?: "Ben could not produce a local answer."
                    liveStatus?.text="BEN • LOCAL BRAIN • ${route.reason}"
                    modelPill.text=route.label
                }
                return@launch
            }
            answer.text = buildString {
                append(local?.answer?.takeIf{it.isNotBlank()} ?: "Ben local reasoning prepared the request.")
                append("\n\nFRANKENSTEIN → GEMINI\n")
                append(route.reason)
                append("\n\nPreparing the bounded local evidence packet…")
            }
            val account=GoogleAccountManager(this@RenActivity)
            val current=account.currentEmail()
            if(current==null){
                val login=account.signIn()
                if(login.isFailure){
                    if(!isFinishing&&!isDestroyed){
                        answer.text=buildString{
                            append(local?.answer?.takeIf{it.isNotBlank()} ?: "Ben could not produce a local answer.")
                            append("\n\nGEMINI SETUP\n")
                            append(account.friendlyError(login.exceptionOrNull() ?: IllegalStateException()))
                            append("\n\nTap G above. You only need to connect your Google account once.")
                        }
                        liveStatus?.text="BEN • LOCAL FALLBACK • GEMINI NOT CONNECTED"
                    }
                    return@launch
                }
                runGeminiResearch(cmd,answer,modelPill,login.getOrNull())
            } else {
                runGeminiResearch(cmd,answer,modelPill,current)
            }
        }
    }

    private fun showGoogleAccountDialog(){
        val account=GoogleAccountManager(this)
        val email=account.currentEmail()
        if(email!=null){
            AlertDialog.Builder(this)
                .setTitle("Gemini account")
                .setMessage("Signed in as $email\n\nGemini web research is ready. You can also sign out and switch accounts.")
                .setPositiveButton("OK",null)
                .setNeutralButton("SIGN OUT"){_,_->liveScope.launch{account.signOut();Toast.makeText(this@RenActivity,"Google account signed out",Toast.LENGTH_SHORT).show()}}
                .show()
            return
        }
        if(!account.firebaseReady()){
            AlertDialog.Builder(this)
                .setTitle("Connect Gemini")
                .setMessage("You do not need to understand Firebase to use Gemini. Rovex uses Firebase only as Google's secure mobile gateway. One-time setup: create/connect the Rovex Firebase project, add the Android app, enable Google Sign-In, and place google-services.json in the app module. After that, this same button handles Google sign-in automatically.\n\nI would NOT put a Gemini API key directly inside the APK because Google warns that mobile API keys can be extracted.")
                .setPositiveButton("OPEN SETUP"){_,_->startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.firebase.google.com/")))}
                .setNegativeButton("CLOSE",null)
                .show()
            return
        }
        liveScope.launch{
            val result=account.signIn()
            if(result.isSuccess) Toast.makeText(this@RenActivity,"Google connected • ${result.getOrNull()}",Toast.LENGTH_LONG).show()
            else {
                val error = result.exceptionOrNull() ?: IllegalStateException()
                AlertDialog.Builder(this@RenActivity)
                    .setTitle("Google sign-in failed")
                    .setMessage(account.friendlyError(error))
                    .setNeutralButton("COPY DIAGNOSTIC") { _, _ ->
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Rovex Google Sign-In diagnostic", account.diagnosticSummary()))
                        Toast.makeText(this@RenActivity, "Diagnostic copied", Toast.LENGTH_SHORT).show()
                    }
                    .setPositiveButton("OK",null)
                    .show()
            }
        }
    }

    private fun researchWithGemini(input:EditText, answer:TextView, modelPill:TextView){
        val cmd=input.text?.toString().orEmpty().trim()
        if(cmd.isBlank()){ answer.text="Ask a research question first."; return }
        geminiJob?.cancel()
        val account=GoogleAccountManager(this)
        val current=account.currentEmail()
        if(current==null){
            answer.text="Google sign-in required for Gemini web research…"
            geminiJob=liveScope.launch{
                val login=account.signIn()
                if(login.isFailure){
                    answer.text="Google sign-in could not be completed: ${GoogleAccountManager(this@RenActivity).friendlyError(login.exceptionOrNull() ?: IllegalStateException())}"
                    liveStatus?.text="GEMINI • SIGN-IN FAILED"
                    return@launch
                }
                runGeminiResearch(cmd,answer,modelPill,login.getOrNull())
            }
        } else {
            runGeminiResearch(cmd,answer,modelPill,current)
        }
    }

    private fun runGeminiResearch(cmd:String, answer:TextView, modelPill:TextView, email:String?){
        answer.text="Ben + Gemini are researching…"
        geminiSearchEntry?.visibility=View.GONE
        liveStatus?.text="BEN • GEMINI WEB RESEARCH"
        modelPill.text="Google account • ${email ?: "signed in"}"
        geminiJob=liveScope.launch{
            val result=runCatching{BenGeminiCoordinator(this@RenActivity).research(cmd)}.getOrElse{
                BenGeminiCoordinator.Result("Gemini research failed: ${it.message ?: "unknown error"}", emptyList(), false, email)
            }
            if(isFinishing||isDestroyed)return@launch
            answer.text=buildString{
                append(result.answer)
                if(result.grounded && result.sources.isNotEmpty()){
                    append("\n\nSOURCES")
                    result.sources.forEach{source->append("\n[${source.citationIndex}] ${source.title}\n${source.uri}")}
                }
            }
            result.searchEntryPointHtml?.takeIf{it.isNotBlank()}?.let{html->
                geminiSearchEntry?.visibility=View.VISIBLE
                geminiSearchEntry?.loadDataWithBaseURL("https://www.google.com/",html,"text/html","UTF-8",null)
            }
            liveStatus?.text="BEN • ${if(result.grounded)"GEMINI GROUNDED" else "GEMINI RESPONSE"} • ${result.sources.size} sources"
            modelPill.text="Gemini • ${result.signedInEmail ?: email ?: "signed out"}"
            AppManagers.renMemory.putStudy(answer.text.toString())
        }
    }

    private fun generateChatVisual(input:EditText, answer:TextView, modelPill:TextView){
        val prompt=input.text?.toString().orEmpty().trim()
        if(prompt.isBlank()){answer.text="Give Ben a focused topic first, then tap VISUAL.";return}
        answer.text="Gemini is creating a focused medical study visual…"
        modelPill.text="Gemini visual • generating"
        geminiJob?.cancel()
        geminiJob=liveScope.launch{
            val result=BenGeminiImageGenerator(this@RenActivity).generate("Create a clean exam-oriented medical visual for this study request. Prefer a labeled mechanism diagram, diagnostic algorithm, comparison table, anatomy/pathology schematic, or treatment flowchart as appropriate. Avoid decorative content. Topic: ${prompt.take(4000)}")
            if(isFinishing||isDestroyed)return@launch
            result.onSuccess{bitmap->chatMedia?.setImageBitmap(bitmap);chatMedia?.visibility=View.VISIBLE;answer.text="Gemini visual ready. This visual is a study aid; verify clinical facts against the grounded text and QBank evidence.";modelPill.text="Gemini visual • ready"}
                .onFailure{answer.text="Gemini visual unavailable: ${it.message ?: "unknown error"}\n\nUse GEMINI for grounded text research instead.";modelPill.text="Gemini • fallback"}
        }
    }

    private fun rounded(c:Int,r:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
    override fun onDestroy(){liveJob?.cancel();geminiJob?.cancel();geminiSearchEntry?.stopLoading();geminiSearchEntry?.destroy();geminiSearchEntry=null;chatMedia?.setImageDrawable(null);chatMedia=null;liveScope.cancel();super.onDestroy()}
}
