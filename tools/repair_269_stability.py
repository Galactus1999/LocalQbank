from pathlib import Path
import re,sys
R=Path(sys.argv[1] if len(sys.argv)>1 else ".")

def one(n):
    p=[x for x in R.rglob(n) if "app/src/main" in x.as_posix()]
    if len(p)!=1: raise SystemExit(f"ERROR: expected one {n}, found {len(p)}")
    return p[0]

# Frankenstein / Free-AI display hygiene: remove accidental CSS/HTML source leakage.
san=R/"app/src/main/java/com/localqbank/library/RovexAiDisplaySanitizer.kt"
san.write_text(r'''package com.localqbank.library

object RovexAiDisplaySanitizer {
    private val STYLE=Regex("(?is)<style\\b[^>]*>.*?</style\\s*>")
    private val SCRIPT=Regex("(?is)<script\\b[^>]*>.*?</script\\s*>")
    private val CSS_RUN=Regex("(?is)^\\s*(?:body|html|h1|h2|h3|h4|h5|h6|p|blockquote|code|pre|table|th|td|mark|\\.card|\\.label|\\.answer|img|a)\\s*\\{[^}]{0,6000}\\}.*?(?=Found \\d+ relevant local questions\\.|Clinical concepts:|Q\\d+\\s*[•.-])")
    fun clean(raw:String?):String {
        var x=raw.orEmpty().replace("\\u0000"," ")
        x=x.replace(STYLE," ").replace(SCRIPT," ").replace(CSS_RUN," ")
        return x.replace(Regex("[ \\t]{2,}")," ").replace(Regex("\\n{3,}"),"\\n\\n").trim()
    }
}
''')

p=one("BenQuestionAiContextDialog.kt"); s=p.read_text()
s=s.replace("Return concise exam-oriented Markdown with headings, short paragraphs, useful comparison tables, and bold key takeaways.",
            "Return concise exam-oriented Markdown with headings, short paragraphs, useful comparison tables, and bold key takeaways. Never output CSS, <style> blocks, HTML document wrappers, or CSS selector source such as body{...}; return only the actual answer content.")
s=s.replace('val answer = result?.let { "## Free AI\\n\\n${it.text}\\n\\n*${it.provider.label} • ${it.model}*" }',
            'val answer = result?.let { "## Free AI\\n\\n${RovexAiDisplaySanitizer.clean(it.text)}\\n\\n*${it.provider.label} • ${it.model}*" }')
p.write_text(s)

# AMOLED-safe capsule footer: runtime theme owns the colours.
p=one("activity_main.xml"); x=p.read_text()
x=x.replace('android:background="#0F1C2E"','android:background="@android:color/transparent"')
x=x.replace('android:textColor="#FFFFFF"','android:textColor="@android:color/transparent"')
p.write_text(x)

# QBank dashboard: live QBank/test rows, direct touch targets, analytical HTML-style hero.
p=one("RovexSectionDashboardActivity.kt"); s=p.read_text()
s=s.replace('private data class Row(val name:String,val total:Int,val solved:Int,val correct:Int){',
'''private data class Row(val name:String,val total:Int,val solved:Int,val correct:Int,val testId:String="",val position:Int=0,val path:String="",val source:String=""){''')
s=s.replace('''        val rows=refs.groupBy{it.category.ifBlank{"General"}}.map{(name,items)->
            var solved=0;var correct=0
            items.forEach{r->when(p?.record(r.stableKey)?.status){"correct"->{solved++;correct++};"wrong"->solved++}}
            Row(name,items.size,solved,correct)
        }.sortedByDescending{it.total}''',
'''        val rows=refs.groupBy{it.testId}.map{(_,items)->
            val first=items.first()
            var solved=0;var correct=0
            items.forEach{r->when(p?.record(r.stableKey)?.status){"correct"->{solved++;correct++};"wrong"->solved++}}
            Row(first.testTitle.ifBlank{"Untitled QBank"},items.size,solved,correct,first.testId,items.minOfOrNull{it.position}?:0,first.category.ifBlank{"General"},first.sourceName)
        }.sortedWith(compareBy<Row>{it.path.lowercase()}.thenBy{it.name.lowercase()})''')
s=s.replace('val overall=Row("Overall",rows.sumOf{it.total},rows.sumOf{it.solved},rows.sumOf{it.correct})',
            'val overall=Row("Main QBank",rows.sumOf{it.total},rows.sumOf{it.solved},rows.sumOf{it.correct})')
s=s.replace('''val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14),d(12),d(14),d(11));background=android.graphics.drawable.GradientDrawable().apply{setColor(fill);cornerRadius=d(17).toFloat()}}''',
'''val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14),d(12),d(14),d(11));background=android.graphics.drawable.GradientDrawable().apply{setColor(fill);cornerRadius=d(17).toFloat()};isClickable=true;isFocusable=true;setOnClickListener{if(r.testId.isNotBlank())startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{putExtra("testId",r.testId);putExtra("title",r.name);putExtra("position",r.position);putExtra("sectionLabel",r.path);putExtra("practiceMode",true)})}}''')
s=s.replace('''b.addView(TextView(this).apply{text=r.solved.toString()+"/"+r.total+" solved  •  "+r.accuracy+"% accuracy";textSize=10.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(3),0,d(5))})''',
'''b.addView(TextView(this).apply{text=r.path+"  •  "+r.solved.toString()+"/"+r.total+" solved  •  "+r.accuracy+"% accuracy";textSize=10.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(3),0,d(5))})''')

start=s.index("private fun qbank(rows:List<Row>){")
end=s.index("private fun cards(",start)
q='''private fun qbank(rows:List<Row>){
    title("QBank","Main Bank • all subjects combined")
    analyticsHero(rows)
    section("SUB-QBANKS / SECTIONS")
    if(rows.isEmpty())card("No imported QBank yet","Import content first; this view never invents question counts.",ThemeManager.accent(this),"SEARCH"){openSearch()}
    rows.forEachIndexed{index,r->progress(r,index)}
}
private fun analyticsHero(rows:List<Row>){
    val total=rows.sumOf{it.total};val solved=rows.sumOf{it.solved};val correct=rows.sumOf{it.correct}
    val mastery=if(total==0)0 else solved*100/total;val accuracy=if(solved==0)0 else correct*100/solved
    val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16),d(16),d(16),d(14));background=panel()}
    val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    val ring=FrameLayout(this)
    val gauge=ProgressBar(this,null,android.R.attr.progressBarStyleLarge).apply{isIndeterminate=false;max=100;progress=mastery;progressTintList=android.content.res.ColorStateList.valueOf(ThemeManager.accent(this@RovexSectionDashboardActivity))}
    ring.addView(gauge,FrameLayout.LayoutParams(d(112),d(112),Gravity.CENTER))
    ring.addView(TextView(this).apply{text=mastery.toString()+"%\\nMASTERY";gravity=Gravity.CENTER;textSize=17f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))},FrameLayout.LayoutParams(d(112),d(112),Gravity.CENTER))
    top.addView(ring,LinearLayout.LayoutParams(d(126),d(126)))
    val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(10),0,0,0)}
    info.addView(TextView(this).apply{text="Main QBank";textSize=23f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))})
    info.addView(TextView(this).apply{text="All subjects combined\\n"+rows.size+" topics";textSize=13f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(4),0,d(7))})
    info.addView(TextView(this).apply{text=solved.toString()+" / "+total+" Questions";textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity));setPadding(d(10),d(7),d(10),d(7));background=ThemeManager.transparentSectionDrawable(this@RovexSectionDashboardActivity)})
    info.addView(TextView(this).apply{text=accuracy.toString()+"% Accuracy";textSize=13f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.pastelAccentText(this@RovexSectionDashboardActivity,0));setPadding(0,d(7),0,0)})
    top.addView(info,LinearLayout.LayoutParams(0,-2,1f));box.addView(top)
    val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,d(10),0,d(9))}
    fun stat(label:String,value:String){stats.addView(LinearLayout(this@RovexSectionDashboardActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(d(5),d(8),d(5),d(8));background=ThemeManager.transparentSectionDrawable(this@RovexSectionDashboardActivity);addView(TextView(this@RovexSectionDashboardActivity).apply{text=label;textSize=9f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))});addView(TextView(this@RovexSectionDashboardActivity).apply{text=value;textSize=15f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))})},LinearLayout.LayoutParams(0,d(68),1f).apply{setMargins(d(3),0,d(3),0)})}
    stat("SOLVED",solved.toString());stat("ACCURACY",accuracy.toString()+"%");stat("COVERAGE",mastery.toString()+"%");box.addView(stats)
    val resume=TextView(this).apply{text="▶  Resume Main Bank";gravity=Gravity.CENTER;textSize=15f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.bg(this@RovexSectionDashboardActivity));background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.accent(this@RovexSectionDashboardActivity));cornerRadius=d(28).toFloat()};setPadding(0,d(14),0,d(14));setOnClickListener{rows.firstOrNull()?.let{r->if(r.testId.isNotBlank())startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{putExtra("testId",r.testId);putExtra("title",r.name);putExtra("position",r.position);putExtra("practiceMode",true)})}}}
    box.addView(resume,LinearLayout.LayoutParams(-1,d(54)));content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
}
'''
s=s[:start]+q+s[end:]
p.write_text(s)

# Version bump for this patch.
g=R/"app/build.gradle.kts"; gs=g.read_text()
gs=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "8.3.269"',gs,count=1)
gs=re.sub(r'versionCode\s*=\s*\d+','versionCode = 363',gs,count=1)
g.write_text(gs)

# Pre-Gradle source stability gate.
a=R/"tools/assert_phase2_stability.py"
a.write_text('''from pathlib import Path\nimport sys\nr=Path(sys.argv[1] if len(sys.argv)>1 else ".")\nd=next(r.rglob("RovexSectionDashboardActivity.kt")).read_text()\nm=next(r.rglob("MainActivity.kt")).read_text()\nf=next(r.rglob("BenQuestionAiContextDialog.kt")).read_text()\nif d.count("private fun progress(")!=1: raise SystemExit("STABILITY: dashboard progress owner duplicated")\nif "analyticsHero(rows)" not in d: raise SystemExit("STABILITY: QBank analytical hero missing")\nif "setOnClickListener{if(r.testId.isNotBlank())" not in d: raise SystemExit("STABILITY: QBank subsection touch target missing")\nif "RovexAiDisplaySanitizer.clean(it.text)" not in f: raise SystemExit("STABILITY: AI display sanitizer missing")\nif not all(x in m for x in ("navHome","navQBank","navCards","navStats","navMastery")): raise SystemExit("STABILITY: five-section footer incomplete")\nprint("Phase-2 stability assertions PASS")\n''')
import subprocess
subprocess.check_call([sys.executable,str(a),str(R)])
print("8.3.269 stability/UI patch PASS")
