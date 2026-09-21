from pathlib import Path
import re,sys

R=Path(sys.argv[1])

def one(name):
    xs=list(R.rglob(name))
    if len(xs)!=1:
        raise SystemExit(f"V294: expected one {name}, found {len(xs)}")
    return xs[0]

# 1) AMOLED radical cure:
# Imported HTML colours are stripped, and theme colours are NEVER reintroduced as
# ForegroundColorSpan. TextView.setTextColor is the sole foreground-colour authority.
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
new='''        // Theme colour is applied by TextView.setTextColor(), never as a span.
        // Spans have higher precedence and can otherwise preserve an old/imported colour.
        @Suppress("UNUSED_VARIABLE") val ignoredForcedColor = forcedColor
'''
if old not in s:
    raise SystemExit("V294: renderer forced-colour span block changed")
p.write_text(s.replace(old,new,1))

p=one("QuizActivity.kt")
s=p.read_text()
a=s.index("private fun enforceAmoledTextVisibility(root: View){")
b=s.index("\noverride fun onResume()",a)
new_block='''private fun enforceAmoledTextVisibility(root: View){
    // Radical invariant: imported HTML may never own foreground/background colour.
    // TextView.setTextColor()/theme resources are the sole colour authority.
    fun walk(v: View){
        if(v is TextView){
            val spanned=v.text as? android.text.Spanned
            if(spanned!=null && spanned.isNotEmpty()){
                val sp=android.text.SpannableString(spanned)
                quizRichTextRenderer.sanitizeColorSpans(sp)
                v.text=sp
                v.invalidate()
            }
        }
        if(v is ViewGroup) for(i in 0 until v.childCount) walk(v.getChildAt(i))
    }
    walk(root)
}
'''
s=s[:a]+new_block+s[b:]
p.write_text(s)

# 2) Frankenstein search latency:
# Search commands must not initialize/run a neural model before local retrieval.
p=one("ExperiencePerformanceManager.kt")
s=p.read_text()
old='''                val actionCommand = isActionCommand(command)
                val hasNeuralArtifact = neuralModels.installed(BenNeuralModelRegistry.embeddingGemma300m) != null ||
                    neuralModels.installed(BenNeuralModelRegistry.gemma3_270m) != null
                val useNeural = !actionCommand && neuralPolicy.enabled && !neuralPolicy.circuitOpen && hasNeuralArtifact
'''
new='''                val actionCommand = isActionCommand(command)
                val localSearchCommand = isLocalSearchCommand(command)
                val hasNeuralArtifact = neuralModels.installed(BenNeuralModelRegistry.embeddingGemma300m) != null ||
                    neuralModels.installed(BenNeuralModelRegistry.gemma3_270m) != null
                // Search is a deterministic local retrieval operation. Never wait for model
                // initialization/inference before returning matching QBank questions.
                val useNeural = !actionCommand && !localSearchCommand && neuralPolicy.enabled && !neuralPolicy.circuitOpen && hasNeuralArtifact
'''
if old not in s:
    raise SystemExit("V294: ExperiencePerformanceManager neural gate changed")
s=s.replace(old,new,1)
needle='    private fun isActionCommand(command: String): Boolean {'
insert='''    private fun isLocalSearchCommand(command: String): Boolean {
        val q=command.lowercase()
        return listOf("find","search","related question","related questions","similar question",
            "similar questions","matching question","matching questions","show questions",
            "give me questions","qbank questions","previous question","pyq").any{q.contains(it)}
    }

'''
if needle not in s:
    raise SystemExit("V294: local search insertion point missing")
p.write_text(s.replace(needle,insert+needle,1))

# Retrieval fan-out was 24 variants x 500 DB hits. Cap it to eight variants x 180.
p=one("RenCognitiveEngine.kt")
s=p.read_text()
old="val variants = understanding.expansions.take(24)"
new='val variants = linkedSetOf<String>().apply { add(query); understanding.expansions.take(7).forEach { add(it) } }.filter { it.isNotBlank() }.take(8)'
if old not in s:
    raise SystemExit("V294: Ren expansion line changed")
s=s.replace(old,new,1)
old2='db.search(variant, 500).forEach { hit ->'
if old2 not in s:
    raise SystemExit("V294: Ren DB search limit changed")
s=s.replace(old2,'db.search(variant, 180).forEach { hit ->',1)
p.write_text(s)

# 3 + 4) Footer:
# Home has NO selected study section. It only gets an accent border/glow.
p=one("MainActivity.kt")
s=p.read_text()
start=s.index("    private fun setupRovexBottomNav(){")
end=s.index("\n    private fun scrollHomeTop()",start)
nav='''    private fun setupRovexBottomNav(){
        val nav=findViewById<View>(R.id.rovexBottomNav) ?: return
        val accent=ThemeManager.accent(this@MainActivity)
        nav.background=android.graphics.drawable.GradientDrawable().apply{
            setColor(ThemeManager.elevated(this@MainActivity))
            cornerRadius=28f*resources.displayMetrics.density
            setStroke(dp(1),android.graphics.Color.argb(
                if(ThemeManager.isDark(this@MainActivity))115 else 90,
                android.graphics.Color.red(accent),
                android.graphics.Color.green(accent),
                android.graphics.Color.blue(accent)
            ))
        }
        listOf(R.id.navQBank,R.id.navCards,R.id.navMastery).forEach{id->
            (findViewById<View>(id) as? TextView)?.apply{
                setTextColor(ThemeManager.muted(this@MainActivity))
                background=null
                isSelected=false
                isActivated=false
            }
        }
        fun openSection(id:String){
            startActivity(Intent(this,RovexSectionDashboardActivity::class.java).apply{
                putExtra("section",id)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        findViewById<View>(R.id.navQBank)?.setOnClickListener{openSection("qbank")}
        findViewById<View>(R.id.navCards)?.setOnClickListener{openSection("flashcards")}
        findViewById<View>(R.id.navMastery)?.setOnClickListener{openSection("mastery")}
    }
'''
s=s[:start]+nav+s[end:]
p.write_text(s)

# Section dashboard: theme changes repaint the actual footer, not a cached white style.
p=one("RovexSectionDashboardActivity.kt")
s=p.read_text()
start=s.index("private fun renderThemeChrome(){")
end=s.index("\nprivate fun d(x:Int)",start)
chrome='''private fun renderThemeChrome(){
    if(!::navBar.isInitialized)return
    val accent=ThemeManager.accent(this@RovexSectionDashboardActivity)
    navBar.background=android.graphics.drawable.GradientDrawable().apply{
        setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity))
        cornerRadius=d(30).toFloat()
        setStroke(d(1),android.graphics.Color.argb(
            if(ThemeManager.isDark(this@RovexSectionDashboardActivity))105 else 80,
            android.graphics.Color.red(accent),
            android.graphics.Color.green(accent),
            android.graphics.Color.blue(accent)
        ))
    }
    for(i in 0 until navBar.childCount){
        val v=navBar.getChildAt(i) as? TextView ?: continue
        val id=v.tag?.toString().orEmpty()
        val on=id==active
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

# Version.
p=one("app/build.gradle.kts")
s=p.read_text()
s=re.sub(r'versionCode\s*=\s*\d+','versionCode = 388',s,count=1)
s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "8.3.294"',s,count=1)
p.write_text(s)

# Fail-closed source assertions.
if "out.setSpan(ForegroundColorSpan" in one("QuizRichTextRenderer.kt").read_text():
    raise SystemExit("V294: renderer still injects ForegroundColorSpan")
qa=one("QuizActivity.kt").read_text()
if "sp.setSpan(android.text.style.ForegroundColorSpan" in qa:
    raise SystemExit("V294: QuizActivity still injects ForegroundColorSpan")
ep=one("ExperiencePerformanceManager.kt").read_text()
if "localSearchCommand" not in ep or "!actionCommand && !localSearchCommand" not in ep:
    raise SystemExit("V294: search bypass missing")
rn=one("RenCognitiveEngine.kt").read_text()
if "take(7)" not in rn or "db.search(variant, 180)" not in rn:
    raise SystemExit("V294: retrieval cap missing")
ma=one("MainActivity.kt").read_text()
if "paint(R.id.navQBank)" in ma:
    raise SystemExit("V294: Home still selects QBank")
print("V294_SOURCE_ASSERTIONS_PASS")
