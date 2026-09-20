from pathlib import Path
import re
app=Path(".")/"app"
p=app/"src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt"
d=p.read_text()
a=d.index("private fun subjectRows(rows:List<Row>):List<Row>{")
b=d.index("\nprivate fun analyticsHero(",a)
new=r'''private fun subjectKey(r:Row):String{
    val raw=r.path.trim()
    return raw.split(">", "/", "::").firstOrNull()?.trim().orEmpty().ifBlank{"General"}
}
private fun subjectRows(rows:List<Row>):List<Row> = rows.sortedBy{it.name.lowercase()}\nprivate fun subjectGroups(rows:List<Row>):List<Pair<String,List<Row>>>{
    return rows.groupBy{subjectKey(it)}.toList().sortedBy{it.first.lowercase()}
}
private fun qbank(rows:List<Row>){
    title("QBank","Main Bank • all subjects combined")
    analyticsHero(rows)
    section("SUBJECTS")
    subjectGroups(rows).forEachIndexed{si,pair->
        val subject=pair.first; val items=pair.second
        val total=items.sumOf{it.total}; val solved=items.sumOf{it.solved}; val correct=items.sumOf{it.correct}
        val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(d(10),d(8),d(10),d(4))}
        val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14),d(13),d(14),d(12));background=panel()}
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        val arrow=TextView(this).apply{text="›";textSize=24f;gravity=Gravity.CENTER;setTextColor(ThemeManager.pastelAccentText(this@RovexSectionDashboardActivity,si))}
        head.addView(TextView(this).apply{text=subject;textSize=16f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))},LinearLayout.LayoutParams(0,-2,1f))
        head.addView(TextView(this).apply{text=items.size.toString()+" QBank"+if(items.size==1)"" else "s";textSize=10f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,0,d(8),0)})
        head.addView(TextView(this).apply{text=(if(total==0)0 else solved*100/total).toString()+"%";textSize=12f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.pastelAccentText(this@RovexSectionDashboardActivity,si))})
        head.addView(arrow,LinearLayout.LayoutParams(d(28),d(28)))
        shell.addView(head)
        shell.addView(TextView(this).apply{text=total.toString()+" questions • "+solved+" solved • "+(if(solved==0)0 else correct*100/solved)+"% accuracy";textSize=10.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(4),0,0)})
        shell.addView(wrap)
        items.sortedBy{it.name.lowercase()}.forEachIndexed{ci,r->
            val child=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(13),d(11),d(13),d(10));background=panel();setOnClickListener{if(r.testId.isNotBlank())startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{putExtra("testId",r.testId);putExtra("title",r.name);putExtra("position",r.position);putExtra("sectionLabel",r.path);putExtra("practiceMode",true)})}}
            child.addView(TextView(this).apply{text=r.name;textSize=13f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))})
            child.addView(TextView(this).apply{text=r.total.toString()+" questions • "+r.solved+" solved • "+r.accuracy+"% accuracy";textSize=10f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(3),0,d(5))})
            child.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{progress=r.mastery;progressTintList=android.content.res.ColorStateList.valueOf(ThemeManager.pastelAccentText(this@RovexSectionDashboardActivity,ci))})
            wrap.addView(child,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})
        }
        head.setOnClickListener{val open=wrap.visibility!=View.VISIBLE;wrap.visibility=if(open)View.VISIBLE else View.GONE;arrow.text=if(open)"⌄" else "›"}
        content.addView(shell,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(8)})
    }
}
'''
d=d[:a]+new+d[b:]
d=d.replace('setOnClickListener{switchSection(id)}','setOnClickListener{if(id=="home"){startActivity(Intent(this@RovexSectionDashboardActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish()}else switchSection(id)}')
p.write_text(d)
p=app/"src/main/java/com/localqbank/library/RenActivity.kt"
r=p.read_text()
r=r.replace('private fun cleanAiAnswer(raw:String):String = BenResponsePolicy.normalize(raw).orEmpty().trim()','private fun cleanAiAnswer(raw:String):String = BenResponsePolicy.normalize(raw).orEmpty().trim()\n    private fun safeAnswerSpanned(raw:String):android.text.Spanned = runCatching { rovexMarkdownToSpanned(raw) }.getOrElse { android.text.SpannedString(raw) }')
r=r.replace('rovexMarkdownToSpanned(cleanAiAnswer(insight.body))','safeAnswerSpanned(cleanAiAnswer(insight.body))').replace('rovexMarkdownToSpanned(cleanAiAnswer(result.text))','safeAnswerSpanned(cleanAiAnswer(result.text))').replace('if(insight.actions.contains("Open matching questions")&&ids.isNotEmpty()){','if(ids.isNotEmpty()){')
p.write_text(r)
p=app/"src/main/java/com/localqbank/library/ExperiencePerformanceManager.kt"
r=p.read_text().replace('if (result != null && result.answer.isNotBlank()) {','if (result != null && BenResponsePolicy.normalize(result.answer).orEmpty().isNotBlank()) {').replace('result.answer.trim(),','BenResponsePolicy.normalize(result.answer).orEmpty().trim(),')
p.write_text(r)
p=app/"build.gradle.kts"
r=p.read_text().replace('versionCode = 366','versionCode = 367').replace('versionName = "8.3.272"','versionName = "8.3.273"')
p.write_text(r)
print("v273 core patch pass")

# Replace the response hygiene layer with a fail-closed text sanitizer.
p=app/"src/main/java/com/localqbank/library/BenResponsePolicy.kt"
policy = """package com.localqbank.library

object BenResponsePolicy {
    const val MAX_RESPONSE_CHARS = 16_384
    private val styleBlock = Regex("(?is)<style\\\\b[^>]*>.*?</style\\\\s*>")
    private val scriptBlock = Regex("(?is)<script\\\\b[^>]*>.*?</script\\\\s*>")
    private val eventAttr = Regex("(?is)\\\\s+on[a-z]+\\\\s*=\\\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\\\s>]+)")
    private val dangerousTag = Regex("(?is)</?(?:iframe|object|embed|svg|math|link|meta|base|form|input|textarea|select|button)(?:\\\\s+[^>]*)?>")
    private val htmlTag = Regex("(?is)</?[a-z][a-z0-9:-]*(?:\\\\s+[^>]*)?/? >".replace(" />","/>"))
    fun normalize(raw: String?): String? {
        var x = raw?.replace("\\r\\n", "\\n")?.replace("\\r", "\\n") ?: return null
        x = x.replace(styleBlock, " ")
            .replace(scriptBlock, " ")
            .replace(eventAttr, " ")
            .replace(dangerousTag, " ")
            .replace(htmlTag, " ")
        x = x.replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim()
        return BoundedTextPolicy.normalize(x, MAX_RESPONSE_CHARS)
    }
}
"""
s=p.read_text(encoding="utf-8")
s=re.sub(r'(?s)object BenResponsePolicy\s*\{.*\}\s*$',policy,s)
p.write_text(s,encoding="utf-8")
print("v273 sanitizer added")
