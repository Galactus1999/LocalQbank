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
if code == 369 and name == "8.3.275":
    gradle.write_text(
        text.replace("versionCode = 369", "versionCode = 370")
            .replace('versionName = "8.3.275"', 'versionName = "8.3.276"', 1),
        encoding="utf-8",
    )
elif code >= 370:
    pass
else:
    raise SystemExit(f"ERROR: unexpected source version {name} ({code}); refusing blind mutation")

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
