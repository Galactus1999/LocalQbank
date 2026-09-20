#!/usr/bin/env python3
from pathlib import Path
import re

R=Path.cwd()
if not (R/"settings.gradle.kts").is_file() or not (R/"app").is_dir():
    raise SystemExit("AMOLED: not an Android project root")

def f(rel):
    p=R/rel
    if not p.is_file(): raise SystemExit(f"AMOLED: missing {rel}")
    return p

# AMOLED-only repair. No QBank, dashboard, Ben, search, import, SRS, or navigation rewrites.
g=f("app/build.gradle.kts")
gs=g.read_text()
if "versionCode = 372" not in gs or 'versionName = "8.3.278"' not in gs:
    raise SystemExit("AMOLED: expected v8.3.276 / versionCode 370 baseline not found")
g.write_text(gs.replace("versionCode = 370","versionCode = 372",1).replace('versionName = "8.3.276"','versionName = "8.3.278"',1))

tm=f("app/src/main/java/com/localqbank/library/ThemeManager.kt")
s=tm.read_text()
s=s.replace('const val AMOLED="amoled"; const val MIDNIGHT=',
            'const val LEGACY_AMOLED="amoled"; const val LEGACY_AMOLED_V2="amoled_v2"; const val AMOLED="amoled_black_v3"; const val MIDNIGHT=',1)
s=s.replace('fun get(c:Context)=c.getSharedPreferences("ui",Context.MODE_PRIVATE).getString("theme",LIGHT)?:LIGHT',
'''fun get(c:Context):String {
        val prefs=c.getSharedPreferences("ui",Context.MODE_PRIVATE)
        val stored=prefs.getString("theme",LIGHT)?:LIGHT
        if(stored==LEGACY_AMOLED || stored==LEGACY_AMOLED_V2){
            prefs.edit().putString("theme",AMOLED).apply()
            return AMOLED
        }
        return stored
    }''',1)
needle='    fun dialogBg(c:Context)=if(isDark(c)) elevated(c) else Color.WHITE\n'
insert='''    /** Dedicated quiz palette. The AMOLED profile is intentionally a new palette ID so old
     * persisted AMOLED state cannot keep carrying stale theme semantics into the quiz renderer. */
    fun quizQuestionText(c:Context)=if(get(c)==AMOLED) Color.rgb(248,250,252) else text(c)
    fun quizOptionText(c:Context)=if(get(c)==AMOLED) Color.rgb(255,253,245) else optionText(c)
    fun quizMutedText(c:Context)=if(get(c)==AMOLED) Color.rgb(190,199,210) else muted(c)
    fun quizLinkText(c:Context)=if(get(c)==AMOLED) Color.rgb(138,216,255) else accent(c)

'''
if "fun quizQuestionText(c:Context)" not in s:
    if needle not in s: raise SystemExit("AMOLED: ThemeManager insertion point missing")
    s=s.replace(needle,insert+needle,1)
tm.write_text(s)

q=f("app/src/main/java/com/localqbank/library/QuizActivity.kt")
qs=q.read_text()
if "import kotlin.math.pow" not in qs:
    qs=qs.replace("import kotlinx.coroutines.withContext\n","import kotlinx.coroutines.withContext\nimport kotlin.math.pow\n",1)
start=qs.find("private fun enforceAmoledTextVisibility(root: View){")
end=qs.find("\noverride fun onResume()",start)
if start<0 or end<0: raise SystemExit("AMOLED: visibility guard not found")
guard='''private fun enforceAmoledTextVisibility(root:View){
    if(ThemeManager.get(this@QuizActivity)!=ThemeManager.AMOLED)return
    fun walk(v:View){
        if(v is TextView && v.tag=="amoled-html-text"){
            val c=v.currentTextColor
            fun l(x:Int)=if(x/255.0<=.04045)x/3294.6 else Math.pow((x/255.0+.055)/1.055,2.4)
            val lum=.2126*l(android.graphics.Color.red(c))+.7152*l(android.graphics.Color.green(c))+.0722*l(android.graphics.Color.blue(c))
            if(android.graphics.Color.alpha(c)<220 || (lum+.05)/.05<4.5)v.setTextColor(ThemeManager.quizQuestionText(this@QuizActivity))
            v.setLinkTextColor(ThemeManager.quizLinkText(this@QuizActivity))
        }; if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i))
    }
    walk(root)
}'''
qs=qs[:start]+guard+qs[end:]
# Tag only question/option/table HTML views.
qs=qs.replace('val text = TextView(this).apply {\n            this.text = optionSpanned(option.text)',
              'val text = TextView(this).apply {\n            tag = "amoled-html-text"\n            this.text = optionSpanned(option.text)',1)
qs=qs.replace('val t = TextView(this).apply {\n            text = toSpanned(html)',
              'val t = TextView(this).apply {\n            tag = "amoled-html-text"\n            text = toSpanned(html)',1)
qs=qs.replace('val tv = TextView(this).apply {\n                    text = toSpanned(inner)',
              'val tv = TextView(this).apply {\n                    tag = "amoled-html-text"\n                    text = toSpanned(inner)',1)
qs=qs.replace('addHtmlText(q.text, 20f * fontScale, true)',
              'addHtmlText(q.text, 20f * fontScale, true, ThemeManager.quizQuestionText(this@QuizActivity))',1)
qs=qs.replace('setTextColor(ThemeManager.text(this@QuizActivity))\n            setLineSpacing(0f, 1.25f)',
              'setTextColor(color ?: ThemeManager.quizQuestionText(this@QuizActivity))\n            setLinkTextColor(ThemeManager.quizLinkText(this@QuizActivity))\n            setLineSpacing(0f, 1.25f)',1)
qs=qs.replace('setTextColor(ThemeManager.text(this@QuizActivity))\n                    setPadding(dp(10), dp(8), dp(10), dp(8))',
              'setTextColor(ThemeManager.quizQuestionText(this@QuizActivity))\n                    setLinkTextColor(ThemeManager.quizLinkText(this@QuizActivity))\n                    setPadding(dp(10), dp(8), dp(10), dp(8))',1)
qs=qs.replace('setLinkTextColor(ThemeManager.accent(this@QuizActivity))\n            gravity = Gravity.CENTER_VERTICAL\n            setLineSpacing(0f, 1.16f)',
              'setLinkTextColor(ThemeManager.quizLinkText(this@QuizActivity))\n            gravity = Gravity.CENTER_VERTICAL\n            setLineSpacing(0f, 1.16f)',1)
# Neutralize TextAppearanceSpan colour if the existing sanitizer has not already done so.
if "TextAppearanceSpan::class.java" not in qs:
    if "import android.text.style.TextAppearanceSpan" not in qs:
        qs=qs.replace("import android.text.Spanned\n","import android.text.Spanned\nimport android.text.style.TextAppearanceSpan\n",1)
    old='''        out.getSpans(0,out.length,android.text.style.ForegroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,android.text.style.BackgroundColorSpan::class.java).forEach{out.removeSpan(it)}
        return out'''
    new='''        out.getSpans(0,out.length,android.text.style.ForegroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,android.text.style.BackgroundColorSpan::class.java).forEach{out.removeSpan(it)}
        out.getSpans(0,out.length,TextAppearanceSpan::class.java).forEach { span ->
            val a=out.getSpanStart(span); val b=out.getSpanEnd(span); val flags=out.getSpanFlags(span)
            if(a>=0 && b>a){ out.removeSpan(span); out.setSpan(TextAppearanceSpan(span.family,span.textStyle,span.textSize,null,null),a,b,flags) }
            else out.removeSpan(span)
        }
        return out'''
    if old not in qs: raise SystemExit("AMOLED: HTML sanitizer block not found")
    qs=qs.replace(old,new,1)
# Replace the HTML sanitizer with the authoritative AMOLED boundary.
start=qs.find("    private fun themeSafeSpanned(html: String): Spanned {")
end=qs.find("    private fun toSpanned(html: String): Spanned {",start)
if start < 0 or end < 0:
    raise SystemExit("AMOLED: themeSafeSpanned boundary missing")
sanitizer='''    private fun themeSafeSpanned(html: String): Spanned {
        // Imported QBank HTML may contain arbitrary web colours. Android gives character-level
        // spans higher precedence than TextView.setTextColor(), so a black/dark imported span
        // can defeat the AMOLED palette. Strip only colour declarations; preserve structure,
        // emphasis, links, images and other rich formatting.
        val sanitized = html
            .replace(Regex("(?is)\\\\b(?:color|bgcolor)\\\\s*=\\\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\\\s>]+)"), "")
            .replace(Regex("(?is)(\\\\bstyle\\\\s*=\\\\s*\\\")([^\\\"]*)(\\\")"), { m ->
                val body = m.groupValues[2]
                    .replace(Regex("(?is)(?:^|;)\\\\s*(?:color|background-color)\\\\s*:[^;\\\"]*;?"), ";")
                "style=\\\"$body\\\""
            })
            .replace(Regex("(?is)(\\\\bstyle\\\\s*=\\\\s*')([^']*)(')"), { m ->
                val body = m.groupValues[2]
                    .replace(Regex("(?is)(?:^|;)\\\\s*(?:color|background-color)\\\\s*:[^;']*;?"), ";")
                "style='$body'"
            })
            .replace(Regex("(?is)</font>"), "")
        val parsed = Html.fromHtml(sanitized, Html.FROM_HTML_MODE_LEGACY)
        if (parsed !is android.text.Spannable) return parsed
        val out = android.text.SpannableString(parsed)
        out.getSpans(0, out.length, android.text.style.ForegroundColorSpan::class.java).forEach { out.removeSpan(it) }
        out.getSpans(0, out.length, android.text.style.BackgroundColorSpan::class.java).forEach { out.removeSpan(it) }
        out.getSpans(0, out.length, TextAppearanceSpan::class.java).forEach { span ->
            val start = out.getSpanStart(span)
            val end = out.getSpanEnd(span)
            val flags = out.getSpanFlags(span)
            if (start >= 0 && end > start) {
                out.removeSpan(span)
                out.setSpan(TextAppearanceSpan(span.family, span.textStyle, span.textSize, null, null), start, end, flags)
            } else out.removeSpan(span)
        }
        return out
    }

q.write_text(qs)

# Hard guards prove this batch cannot remove the QBank feature.
d=f("app/src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt").read_text()
m=f("app/src/main/java/com/localqbank/library/MainActivity.kt").read_text()
if "private fun qbank(" not in d or "Overall QBank Mastery" not in d or "R.id.navQBank" not in m:
    raise SystemExit("AMOLED: QBank-preservation guard failed")
if "4.5" not in qs or 'AMOLED="amoled_black_v3"' not in s:
    raise SystemExit("AMOLED: final guards failed")
print("AMOLED-ONLY REPAIR PASS: v8.3.278 / versionCode 372")
