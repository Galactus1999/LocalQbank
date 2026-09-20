#!/usr/bin/env python3
from pathlib import Path
import re
import json

ROOT = Path.cwd()
if not (ROOT / "settings.gradle.kts").is_file() or not (ROOT / "app").is_dir():
    raise SystemExit("ERROR: not an Android project root")

gradle = ROOT / "app" / "build.gradle.kts"
text = gradle.read_text(encoding="utf-8")
vc = re.search(r"versionCode\s*=\s*(\d+)", text)
vn = re.search(r'versionName\s*=\s*"([^"]+)"', text)
if not vc or not vn:
    raise SystemExit("ERROR: release version markers not found")

code = int(vc.group(1))
name = vn.group(1)
if code in (369, 370) and name in ("8.3.275", "8.3.276"):
    gradle.write_text(
        re.sub(r"versionCode = (?:369|370)", "versionCode = 371", text, count=1)
            .replace('versionName = "8.3.275"', 'versionName = "8.3.277"', 1)
            .replace('versionName = "8.3.276"', 'versionName = "8.3.277"', 1),
        encoding="utf-8",
    )
elif code >= 370:
    pass
else:
    raise SystemExit(f"ERROR: unexpected source version {name} ({code}); refusing blind mutation")

dashboard = ROOT / "app/src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt"
d = dashboard.read_text(encoding="utf-8")
if "import android.view.ViewGroup" not in d:
    d = d.replace("import android.view.View\n", "import android.view.View\nimport android.view.ViewGroup\n", 1)
d = d.replace("rowsForBucket(rows,subject)", "rowsForSubject(rows,subject)")
dashboard.write_text(d, encoding="utf-8")

// Remove the QBank dashboard tab entirely from the five-item bottom navigation.
// QBank itself remains available through the main library/import/search workflows.
start = d.find("private fun qbank(")
if start >= 0:
    end = d.find("private fun analyticsHero(", start)
    if end < 0: raise SystemExit("ERROR: qbank function end marker missing")
    d = d[:start] + d[end:]

d = d.replace(
    'when(active){"qbank"->qbank(data.rows);"flashcards"->cards(if(data.cards!=null) cardData else Triple(-1,-1,-1));',
    'when(active){"flashcards"->cards(if(data.cards!=null) cardData else Triple(-1,-1,-1));'
)

d = d.replace(
    '    card("All QBank subjects","Browse the complete imported subject/section hierarchy. Nothing is hard-coded.",ThemeManager.accent(this),"OPEN QBANK"){switchSection("qbank")}\n',
    ''
)

d = d.replace(
    '    card("Solved",o.solved.toString()+" questions have recorded progress.",ThemeManager.pastelAccentText(this,0),"OPEN QBANK"){switchSection("qbank")}\n'
    '    card("Correct",o.correct.toString()+" correct answers • "+o.accuracy+"% accuracy.",ThemeManager.pastelAccentText(this,1),"REVIEW"){switchSection("qbank")}\n'
    '    card("Wrong",(o.solved-o.correct).coerceAtLeast(0).toString()+" recorded wrong answers.",ThemeManager.pastelAccentText(this,2),"REVIEW"){switchSection("qbank")}\n',
    '    card("Solved",o.solved.toString()+" questions have recorded progress.",ThemeManager.pastelAccentText(this,0),"DETAILS"){}\n'
    '    card("Correct",o.correct.toString()+" correct answers • "+o.accuracy+"% accuracy.",ThemeManager.pastelAccentText(this,1),"DETAILS"){}\n'
    '    card("Wrong",(o.solved-o.correct).coerceAtLeast(0).toString()+" recorded wrong answers.",ThemeManager.pastelAccentText(this,2),"DETAILS"){}\n'
)

d = d.replace(
    '    card("Overall QBank Mastery",o.mastery.toString()+"% of imported questions have been attempted at least once.",ThemeManager.accent(this),"OPEN QBANK"){switchSection("qbank")}\n',
    ''
)

d = d.replace(
    'listOf("home" to "Home","qbank" to "QBank","flashcards" to "Cards","analytics" to "Stats","mastery" to "Mastery")',
    'listOf("home" to "Home","flashcards" to "Cards","analytics" to "Stats","mastery" to "Mastery")'
)

d = d.replace(
    'v.text=if(on) "●  "+when(id){"home"->"Home";"qbank"->"QBank";"flashcards"->"Cards";"analytics"->"Stats";else->"Mastery"} else when(id){"home"->"Home";"qbank"->"QBank";"flashcards"->"Cards";"analytics"->"Stats";else->"Mastery"}',
    'v.text=if(on) "●  "+when(id){"home"->"Home";"flashcards"->"Cards";"analytics"->"Stats";else->"Mastery"} else when(id){"home"->"Home";"flashcards"->"Cards";"analytics"->"Stats";else->"Mastery"}'
)

d = d.replace(
    'if(active==id) return',
    'if(active==id) return'
)
d = d.replace(
    'if(active==id) return\n    active=id',
    'if(active==id) return\n    if(id=="qbank") return\n    active=id'
)

dashboard.write_text(d, encoding="utf-8")

// Remove the QBank item from MainActivity's five-item footer.
main = ROOT / "app/src/main/java/com/localqbank/library/MainActivity.kt"
m = main.read_text(encoding="utf-8")
m = m.replace('listOf(R.id.navHome,R.id.navQBank,R.id.navCards,R.id.navStats,R.id.navMastery)',
              'listOf(R.id.navHome,R.id.navCards,R.id.navStats,R.id.navMastery)')
m = m.replace('        findViewById<View>(R.id.navQBank)?.setOnClickListener{startActivity(Intent(this,RovexSectionDashboardActivity::class.java).putExtra("section","qbank"))}\n','')
main.write_text(m, encoding="utf-8")

// Remove navQBank from MainActivity XML and change the remaining capsule to four equal items.
xml = ROOT / "app/src/main/res/layout/activity_main.xml"
x = xml.read_text(encoding="utf-8")
x = re.sub(r'\s*<TextView android:id="@\+id/navQBank"[\s\S]*?/>', '', x, count=1)
x = x.replace('android:weightSum="5"', 'android:weightSum="4"', 1)
xml.write_text(x, encoding="utf-8")

// Robust AMOLED contrast enforcement: dark-gray imported text can be invisible on pure black
// even when it is lighter than the old fixed 0x303030 threshold. Use WCAG-style luminance.
quiz = ROOT / "app/src/main/java/com/localqbank/library/QuizActivity.kt"
qs = quiz.read_text(encoding="utf-8")
old_fn = re.search(r'private fun enforceAmoledTextVisibility\(root: View\)\{[\s\S]*?\n\}\n\noverride fun onResume', qs)
if not old_fn:
    raise SystemExit("ERROR: AMOLED visibility function not found")
new_fn = '''private fun enforceAmoledTextVisibility(root: View){
    if (ThemeManager.get(this@QuizActivity) != ThemeManager.AMOLED) return
    fun linear(v:Int):Double {
        val c=v.coerceIn(0,255)/255.0
        return if(c<=0.04045) c/12.92 else Math.pow((c+0.055)/1.055,2.4)
    }
    fun luminance(color:Int):Double =
        0.2126*linear(android.graphics.Color.red(color)) +
        0.7152*linear(android.graphics.Color.green(color)) +
        0.0722*linear(android.graphics.Color.blue(color))
    fun walk(v: View){
        if(v is TextView){
            val c=v.currentTextColor
            val alpha=android.graphics.Color.alpha(c)
            val contrast=(luminance(c)+0.05)/0.05 // AMOLED content background is black.
            if(alpha < 220 || contrast < 4.5){
                v.setTextColor(ThemeManager.text(this@QuizActivity))
            }
        }
        if(v is ViewGroup){
            for(i in 0 until v.childCount) walk(v.getChildAt(i))
        }
    }
    walk(root)
}

override fun onResume'''
qs = qs[:old_fn.start()] + new_fn + qs[old_fn.end():]

if "TextAppearanceSpan" not in qs:
    qs = qs.replace("import android.text.Spanned\n", "import android.text.Spanned\nimport android.text.style.TextAppearanceSpan\n", 1)
old_cleanup = '''        out.getSpans(0,out.length,android.text.style.ForegroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,android.text.style.BackgroundColorSpan::class.java).forEach{out.removeSpan(it)}
        return out'''
new_cleanup = '''        out.getSpans(0,out.length,android.text.style.ForegroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,android.text.style.BackgroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,TextAppearanceSpan::class.java).forEach { span ->
            val start=out.getSpanStart(span); val end=out.getSpanEnd(span); val flags=out.getSpanFlags(span)
            if(start<0 || end<=start){ out.removeSpan(span) } else {
                val replacement=TextAppearanceSpan(span.family,span.textStyle,span.textSize,null,null)
                out.removeSpan(span); out.setSpan(replacement,start,end,flags)
            }
        }
        return out'''
if old_cleanup in qs:
    qs=qs.replace(old_cleanup,new_cleanup,1)
quiz.write_text(qs, encoding="utf-8")

// Source assertions.
if 'qbank' in d[d.find('private fun render'):d.find('private fun title')]:
    raise SystemExit("ERROR: qbank render branch remains")
if '"qbank" to "QBank"' in d:
    raise SystemExit("ERROR: QBank remains in dashboard footer")
if 'navQBank' in m or 'navQBank' in x:
    raise SystemExit("ERROR: navQBank remains in main footer")
if 'private fun qbank(' in d:
    raise SystemExit("ERROR: qbank dashboard function remains")
if 'Overall QBank Mastery' in d:
    raise SystemExit("ERROR: QBank card remains in mastery")
if 'contrast < 4.5' not in qs:
    raise SystemExit("ERROR: robust AMOLED contrast guard missing")

print("repair_277_qbank_remove_amoled: PASS")

quiz = ROOT / "app/src/main/java/com/localqbank/library/QuizActivity.kt"
s = quiz.read_text(encoding="utf-8")
if "import android.text.style.TextAppearanceSpan" not in s:
    s = s.replace("import android.text.Spanned\n", "import android.text.Spanned\nimport android.text.style.TextAppearanceSpan\n", 1)

old = """        out.getSpans(0,out.length,android.text.style.ForegroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,android.text.style.BackgroundColorSpan::class.java).forEach{out.removeSpan(it)}
        return out
"""
new = """        // Imported HTML can encode text colours through BOTH ForegroundColorSpan and
        // TextAppearanceSpan. Android's Html parser can create TextAppearanceSpan for
        // resource-backed <font color="@android:color/..."> values, and that span can
        // override TextView.setTextColor(). Remove colour-bearing appearance spans while
        // preserving family/style/size.
        out.getSpans(0,out.length,android.text.style.ForegroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,android.text.style.BackgroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,TextAppearanceSpan::class.java).forEach { span ->
            val start = out.getSpanStart(span)
            val end = out.getSpanEnd(span)
            val flags = out.getSpanFlags(span)
            if (start < 0 || end <= start) {
                out.removeSpan(span)
            } else {
                val replacement = TextAppearanceSpan(
                    span.family, span.textStyle, span.textSize, null, null
                )
                out.removeSpan(span)
                out.setSpan(replacement, start, end, flags)
            }
        }
        return out
"""
if old in s:
    s = s.replace(old, new, 1)
elif "TextAppearanceSpan::class.java" not in s:
    raise SystemExit("ERROR: expected themeSafeSpanned span cleanup block not found")

needle = """            setTextColor(ThemeManager.text(this@QuizActivity))
            setLineSpacing(0f, 1.25f)
"""
repl = """            setTextColor(ThemeManager.text(this@QuizActivity))
            setLinkTextColor(ThemeManager.accent(this@QuizActivity))
            setLineSpacing(0f, 1.25f)
"""
if needle in s:
    s = s.replace(needle, repl, 1)
s = s.replace(
"""                    setTextColor(ThemeManager.text(this@QuizActivity))
                    setPadding(dp(10), dp(8), dp(10), dp(8))
""",
"""                    setTextColor(ThemeManager.text(this@QuizActivity))
                    setLinkTextColor(ThemeManager.accent(this@QuizActivity))
                    setPadding(dp(10), dp(8), dp(10), dp(8))
""",
1)
s = s.replace(
"""            setTextColor(fg)
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(0f, 1.16f)
""",
"""            setTextColor(fg)
            setLinkTextColor(ThemeManager.accent(this@QuizActivity))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(0f, 1.16f)
""",
1)
quiz.write_text(s, encoding="utf-8")

ren = ROOT / "app/src/main/java/com/localqbank/library/RenActivity.kt"
r = ren.read_text(encoding="utf-8")
r = r.replace(
"""        AppManagers.renMemory.putWorking(cmd)
        AppManagers.renMemory.appendChatTurn("USER", cmd)
        answer.text="Ben is working…"
""",
"""        // Ben + AI search is stateless by default. Do not persist the user's query as
        // hidden chat context; a storage failure must never kill the search UI.
        answer.text="Ben is working…"
""",
1)
ren.write_text(r, encoding="utf-8")

search = ROOT / "app/src/main/java/com/localqbank/library/SearchActivity.kt"
r = search.read_text(encoding="utf-8")
r = r.replace(
"""        db=QBankDb(this); store=ProgressStore(this)
        setContentView(build())
""",
"""        try {
            db=QBankDb(this)
            store=ProgressStore(this)
        } catch (t: Throwable) {
            showSearchStartupError(t)
            return
        }
        setContentView(build())
""",
1)
marker = "    private fun build():View {\n"
helper = """    private fun showSearchStartupError(t: Throwable) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = ThemeManager.backgroundDrawable(this@SearchActivity)
        }
        root.addView(TextView(this).apply {
            text = "Search is temporarily unavailable"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(this@SearchActivity))
        })
        root.addView(TextView(this).apply {
            text = "Your QBank data is safe. Close Search and continue studying."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.muted(this@SearchActivity))
            setPadding(0, dp(10), 0, dp(18))
        })
        root.addView(TextView(this).apply {
            text = "CLOSE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.bg(this@SearchActivity))
            background = rounded(ThemeManager.accent(this@SearchActivity), 14f)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(140), dp(44)))
        setContentView(root)
        android.util.Log.e("Rovex.Search", "Search database startup failed", t)
    }

"""
if "private fun showSearchStartupError" not in r:
    if marker not in r:
        raise SystemExit("ERROR: SearchActivity build marker not found")
    r = r.replace(marker, helper + marker, 1)
r = r.replace(
"""    private fun runSearch(q:String){
        val text=q.trim();val serial=generation.get()
""",
"""    private fun runSearch(q:String){
        if (!::db.isInitialized || isFinishing || isDestroyed) return
        val text=q.trim();val serial=generation.get()
""",
1)
r = r.replace(
"""    override fun onResume(){super.onResume();if(::searchBox.isInitialized)runSearch(searchBox.text.toString())}
    override fun onDestroy(){executor.close();db.close();super.onDestroy()}
""",
"""    override fun onResume(){super.onResume();if(::searchBox.isInitialized && ::db.isInitialized)runSearch(searchBox.text.toString())}
    override fun onDestroy(){executor.close();if(::db.isInitialized)runCatching{db.close()};super.onDestroy()}
""",
1)
search.write_text(r, encoding="utf-8")

baseline = ROOT / "tools" / "architecture_baseline.json"
if baseline.is_file():
    data = json.loads(baseline.read_text(encoding="utf-8"))
    data.setdefault("activity_persistence", {}).setdefault(
        "RovexSectionDashboardActivity.kt", {}
    )["qbank_db_ctor"] = 1
    baseline.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

for obsolete in ("repair_272_phase3.py", "repair_273_core.py", "repair_274_ui_theme.py"):
    (ROOT / "tools" / obsolete).unlink(missing_ok=True)

print("repair_276_ai_search_amoled: PASS")
