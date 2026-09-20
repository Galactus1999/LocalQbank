from pathlib import Path
import re,sys,subprocess
R=Path(sys.argv[1] if len(sys.argv)>1 else ".")

def one(n):
    p=[x for x in R.rglob(n) if "app/src/main" in x.as_posix()]
    if len(p)!=1: raise SystemExit(f"ERROR: expected one {n}, found {len(p)}")
    return p[0]

# Remove redundant sanitizer singleton/file; use existing BenResponsePolicy.
obsolete=R/"app/src/main/java/com/localqbank/library/RovexAiDisplaySanitizer.kt"
if obsolete.exists(): obsolete.unlink()
p=one("BenQuestionAiContextDialog.kt"); s=p.read_text()
s=s.replace("${sanitizeRovexAiDisplayText(it.text)}","${BenResponsePolicy.normalize(it.text).orEmpty()}")
p.write_text(s)

# Shared AMOLED-safe HTML span sanitizer for questions, explanations, answers and options.
p=one("QuizActivity.kt"); s=p.read_text()
if "private fun themeSafeSpanned(html: String): Spanned" not in s:
    helper="""    private fun themeSafeSpanned(html: String): Spanned {
        val sanitized = html
            .replace(Regex("(?is)<font\\s+[^>]*color\\s*=\\s*['\"][^'\"]+['\"][^>]*>"), "")
            .replace(Regex("(?is)</font>"), "")
            .replace(Regex("(?is)style\\s*=\\s*['\"][^'\"]*color\\s*:[^;\"]+;?[^'\"]*['\"]"), "")
        val parsed = Html.fromHtml(sanitized, Html.FROM_HTML_MODE_LEGACY)
        if (parsed !is android.text.Spannable) return parsed
        val out = android.text.SpannableString(parsed)
        out.getSpans(0, out.length, android.text.style.ForegroundColorSpan::class.java).forEach { out.removeSpan(it) }
        return out
    }

"""
    pos=s.index("    private fun toSpanned(html: String): Spanned")
    s=s[:pos]+helper+s[pos:]
old="""    private fun toSpanned(html: String): Spanned {
        if (html.length > 180_000) return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        synchronized(spannedCache) { spannedCache[html]?.let { return it } }
        val parsed = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        synchronized(spannedCache) { spannedCache[html] = parsed }
        return parsed
    }
"""
new="""    private fun toSpanned(html: String): Spanned {
        if (html.length > 180_000) return themeSafeSpanned(html)
        synchronized(spannedCache) { spannedCache[html]?.let { return it } }
        val parsed = themeSafeSpanned(html)
        synchronized(spannedCache) { spannedCache[html] = parsed }
        return parsed
    }
"""
if old not in s: raise SystemExit("ERROR: QuizActivity toSpanned block changed unexpectedly")
s=s.replace(old,new,1)
s,n=re.subn(r"    private fun optionSpanned\(html:String\): Spanned \{.*?^    \}","    private fun optionSpanned(html:String): Spanned = themeSafeSpanned(html)",s,count=1,flags=re.S|re.M)
if n!=1: raise SystemExit("ERROR: optionSpanned block not found")
p.write_text(s)

# Instantiate existing matched-question action button.
p=one("RenActivity.kt"); s=p.read_text()
if "openMatchButton = TextView(this).apply" not in s:
    needle="""        root.addView(answerScroll,LinearLayout.LayoutParams(-1,0,1f))

        liveStatus=TextView(this).apply {"""
    insert="""        root.addView(answerScroll,LinearLayout.LayoutParams(-1,0,1f))

        openMatchButton = TextView(this).apply {
            text = "OPEN MATCHED QUESTIONS"
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.accent(this@RenActivity))
            background = UiDrawableUtils.roundedDrawable(this@RenActivity, ThemeManager.elevated(this@RenActivity), 12f)
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }
        root.addView(openMatchButton, LinearLayout.LayoutParams(-1, dp(40)).apply { setMargins(0, 0, 0, dp(5)) })

        liveStatus=TextView(this).apply {"""
    if needle not in s: raise SystemExit("ERROR: RenActivity button insertion point missing")
    s=s.replace(needle,insert,1)
p.write_text(s)

# Five-section capsule: immediate repaint and latest-request-wins async commit.
p=one("RovexSectionDashboardActivity.kt"); s=p.read_text()
s=s.replace('private var active="home"','private var active="home"\nprivate var sectionGeneration=0L\nprivate lateinit var navBar:LinearLayout',1)
s=s.replace('override fun onCreate(b:Bundle?){super.onCreate(b);active=intent.getStringExtra("section")?:"home";buildShell();loadLiveData()}',
'''override fun onCreate(b:Bundle?){super.onCreate(b);active=intent.getStringExtra("section")?:"home";buildShell();repaintNav();loadLiveData(sectionGeneration,active)}''',1)
s=s.replace('root.addView(nav(),LinearLayout.LayoutParams(-1,d(68)));setContentView(root)',
'''navBar=nav()
    root.addView(navBar,LinearLayout.LayoutParams(-1,d(68)));setContentView(root)''',1)
s=s.replace('private fun loadLiveData(){',
'''private fun switchSection(id:String){
    active=id
    sectionGeneration++
    repaintNav()
    loadLiveData(sectionGeneration,id)
}
private fun repaintNav(){
    if(!::navBar.isInitialized)return
    for(i in 0 until navBar.childCount){
        (navBar.getChildAt(i) as? TextView)?.let{v->
            val id=v.tag?.toString().orEmpty()
            v.setTextColor(if(active==id)ThemeManager.accent(this) else ThemeManager.muted(this))
            v.background=if(active==id)android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.bg(this@RovexSectionDashboardActivity));cornerRadius=d(20).toFloat()} else null
        }
    }
}
private fun loadLiveData(token:Long=sectionGeneration,requestedSection:String=active){''',1)
s=s.replace('runOnUiThread{if(!isFinishing&&!isDestroyed)render(rows,overall,cards)}',
'runOnUiThread{if(!isFinishing&&!isDestroyed&&token==sectionGeneration&&requestedSection==active)render(rows,overall,cards)}',1)
s=s.replace('{active="stats";render(emptyList(),o,c)}','{switchSection("stats")}')
s=s.replace('{active="cards";render(emptyList(),o,c)}','{switchSection("cards")}')
s=s.replace('{active="qbank";loadLiveData()}','{switchSection("qbank")}')
start=s.index("private fun nav():View{"); end=s.index("private fun openSearch()",start)
nav='''private fun nav():View{
    val l=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(d(7),d(7),d(7),d(7));background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity));cornerRadius=d(30).toFloat()}}
    listOf("home" to "Home","qbank" to "QBank","cards" to "Cards","stats" to "Stats","mastery" to "Mastery").forEach{(id,label)->
        l.addView(TextView(this).apply{tag=id;text=label;gravity=Gravity.CENTER;textSize=10f;setTypeface(null,Typeface.BOLD);setPadding(d(2),0,d(2),0);setOnClickListener{switchSection(id)}},LinearLayout.LayoutParams(0,-1,1f).apply{setMargins(d(2),0,d(2),0)})
    }
    return l
}
private fun openSearch()'''
s=s[:start]+nav+s[end+len("private fun openSearch()"):]
p.write_text(s)

# Monotonic version.
g=R/"app/build.gradle.kts"; gs=g.read_text()
gs=re.sub(r'versionName\s*=\s*"8\.3\.269"','versionName = "8.3.270"',gs,count=1)
gs=re.sub(r'versionCode\s*=\s*363','versionCode = 364',gs,count=1)
g.write_text(gs)

# Deterministic pre-Gradle gate.
a=R/"tools/assert_phase2_stability.py"
a.write_text("""from pathlib import Path
import sys
r=Path(sys.argv[1] if len(sys.argv)>1 else ".")
d=next(r.rglob("RovexSectionDashboardActivity.kt")).read_text()
q=next(r.rglob("QuizActivity.kt")).read_text()
ren=next(r.rglob("RenActivity.kt")).read_text()
f=next(r.rglob("BenQuestionAiContextDialog.kt")).read_text()
if "analyticsHero(rows)" not in d: raise SystemExit("STABILITY: QBank analytical hero missing")
if 'setOnClickListener{if(r.testId.isNotBlank())' not in d: raise SystemExit("STABILITY: QBank subsection touch target missing")
if "sectionGeneration" not in d or "token==sectionGeneration" not in d: raise SystemExit("STABILITY: async generation guard missing")
if "private fun switchSection(id:String)" not in d: raise SystemExit("STABILITY: switchSection missing")
if "setOnClickListener{switchSection(id)}" not in d: raise SystemExit("STABILITY: nav switch path missing")
if "private fun themeSafeSpanned(html: String): Spanned" not in q: raise SystemExit("STABILITY: shared AMOLED sanitizer missing")
if "private fun optionSpanned(html:String): Spanned = themeSafeSpanned(html)" not in q: raise SystemExit("STABILITY: options not routed through shared sanitizer")
if "openMatchButton = TextView(this).apply" not in ren: raise SystemExit("STABILITY: matched-question button not instantiated")
if "openMatchButton?.setOnClickListener" not in ren: raise SystemExit("STABILITY: matched-question action wiring missing")
if "BenResponsePolicy.normalize(it.text).orEmpty()" not in f: raise SystemExit("STABILITY: Free AI display normalization missing")
if (r/"app/src/main/java/com/localqbank/library/RovexAiDisplaySanitizer.kt").exists(): raise SystemExit("STABILITY: redundant sanitizer file still generated")
print("Phase-2 stability assertions PASS")
""",encoding="utf-8")
subprocess.check_call([sys.executable,str(a),str(R)])
print("8.3.270 Phase-2 stability repair PASS")
