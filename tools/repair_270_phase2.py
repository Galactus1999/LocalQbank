from pathlib import Path
import re,sys,subprocess
R=Path(sys.argv[1] if len(sys.argv)>1 else ".")

def one(n):
    p=[x for x in R.rglob(n) if "app/src/main" in x.as_posix()]
    if len(p)!=1: raise SystemExit(f"ERROR: expected one {n}, found {len(p)}")
    return p[0]

# Extend the existing BenResponsePolicy owner with generic CSS/HTML-wrapper sanitization.
p=one("BenResponsePolicy.kt"); s=p.read_text()
start=s.index("object BenResponsePolicy {")
s=s[:start]+'''object BenResponsePolicy {
    const val MAX_RESPONSE_CHARS = 16_384
    private val styleBlock = Regex("""(?is)<style\\b[^>]*>.*?</style\\s*>""")
    private val scriptBlock = Regex("""(?is)<script\\b[^>]*>.*?</script\\s*>""")
    private val htmlWrapper = Regex("""(?is)</?(?:!doctype|html|head|body|main|section|article|div)(?:\\s+[^>]*)?>""")
    private val cssProperty = Regex("""(?i)\\b(?:font-family|font-size|line-height|margin(?:-[a-z]+)?|padding(?:-[a-z]+)?|color|background(?:-[a-z]+)?|border(?:-[a-z]+)?|border-radius|display|width|height|min-width|max-width|min-height|max-height|overflow|text-align|vertical-align|letter-spacing|font-weight|text-transform|object-fit|box-shadow|position|top|right|bottom|left|content)\\s*:""")
    private val cssSelector = Regex("""(?is)(?:[a-z][a-z0-9_-]*|[.#][a-z][a-z0-9_-]*)(?:\\s*(?:,|>|\\+|~)\\s*(?:[a-z][a-z0-9_-]*|[.#][a-z][a-z0-9_-]*))*""")
    private val cssRule = Regex("""(?s)(?:^|(?<=}))\\s*([^{}]{1,240})\\{([^{}]{1,5000})\\}""")
    fun normalize(raw: String?): String? {
        var x = raw?.replace("\\r\\n","\\n")?.replace("\\r","\\n") ?: return null
        x = x.replace(styleBlock," ").replace(scriptBlock," ").replace(htmlWrapper," ")
        repeat(5) {
            x = cssRule.replace(x) { m ->
                val selector = m.groupValues[1].trim()
                val declarations = m.groupValues[2]
                if (cssSelector.matches(selector) && cssProperty.containsMatchIn(declarations)) " " else m.value
            }
        }
        return BoundedTextPolicy.normalize(x, MAX_RESPONSE_CHARS)
    }
}
'''
p.write_text(s)
p=one("BenQuestionAiContextDialog.kt"); s=p.read_text()
s=s.replace("${sanitizeRovexAiDisplayText(it.text)}","${BenResponsePolicy.normalize(it.text).orEmpty()}")
p.write_text(s)

# Shared AMOLED-safe HTML span sanitizer for questions, explanations, answers and options.
p=one("QuizActivity.kt"); s=p.read_text()
if "private fun themeSafeSpanned(html: String): Spanned" not in s:
    helper='''    private fun themeSafeSpanned(html: String): Spanned {
        val sanitized = html
            .replace(Regex("""(?is)<font\\s+[^>]*color\\s*=\\s*['"][^'"]+['"][^>]*>"""), "")
            .replace(Regex("""(?is)</font>"""), "")
            .replace(Regex("""(?is)style\\s*=\\s*['"][^'"]*color\\s*:[^;"]+;?[^'"]*['"]"""), "")
        val parsed = Html.fromHtml(sanitized, Html.FROM_HTML_MODE_LEGACY)
        if (parsed !is android.text.Spannable) return parsed
        val out = android.text.SpannableString(parsed)
        out.getSpans(0, out.length, android.text.style.ForegroundColorSpan::class.java).forEach { out.removeSpan(it) }
        return out
    }

'''
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

# Route the actual Frankenstein display cleaner through the upgraded generic policy.
p=one("RenActivity.kt"); s=p.read_text()
a=s.index("    private fun cleanAiAnswer")
b=s.index("\n    private fun currentPromptForContext()",a)
s=s[:a]+"    private fun cleanAiAnswer(raw:String):String = BenResponsePolicy.normalize(raw).orEmpty().trim()"+s[b:]
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

# Regression tests for the exact CSS-leak classes that previously escaped the fixed selector list.
t=R/"app/src/test/java/com/localqbank/library/BenResponsePolicyPhase2Test.kt"
t.parent.mkdir(parents=True,exist_ok=True)
t.write_text("""package com.localqbank.library
import org.junit.Assert.*
import org.junit.Test
class BenResponsePolicyPhase2Test {
    @Test fun stripsGenericCssSelectorsButKeepsClinicalText() {
        val raw = "body{font-size:16px;color:#000} th{background:#111;color:#fff} mark{padding:2px} .card{margin:8px} .label{font-weight:bold} img{width:100%}\\nClinical concepts: malignancy\\nFound 18 relevant local questions."
        val out = BenResponsePolicy.normalize(raw).orEmpty()
        assertFalse(out.contains("body{"))
        assertFalse(out.contains("th{"))
        assertFalse(out.contains("mark{"))
        assertFalse(out.contains(".card{"))
        assertFalse(out.contains(".label{"))
        assertFalse(out.contains("img{"))
        assertTrue(out.contains("Clinical concepts: malignancy"))
        assertTrue(out.contains("Found 18 relevant local questions."))
    }
    @Test fun stripsHtmlWrappersWithoutDeletingAnswer() {
        val out = BenResponsePolicy.normalize("<html><body><p>Correct answer: beta blocker</p></body></html>").orEmpty()
        assertTrue(out.contains("Correct answer: beta blocker"))
        assertFalse(out.contains("<html>"))
        assertFalse(out.contains("<body>"))
    }
}
""",encoding="utf-8")

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
if "BenResponsePolicy.normalize(it.text).orEmpty()" not in f: raise SystemExit("STABILITY: Free AI dialog normalization missing")
bp=next(r.rglob("BenResponsePolicy.kt")).read_text()
if "cssRule" not in bp or "cssSelector" not in bp or "cssProperty" not in bp: raise SystemExit("STABILITY: generic CSS sanitizer missing")
if "private fun cleanAiAnswer(raw:String):String = BenResponsePolicy.normalize(raw).orEmpty().trim()" not in ren: raise SystemExit("STABILITY: Ren display cleaner not upgraded")
if (r/"app/src/main/java/com/localqbank/library/RovexAiDisplaySanitizer.kt").exists(): raise SystemExit("STABILITY: redundant sanitizer file still generated")
print("Phase-2 stability assertions PASS")
""",encoding="utf-8")
subprocess.check_call([sys.executable,str(a),str(R)])

# Final dashboard normalization: HTML-reference hierarchy, subject aggregation, and compile-safe capsule return type.
_dash=one("RovexSectionDashboardActivity.kt")
_ds=_dash.read_text()
_a=_ds.index("private fun qbank(")
_b=_ds.index("private fun openSearch()",_a)
_ds=_ds[:_a]+"private fun subjectRows(rows:List<Row>):List<Row>=rows.groupBy{it.path.ifBlank{\"General\"}}.map{(subject,items)->{val f=items.minByOrNull{it.position}?:items.first();Row(subject,items.sumOf{it.total},items.sumOf{it.solved},items.sumOf{it.correct},f.testId,f.position,subject,f.source)}}.sortedBy{it.name.lowercase()}\nprivate fun hero(title:String,subtitle:String,pct:Int,label:String,stats:List<Pair<String,String>>):View{\n    val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16),d(16),d(16),d(15));background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity));cornerRadius=d(24).toFloat()}}\n    val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val ring=FrameLayout(this);ring.addView(ProgressBar(this,null,android.R.attr.progressBarStyleLarge).apply{isIndeterminate=false;max=100;progress=pct;progressTintList=android.content.res.ColorStateList.valueOf(ThemeManager.accent(this@RovexSectionDashboardActivity))},FrameLayout.LayoutParams(d(110),d(110),Gravity.CENTER));ring.addView(TextView(this).apply{text=pct.toString()+\"%\\n\"+label.uppercase();gravity=Gravity.CENTER;textSize=15f;setTypeface(null,Typeface.BOLD);setTextColor(android.graphics.Color.WHITE)},FrameLayout.LayoutParams(d(110),d(110),Gravity.CENTER));top.addView(ring,LinearLayout.LayoutParams(d(120),d(120)));val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(12),0,0,0)};info.addView(TextView(this).apply{text=title;textSize=21f;setTypeface(null,Typeface.BOLD);setTextColor(android.graphics.Color.WHITE)});info.addView(TextView(this).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(4),0,0)});top.addView(info,LinearLayout.LayoutParams(0,-2,1f));box.addView(top)\n    val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,d(12),0,0)};stats.forEach{(k,v)->val x=LinearLayout(this@RovexSectionDashboardActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(d(3),d(7),d(3),d(7));background=android.graphics.drawable.GradientDrawable().apply{setColor(android.graphics.Color.argb(20,255,255,255));cornerRadius=d(13).toFloat()}};x.addView(TextView(this@RovexSectionDashboardActivity).apply{text=k;textSize=8.5f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))});x.addView(TextView(this@RovexSectionDashboardActivity).apply{text=v;textSize=14f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD);setTextColor(android.graphics.Color.WHITE);setPadding(0,d(3),0,0)});row.addView(x,LinearLayout.LayoutParams(0,d(58),1f).apply{setMargins(d(2),0,d(2),0)})};box.addView(row);return box\n}\nprivate fun subjectProgress(r:Row,index:Int,detail:String){val fg=ThemeManager.pastelAccentText(this,index);val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14),d(13),d(14),d(12));background=ThemeManager.transparentSectionDrawable(this@RovexSectionDashboardActivity);isClickable=true;setOnClickListener{if(r.testId.isNotBlank())startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{putExtra(\"testId\",r.testId);putExtra(\"title\",r.name);putExtra(\"position\",r.position);putExtra(\"sectionLabel\",r.path);putExtra(\"practiceMode\",true)})}};val line=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};line.addView(TextView(this@RovexSectionDashboardActivity).apply{text=r.name;textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))},LinearLayout.LayoutParams(0,-2,1f));line.addView(TextView(this@RovexSectionDashboardActivity).apply{text=if(detail.contains(\"mastery\"))r.mastery.toString()+\"%\" else r.accuracy.toString()+\"%\";textSize=13f;setTypeface(null,Typeface.BOLD);setTextColor(fg)});b.addView(line);b.addView(TextView(this).apply{text=detail;textSize=10.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(5),0,0)});b.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=if(detail.contains(\"mastery\"))r.mastery else r.accuracy;progressTintList=android.content.res.ColorStateList.valueOf(fg)},LinearLayout.LayoutParams(-1,d(6)).apply{topMargin=d(9)});content.addView(b,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})}\nprivate fun qbank(rows:List<Row>){title(\"QBank\",\"Main Bank • all subjects combined\");analyticsHero(rows);section(\"SUBJECTS\");val subs=subjectRows(rows);if(subs.isEmpty())card(\"No imported QBank yet\",\"Import content first; this view never invents question counts.\",ThemeManager.accent(this),\"IMPORT\"){startActivity(Intent(this,HtmlImportActivity::class.java))}else subs.forEachIndexed{i,r->subjectProgress(r,i,r.solved.toString()+\" / \"+r.total+\" attempted • \"+r.accuracy+\"% accuracy\")}}\nprivate fun analyticsHero(rows:List<Row>){val total=rows.sumOf{it.total};val solved=rows.sumOf{it.solved};val correct=rows.sumOf{it.correct};val mastery=if(total==0)0 else solved*100/total;val accuracy=if(solved==0)0 else correct*100/solved;content.addView(hero(\"Main QBank\",\"All subjects combined • \"+subjectRows(rows).size+\" subjects\",mastery,\"mastery\",listOf(\"SOLVED\" to solved.toString(),\"ACCURACY\" to accuracy.toString()+\"%\",\"COVERAGE\" to mastery.toString()+\"%\")),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)});content.addView(TextView(this).apply{text=\"▶  Resume Main Bank\";gravity=Gravity.CENTER;textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.bg(this@RovexSectionDashboardActivity));background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.accent(this@RovexSectionDashboardActivity));cornerRadius=d(26).toFloat()};setPadding(0,d(13),0,d(13));setOnClickListener{rows.firstOrNull()?.let{r->if(r.testId.isNotBlank())startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{putExtra(\"testId\",r.testId);putExtra(\"title\",r.name);putExtra(\"position\",r.position);putExtra(\"practiceMode\",true)})}}},LinearLayout.LayoutParams(-1,d(50)).apply{bottomMargin=d(10)})}\nprivate fun cards(c:Triple<Int,Int,Int>){title(\"Flashcards\",\"Spaced repetition • retention • review\");content.addView(hero(\"Daily Review Due\",c.third.toString()+\" cards due • \"+c.second+\" reviews recorded\",if(c.first==0)0 else (100-(c.third*100/c.first).coerceAtMost(100)),\"due\",listOf(\"TOTAL\" to c.first.toString(),\"REVIEWS\" to c.second.toString(),\"DUE\" to c.third.toString())),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)});card(\"Start Review\",\"Open the existing SRS workflow with the live due count shown above.\",ThemeManager.pastelAccentText(this,1),\"REVIEW\"){startActivity(Intent(this,FlashcardActivity::class.java))};section(\"FLASHCARD LIBRARY\");card(\"Local card database\",c.first.toString()+\" cards • \"+c.second+\" reviews recorded • \"+c.third+\" due now.\",ThemeManager.accent(this),\"OPEN CARDS\"){startActivity(Intent(this,FlashcardActivity::class.java))};card(\"Deck management\",\"Create, import, suspend and review decks in the existing FlashcardActivity.\",ThemeManager.pastelAccentText(this,0),\"OPEN DECKS\"){startActivity(Intent(this,FlashcardActivity::class.java))}}\nprivate fun stats(rows:List<Row>,o:Row){title(\"Performance\",\"Accuracy • solved questions • subject analysis\");content.addView(hero(\"Performance\",o.solved.toString()+\" solved questions • live local progress\",o.accuracy,\"accuracy\",listOf(\"CORRECT\" to o.correct.toString(),\"WRONG\" to (o.solved-o.correct).coerceAtLeast(0).toString(),\"UNATTEMPTED\" to (o.total-o.solved).coerceAtLeast(0).toString())),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)});section(\"SUBJECT PERFORMANCE\");val subs=subjectRows(rows);if(subs.isEmpty())card(\"No performance data yet\",\"Solve questions to populate this panel from real progress.\",ThemeManager.accent(this),\"OPEN QBANK\"){switchSection(\"qbank\")}else subs.forEachIndexed{i,r->subjectProgress(r,i,r.solved.toString()+\" solved • \"+r.correct+\" correct • \"+(r.solved-r.correct).coerceAtLeast(0)+\" wrong\")}}\nprivate fun mastery(rows:List<Row>,o:Row){title(\"Mastery\",\"Subject coverage • retention • focus areas\");val subs=subjectRows(rows);content.addView(hero(\"Overall Mastery\",o.solved.toString()+\" / \"+o.total+\" questions attempted • \"+subs.size+\" subjects\",o.mastery,\"overall\",listOf(\"SUBJECTS\" to subs.size.toString(),\"ATTEMPTED\" to o.solved.toString(),\"ACCURACY\" to o.accuracy.toString()+\"%\")),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)});section(\"SUBJECT MASTERY\");if(subs.isEmpty())card(\"No imported subjects\",\"Import content to build the mastery map.\",ThemeManager.accent(this),\"IMPORT\"){startActivity(Intent(this,HtmlImportActivity::class.java))}else subs.forEachIndexed{i,r->subjectProgress(r,i,r.mastery.toString()+\"% mastery • \"+r.solved+\" / \"+r.total+\" attempted\")};section(\"FOCUS AREAS\");subs.sortedBy{it.mastery}.take(minOf(3,subs.size)).forEachIndexed{i,r->card(r.name,r.mastery.toString()+\"% mastery • \"+(r.total-r.solved).coerceAtLeast(0)+\" questions remaining\",ThemeManager.pastelAccentText(this,i+1),\"FOCUS\"){if(r.testId.isNotBlank())startActivity(Intent(this,QuizActivity::class.java).apply{putExtra(\"testId\",r.testId);putExtra(\"title\",r.name);putExtra(\"position\",r.position);putExtra(\"sectionLabel\",r.path);putExtra(\"practiceMode\",true)})}}}\nprivate fun nav():LinearLayout{val l=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(d(7),d(7),d(7),d(7));background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity));cornerRadius=d(30).toFloat()}};listOf(\"home\" to \"Home\",\"qbank\" to \"QBank\",\"cards\" to \"Cards\",\"stats\" to \"Stats\",\"mastery\" to \"Mastery\").forEach{(id,label)->l.addView(TextView(this).apply{tag=id;text=label;gravity=Gravity.CENTER;textSize=10f;setTypeface(null,Typeface.BOLD);setPadding(d(2),0,d(2),0);setOnClickListener{switchSection(id)}},LinearLayout.LayoutParams(0,-1,1f).apply{setMargins(d(2),0,d(2),0)})};return l}"+"\n"+_ds[_b:]
_dash.write_text(_ds)
assert "private fun nav():LinearLayout" in _ds
assert "private fun subjectRows(rows:List<Row>)" in _ds
assert "private fun cards(c:Triple<Int,Int,Int>)" in _ds
assert "private fun stats(rows:List<Row>,o:Row)" in _ds
assert "private fun mastery(rows:List<Row>,o:Row)" in _ds
print("FINAL DASHBOARD NORMALIZATION PASS")
print("8.3.270 Phase-2 stability repair PASS")
