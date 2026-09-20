from pathlib import Path
import re

root = Path(".")
app = root / "app"

# v8.3.272 phase-3 batch: original Ren regression + manifest hardening +
# HTML-reference-aligned dashboard navigation/structure + optional QNN packaging cleanup.
ren = app / "src/main/java/com/localqbank/library/RenActivity.kt"
s = ren.read_text(encoding="utf-8")
needle = """        root.addView(row)

        val baseLeft=root.paddingLeft; val baseTop=root.paddingTop"""
repl = """        root.addView(row)

        openMatchButton = TextView(this).apply {
            text = "OPEN MATCHING QUESTIONS"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.bg(this@RenActivity))
            background = UiDrawableUtils.roundedDrawable(this@RenActivity, ThemeManager.accent(this@RenActivity), 12f)
            setPadding(dp(10), 0, dp(10), 0)
            visibility = View.GONE
        }
        root.addView(openMatchButton, LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(5)
        })

        val baseLeft=root.paddingLeft; val baseTop=root.paddingTop"""
if "openMatchButton = TextView(this)" not in s:
    if needle not in s:
        raise SystemExit("RenActivity insertion point missing")
    s = s.replace(needle, repl, 1)
ren.write_text(s, encoding="utf-8")

manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('<activity android:name=".MainActivity" android:exported="true"',
              '<activity android:name=".MainActivity" android:exported="false"')
manifest.write_text(m, encoding="utf-8")

dash = app / "src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt"
d = dash.read_text(encoding="utf-8")
d = d.replace('when(active){"qbank"->qbank(rows);"cards"->cards(cards);"stats"->stats(rows,overall);"mastery"->mastery(rows,overall);else->home(overall,cards)}',
              'when(active){"qbank"->qbank(rows);"flashcards"->cards(cards);"analytics"->stats(rows,overall);"mastery"->mastery(rows,overall);else->home(overall,cards)}')
d = d.replace('switchSection("stats")', 'switchSection("analytics")')
d = d.replace('switchSection("cards")', 'switchSection("flashcards")')
d = d.replace('listOf("home" to "Home","qbank" to "QBank","cards" to "Cards","stats" to "Stats","mastery" to "Mastery")',
              'listOf("home" to "Home","qbank" to "QBank","flashcards" to "Cards","analytics" to "Stats","mastery" to "Mastery")')

start = d.index('private fun cards(c:Triple<Int,Int,Int>){')
end = d.index('\nprivate fun stats(', start)
cards = r'''private fun cards(c:Triple<Int,Int,Int>){
    title("Cards","Flashcards • retention • review")
    val due=c.third
    val total=c.first
    val reviews=c.second
    val retention=if(reviews==0)0 else ((reviews-due).coerceAtLeast(0)*100/reviews)
    val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16),d(16),d(16),d(14));background=panel()}
    hero.addView(TextView(this).apply{text="Flashcard Overview";textSize=12f;letterSpacing=.12f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))})
    hero.addView(TextView(this).apply{text="$total cards • $retention% retention";textSize=20f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity));setPadding(0,d(4),0,d(2))})
    hero.addView(TextView(this).apply{text="$due due today • $reviews reviews recorded";textSize=11f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))})
    val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=if(total==0)0 else ((total-due).coerceAtLeast(0)*100/total);progressTintList=android.content.res.ColorStateList.valueOf(ThemeManager.accent(this@RovexSectionDashboardActivity))}
    hero.addView(bar,LinearLayout.LayoutParams(-1,d(6)).apply{topMargin=d(12)})
    val open=TextView(this).apply{text="▶  Review Flashcards";gravity=Gravity.CENTER;textSize=13f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.bg(this@RovexSectionDashboardActivity));background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.accent(this@RovexSectionDashboardActivity));cornerRadius=d(22).toFloat()};setPadding(0,d(11),0,d(11));setOnClickListener{startActivity(Intent(this@RovexSectionDashboardActivity,FlashcardActivity::class.java))}}
    hero.addView(open,LinearLayout.LayoutParams(-1,d(46)).apply{topMargin=d(12)});content.addView(hero,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
    section("REVIEW")
    card("Due today","$due cards are currently due according to the flashcard scheduler.",ThemeManager.pastelAccentText(this,1),"REVIEW"){startActivity(Intent(this,FlashcardActivity::class.java))}
    card("Study tools","Create, import and review cards using the existing flashcard workflow.",ThemeManager.accent(this),"OPEN TOOLS"){startActivity(Intent(this,FlashcardActivity::class.java))}
}
'''
d = d[:start] + cards + d[end:]

start = d.index('private fun mastery(rows:List<Row>,o:Row){')
end = d.index('\nprivate fun nav()', start)
mastery = r'''private fun mastery(rows:List<Row>,o:Row){
    title("Mastery","Retention • coverage • subject progress")
    val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(d(4),d(4),d(4),d(4));background=ThemeManager.transparentSectionDrawable(this@RovexSectionDashboardActivity);gravity=Gravity.CENTER_VERTICAL}
    val q=TextView(this).apply{text="QBank Mastery";gravity=Gravity.CENTER;textSize=10.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.bg(this@RovexSectionDashboardActivity));background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.accent(this@RovexSectionDashboardActivity));cornerRadius=d(18).toFloat()};setPadding(d(12),d(8),d(12),d(8))}
    val flash=TextView(this).apply{text="Flashcards";gravity=Gravity.CENTER;textSize=10.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(d(12),d(8),d(12),d(8));setOnClickListener{switchSection("flashcards")}}
    tabs.addView(q,LinearLayout.LayoutParams(0,-2,1f));tabs.addView(flash,LinearLayout.LayoutParams(0,-2,1f));content.addView(tabs,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
    card("Overall QBank Mastery",o.mastery.toString()+"% of imported questions have been attempted at least once.",ThemeManager.accent(this),"OPEN QBANK"){switchSection("qbank")}
    section("SUBJECT MASTERY")
    if(rows.isEmpty())card("No imported subjects","Import content to build the mastery map.",ThemeManager.accent(this),"IMPORT"){startActivity(Intent(this,HtmlImportActivity::class.java))}
    subjectRows(rows).forEachIndexed{index,r->progress(r,index)}
}
'''
d = d[:start] + mastery + d[end:]
dash.write_text(d, encoding="utf-8")

gradle = app / "build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
g = re.sub(r'versionCode\s*=\s*365', 'versionCode = 366', g)
g = re.sub(r'versionName\s*=\s*"8\.3\.271"', 'versionName = "8.3.272"', g)
gradle.write_text(g, encoding="utf-8")

# Remove optional Qualcomm/QNN native payloads; deterministic/local fallback remains authoritative.
for name in [
    "libQnnHtpPrepare.so","libQnnHtpV75Skel.so","libQnnSystem.so","libQnnHtp.so",
    "libQnnIr.so","libQnnSaver.so","libQnnHtpV75Stub.so",
    "libLiteRtCompilerPlugin_Qualcomm.so","libLiteRtDispatch_Qualcomm.so"
]:
    (app / "src/main/jniLibs/arm64-v8a" / name).unlink(missing_ok=True)

# Fail closed if any of the intended edits did not land.
checks = {
    "Ren open-match instantiation": "openMatchButton = TextView(this)" in ren.read_text(encoding="utf-8"),
    "Ren open-match listener": "openMatchButton?.setOnClickListener" in ren.read_text(encoding="utf-8"),
    "MainActivity not exported": 'MainActivity" android:exported="false"' in manifest.read_text(encoding="utf-8"),
    "HTML flashcards section id": '"flashcards"' in dash.read_text(encoding="utf-8"),
    "HTML analytics section id": '"analytics"' in dash.read_text(encoding="utf-8"),
    "Subject aggregation": "private fun subjectRows(rows:List<Row>)" in dash.read_text(encoding="utf-8"),
    "Async generation guard": "token==sectionGeneration" in dash.read_text(encoding="utf-8"),
    "Version 8.3.272": 'versionName = "8.3.272"' in gradle.read_text(encoding="utf-8"),
    "VersionCode 366": "versionCode = 366" in gradle.read_text(encoding="utf-8"),
}
for k,v in checks.items():
    if not v:
        raise SystemExit("FAILED PATCH ASSERTION: " + k)
print("v8.3.272 phase-3 batch applied successfully")
