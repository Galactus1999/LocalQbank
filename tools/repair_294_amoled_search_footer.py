from pathlib import Path
import sys

R=Path(sys.argv[1])

def one(name):
    xs=list(R.rglob(name))
    if len(xs)!=1:
        raise SystemExit(f"V294: expected one {name}, found {len(xs)}")
    return xs[0]

# AMOLED: never encode theme colours as ForegroundColorSpan. Imported colour spans are
# stripped centrally; the TextView base colour remains the single theme authority.
p=one("QuizRichTextRenderer.kt")
s=p.read_text()
old='''        forcedColor?.let { color ->
            if (out.isNotEmpty()) {
                // A ForegroundColorSpan is authoritative over TextView.setTextColor().
                // Install the final theme color only after every imported color span is gone.
                out.setSpan(ForegroundColorSpan(color), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
'''
new='''        // Theme colours must never be represented as spans. Android gives text spans higher
        // precedence than TextView.setTextColor(), so a stale theme span can survive a theme
        // switch and make imported quiz text unreadable on AMOLED.
        @Suppress("UNUSED_VARIABLE") val ignoredThemeColor = forcedColor
'''
if old not in s:
    raise SystemExit("V294: QuizRichTextRenderer span block changed")
p.write_text(s.replace(old,new,1))

p=one("QuizActivity.kt")
s=p.read_text()
start=s.index("private fun enforceAmoledTextVisibility(root: View){")
end=s.index("\noverride fun onResume()",start)
block='''private fun enforceAmoledTextVisibility(root: View){
    // Theme changes can happen while QuizActivity is stopped behind SettingsActivity.
    // Reapply semantic base colours on every resume; never inject a theme colour span.
    fun desiredColor(v: TextView): Int? = when(v.tag?.toString().orEmpty()){
        "quiz:question" -> ThemeManager.questionText(this@QuizActivity)
        "quiz:explanation" -> ThemeManager.explanationText(this@QuizActivity)
        "quiz:ai" -> ThemeManager.aiText(this@QuizActivity)
        "quiz:option" -> ThemeManager.optionText(this@QuizActivity)
        "quiz:option:correct" -> ThemeManager.correctText(this@QuizActivity)
        "quiz:option:wrong" -> ThemeManager.wrongText(this@QuizActivity)
        "quiz:option:neutral" -> ThemeManager.muted(this@QuizActivity)
        "quiz:option-label" -> ThemeManager.optionText(this@QuizActivity)
        "quiz:option-label:correct" -> ThemeManager.correctText(this@QuizActivity)
        "quiz:option-label:wrong" -> ThemeManager.wrongText(this@QuizActivity)
        "quiz:option-label:neutral" -> ThemeManager.muted(this@QuizActivity)
        else -> null
    }
    fun walk(v: View){
        if(v is TextView){
            val spanned=v.text as? android.text.Spanned
            if(spanned!=null && spanned.isNotEmpty()){
                val sp=android.text.SpannableString(spanned)
                quizRichTextRenderer.sanitizeColorSpans(sp)
                v.text=sp
            }
            desiredColor(v)?.let{v.setTextColor(it)}
            v.invalidate()
        }
        if(v is ViewGroup) for(i in 0 until v.childCount) walk(v.getChildAt(i))
    }
    walk(root)
}
'''
s=s[:start]+block+s[end:]
s=s.replace('addHtmlText(q.text, 20f * fontScale, true, ThemeManager.questionText(this@QuizActivity))',
            'addHtmlText(q.text, 20f * fontScale, true, ThemeManager.questionText(this@QuizActivity), role="question")')
s=s.replace('addHtmlText(q.explanation ?: "<p>No explanation available.</p>",17f*fontScale,false,ThemeManager.explanationText(this@QuizActivity),q.id)',
            'addHtmlText(q.explanation ?: "<p>No explanation available.</p>",17f*fontScale,false,ThemeManager.explanationText(this@QuizActivity),q.id,"explanation")')
s=s.replace('addHtmlText(q.explanation ?: "<p>No explanation available.</p>", 17f * fontScale, false, ThemeManager.explanationText(this@QuizActivity), q.id)',
            'addHtmlText(q.explanation ?: "<p>No explanation available.</p>", 17f * fontScale, false, ThemeManager.explanationText(this@QuizActivity), q.id, "explanation")')
s=s.replace('private fun addHtmlText(html: String?, size: Float, bold: Boolean = false, color: Int? = null, noteQuestionId: Long? = null)',
            'private fun addHtmlText(html: String?, size: Float, bold: Boolean = false, color: Int? = null, noteQuestionId: Long? = null, role: String = "generic")')
s=s.replace('addRichHtmlWithTables(cleaned, size, bold, color, noteQuestionId)',
            'addRichHtmlWithTables(cleaned, size, bold, color, noteQuestionId, role)')
s=s.replace('addSelectableHtmlText(cleaned, size, bold, color, noteQuestionId)',
            'addSelectableHtmlText(cleaned, size, bold, color, noteQuestionId, role)')
s=s.replace('private fun addRichHtmlWithTables(html: String, size: Float, bold: Boolean, color: Int?, noteQuestionId: Long?)',
            'private fun addRichHtmlWithTables(html: String, size: Float, bold: Boolean, color: Int?, noteQuestionId: Long?, role: String)')
s=s.replace('addSelectableHtmlText(before, size, bold, color, noteQuestionId)',
            'addSelectableHtmlText(before, size, bold, color, noteQuestionId, role)')
s=s.replace('addNativeHtmlTable(m.value, size, color)',
            'addNativeHtmlTable(m.value, size, color, role)')
s=s.replace('addSelectableHtmlText(after, size, bold, color, noteQuestionId)',
            'addSelectableHtmlText(after, size, bold, color, noteQuestionId, role)')
s=s.replace('private fun addSelectableHtmlText(html: String, size: Float, bold: Boolean, color: Int?, noteQuestionId: Long?)',
            'private fun addSelectableHtmlText(html: String, size: Float, bold: Boolean, color: Int?, noteQuestionId: Long?, role: String)')
s=s.replace('''val t = TextView(this).apply {
            text = quizRichTextRenderer.themeSafeSpanned''',
            '''val t = TextView(this).apply {
            tag = "quiz:" + role
            text = quizRichTextRenderer.themeSafeSpanned''')
s=s.replace('private fun addNativeHtmlTable(tableHtml: String, size: Float, color: Int?)',
            'private fun addNativeHtmlTable(tableHtml: String, size: Float, color: Int?, role: String)')
s=s.replace('''val tv = TextView(this).apply {
                    text = quizRichTextRenderer.themeSafeSpanned''',
            '''val tv = TextView(this).apply {
                    tag = "quiz:" + role
                    text = quizRichTextRenderer.themeSafeSpanned''')
s=s.replace('''val letter = TextView(this).apply {
            text = option.label''',
            '''val letter = TextView(this).apply {
            tag = "quiz:option-label:" + if(answered && correct) "correct" else if(answered && selected) "wrong" else "neutral"
            text = option.label''')
s=s.replace('''val text = TextView(this).apply {
            this.text = quizRichTextRenderer.optionSpanned''',
            '''val text = TextView(this).apply {
            tag = "quiz:option:" + if(answered && correct) "correct" else if(answered && selected) "wrong" else "neutral"
            this.text = quizRichTextRenderer.optionSpanned''')
s=s.replace('(row.getChildAt(0) as? TextView)?.apply { setTextColor(fg); background=rounded(bg,50f) }',
            '(row.getChildAt(0) as? TextView)?.apply { tag="quiz:option-label:" + if(isCorrect) "correct" else if(isSelected) "wrong" else "neutral"; setTextColor(fg); background=rounded(bg,50f) }')
s=s.replace('(row.getChildAt(1) as? TextView)?.setTextColor(fg)',
            '(row.getChildAt(1) as? TextView)?.apply { tag="quiz:option:" + if(isCorrect) "correct" else if(isSelected) "wrong" else "neutral"; setTextColor(fg) }')
s=s.replace('''val aiContext = TextView(this).apply {
            text = "✦  BEN + AI • EXAM CONTEXT"''',
            '''val aiContext = TextView(this).apply {
            tag = "quiz:ai"
            text = "✦  BEN + AI • EXAM CONTEXT"''')
s=s.replace('''val answerAiContext = TextView(this).apply {
                text = "✦  BEN + AI • ANSWER & DISTRACTOR CONTEXT"''',
            '''val answerAiContext = TextView(this).apply {
                tag = "quiz:ai"
                text = "✦  BEN + AI • ANSWER & DISTRACTOR CONTEXT"''')
p.write_text(s)

# Frankenstein search: explicit retrieval commands bypass neural generation/model startup.
p=one("ExperiencePerformanceManager.kt")
s=p.read_text()
old='''                val actionCommand = isActionCommand(command)
                val hasNeuralArtifact = neuralModels.installed(BenNeuralModelRegistry.embeddingGemma300m) != null ||
                    neuralModels.installed(BenNeuralModelRegistry.gemma3_270m) != null
                val useNeural = !actionCommand && neuralPolicy.enabled && !neuralPolicy.circuitOpen && hasNeuralArtifact'''
new='''                val actionCommand = isActionCommand(command)
                val localSearchCommand = isLocalSearchCommand(command)
                val hasNeuralArtifact = neuralModels.installed(BenNeuralModelRegistry.embeddingGemma300m) != null ||
                    neuralModels.installed(BenNeuralModelRegistry.gemma3_270m) != null
                // Local retrieval is authoritative for search commands. Neural models are
                // accelerators for explanatory requests, never prerequisites for finding QBank hits.
                val useNeural = !actionCommand && !localSearchCommand && neuralPolicy.enabled &&
                    !neuralPolicy.circuitOpen && hasNeuralArtifact'''
if old not in s: raise SystemExit("V294: Frankenstein useNeural block changed")
s=s.replace(old,new,1)
needle='    private fun isActionCommand(command: String): Boolean {'
insert='''    private fun isLocalSearchCommand(command: String): Boolean {
        val q=command.lowercase()
        return listOf("find","search","related question","related questions","similar question",
            "similar questions","matching question","matching questions","show questions",
            "give me questions","qbank questions","previous question","pyq").any{q.contains(it)}
    }

'''
if needle not in s: raise SystemExit("V294: Frankenstein router point missing")
s=s.replace(needle,insert+needle,1)
p.write_text(s)

# Retrieval fan-out: retain primary query + seven expansions, cap each FTS retrieval at 180.
p=one("RenCognitiveEngine.kt")
s=p.read_text()
s=s.replace('val variants = understanding.expansions.take(24)',
'''val variants = linkedSetOf<String>().apply {
            add(query)
            understanding.expansions.take(7).forEach { add(it) }
        }.filter { it.isNotBlank() }.take(8)''')
s=s.replace('db.search(variant, 500).forEach { hit ->','db.search(variant, 180).forEach { hit ->')
p.write_text(s)

# Home footer: never mark QBank active; reapply theme on every resume; only the capsule border glows.
p=one("MainActivity.kt")
s=p.read_text()
old='''    override fun onResume(){super.onResume(); mainViewModel.prepareForDisplay(); renderImmediateShell(); mainViewModel.refreshSourcesFast(); mainViewModel.refresh()}'''
new='''    override fun onResume(){
        super.onResume()
        if(isFinishing || isDestroyed)return
        mainViewModel.prepareForDisplay()
        renderImmediateShell()
        styleHeaderControls()
        styleDashboardCards()
        applyDashboardTheme()
        setupRovexBottomNav()
        mainViewModel.refreshSourcesFast()
        mainViewModel.refresh()
    }'''
if old not in s: raise SystemExit("V294: MainActivity onResume block changed")
s=s.replace(old,new,1)
start=s.index('    private fun setupRovexBottomNav(){')
end=s.index('\n    private fun scrollHomeTop()',start)
nav='''    private fun setupRovexBottomNav(){
        val nav=findViewById<View>(R.id.rovexBottomNav) ?: return
        val dark=ThemeManager.isDark(this@MainActivity)
        val accent=ThemeManager.accent(this@MainActivity)
        nav.background=GradientDrawable().apply{
            setColor(ThemeManager.elevated(this@MainActivity))
            cornerRadius=28f*resources.displayMetrics.density
            setStroke(dp(1),Color.argb(if(dark)115 else 90,Color.red(accent),Color.green(accent),Color.blue(accent)))
        }
        listOf(R.id.navQBank,R.id.navCards,R.id.navMastery).forEach{id->
            (findViewById<View>(id) as? TextView)?.apply{
                // Home is not one of the three study sections: no tab is active here.
                setTextColor(ThemeManager.muted(this@MainActivity))
                background=null
                isSelected=false
                isActivated=false
            }
        }
        fun openSection(id:String){
            val intent=Intent(this,RovexSectionDashboardActivity::class.java).apply{
                putExtra("section",id)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.navQBank)?.setOnClickListener{openSection("qbank")}
        findViewById<View>(R.id.navCards)?.setOnClickListener{openSection("flashcards")}
        findViewById<View>(R.id.navMastery)?.setOnClickListener{openSection("mastery")}
    }
'''
s=s[:start]+nav+s[end:]
p.write_text(s)

# Section dashboard footer: active state is accent-on-theme-background, not inverted white.
p=one("RovexSectionDashboardActivity.kt")
s=p.read_text()
if 'import android.graphics.Color' not in s:
    s=s.replace('import android.app.Activity\n','import android.app.Activity\nimport android.graphics.Color\n')
start=s.index('private fun renderThemeChrome(){')
end=s.index('\nprivate fun d(x:Int)',start)
chrome='''private fun renderThemeChrome(){
    val root=(content.parent?.parent as? FrameLayout) ?: return
    val footer=root.getChildAt(root.childCount-1) as? LinearLayout ?: return
    val accent=ThemeManager.accent(this@RovexSectionDashboardActivity)
    footer.background=android.graphics.drawable.GradientDrawable().apply{
        setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity))
        cornerRadius=d(30).toFloat()
        setStroke(d(1),Color.argb(if(ThemeManager.isDark(this))105 else 80,Color.red(accent),Color.green(accent),Color.blue(accent)))
    }
    for(i in 0 until footer.childCount){
        val v=footer.getChildAt(i) as? TextView ?: continue
        val id=v.tag as? String ?: continue
        val on=id==active
        v.text=when(id){"qbank"->"QBank";"flashcards"->"Cards";else->"Mastery"}
        v.setTextColor(if(on) accent else ThemeManager.muted(this@RovexSectionDashboardActivity))
        v.background=if(on) android.graphics.drawable.GradientDrawable().apply{
            setColor(ThemeManager.bg(this@RovexSectionDashboardActivity))
            cornerRadius=d(22).toFloat()
            setStroke(d(1),accent)
        } else null
    }
}
'''
s=s[:start]+chrome+s[end:]
p.write_text(s)

# Version bump.
p=one("app/build.gradle.kts")
s=p.read_text().replace('versionCode = 387','versionCode = 388').replace('versionName = "8.3.293"','versionName = "8.3.294"')
p.write_text(s)

# Fail closed assertions.
qr=one("QuizRichTextRenderer.kt").read_text()
qa=one("QuizActivity.kt").read_text()
if "out.setSpan(ForegroundColorSpan" in qr or "sp.setSpan(android.text.style.ForegroundColorSpan" in qa:
    raise SystemExit("V294: foreground theme span injection remains")
if not all(x in qa for x in ("quiz:question","quiz:explanation","quiz:option","quiz:ai")):
    raise SystemExit("V294: quiz semantic roles incomplete")
fr=one("ExperiencePerformanceManager.kt").read_text()
rn=one("RenCognitiveEngine.kt").read_text()
if "isLocalSearchCommand" not in fr or "!actionCommand && !localSearchCommand" not in fr:
    raise SystemExit("V294: search/neural separation missing")
if "take(7)" not in rn or "db.search(variant, 180)" not in rn:
    raise SystemExit("V294: retrieval fan-out cap missing")
ma=one("MainActivity.kt").read_text()
rd=one("RovexSectionDashboardActivity.kt").read_text()
if "paint(R.id.navQBank)" in ma:
    raise SystemExit("V294: Home still has active QBank paint")
if "setStroke(dp(1)" not in ma or "setStroke(d(1)" not in rd:
    raise SystemExit("V294: footer border contract missing")
g=one("app/build.gradle.kts").read_text()
if 'versionCode = 388' not in g or 'versionName = "8.3.294"' not in g:
    raise SystemExit("V294: version bump missing")
print("V294_SOURCE_ASSERTIONS_PASS")
