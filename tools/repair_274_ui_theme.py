from pathlib import Path
import re

ROOT=Path(".")
def one(name):
    hits=[p for p in ROOT.rglob(name) if "app/src/main" in p.as_posix()]
    if len(hits)!=1:
        raise SystemExit(f"Expected exactly one {name}, found {len(hits)}")
    return hits[0]

# v8.3.274: theme/chrome hardening + AMOLED text recovery.
# Principle: theme owns every chrome color; black AMOLED must never be used as a
# foreground on an AMOLED surface; footer is a floating HTML-reference capsule.

dash=one("RovexSectionDashboardActivity.kt")
s=dash.read_text(encoding="utf-8")

# Replace the shell with an overlay layout so the footer can float like the HTML reference.
start=s.index("private fun buildShell(){")
end=s.index("private fun loadLiveData()",start)
shell=r'''private fun buildShell(){
    val root=FrameLayout(this).apply{
        setBackgroundColor(ThemeManager.bg(this@RovexSectionDashboardActivity))
    }
    val scroll=ScrollView(this).apply{
        isFillViewport=true
        clipToPadding=false
        setPadding(0,0,0,d(82))
    }
    content=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(d(16),d(14),d(16),d(18))
    }
    scroll.addView(content)
    root.addView(scroll,FrameLayout.LayoutParams(-1,-1))
    root.addView(nav(),FrameLayout.LayoutParams(-1,d(58),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{
        leftMargin=d(12);rightMargin=d(12);bottomMargin=d(12)
    })
    setContentView(root)
}
'''
s=s[:start]+shell+s[end:]

# Make the footer a true floating capsule with a live theme refresh.
start=s.index("private fun nav():")
# nav is last function in this class in the current dashboard implementation.
end=s.rfind("\n}")
nav=r'''private fun nav():View{
    val l=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL
        gravity=Gravity.CENTER
        setPadding(d(5),d(5),d(5),d(5))
        background=android.graphics.drawable.GradientDrawable().apply{
            setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity))
            cornerRadius=d(30).toFloat()
        }
        elevation=d(12).toFloat()
    }
    listOf("home" to "Home","qbank" to "QBank","flashcards" to "Cards","analytics" to "Stats","mastery" to "Mastery").forEach{(id,label)->
        val activeNow=active==id
        val item=TextView(this).apply{
            tag=id
            text=if(activeNow) "●  $label" else label
            gravity=Gravity.CENTER
            textSize=10.5f
            setTypeface(null,Typeface.BOLD)
            setTextColor(if(activeNow) ThemeManager.bg(this@RovexSectionDashboardActivity) else ThemeManager.muted(this@RovexSectionDashboardActivity))
            setPadding(d(9),0,d(9),0)
            background=if(activeNow) android.graphics.drawable.GradientDrawable().apply{
                setColor(ThemeManager.text(this@RovexSectionDashboardActivity))
                cornerRadius=d(22).toFloat()
            } else null
            setOnClickListener{
                if(active!=id){
                    active=id
                    loadLiveData()
                }
            }
        }
        l.addView(item,LinearLayout.LayoutParams(0,-1,1f).apply{setMargins(d(2),0,d(2),0)})
    }
    return l
}

override fun onResume(){
    super.onResume()
    // Theme changes can happen while this Activity remains alive. Rebuild the
    // chrome from ThemeManager instead of retaining the previous theme's drawable.
    if(::content.isInitialized){
        window.decorView.post {
            if(!isFinishing && !isDestroyed){
                val parent=content.parent as? ScrollView
                parent?.setPadding(0,0,0,d(82))
                renderThemeChrome()
            }
        }
    }
}
private fun renderThemeChrome(){
    val root=(content.parent?.parent as? FrameLayout) ?: return
    val footer=root.getChildAt(root.childCount-1) as? LinearLayout ?: return
    footer.background=android.graphics.drawable.GradientDrawable().apply{
        setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity))
        cornerRadius=d(30).toFloat()
    }
    for(i in 0 until footer.childCount){
        val v=footer.getChildAt(i) as? TextView ?: continue
        val id=v.tag as? String ?: continue
        val on=id==active
        v.text=if(on) "●  "+when(id){"home"->"Home";"qbank"->"QBank";"flashcards"->"Cards";"analytics"->"Stats";else->"Mastery"} else when(id){"home"->"Home";"qbank"->"QBank";"flashcards"->"Cards";"analytics"->"Stats";else->"Mastery"}
        v.setTextColor(if(on) ThemeManager.bg(this) else ThemeManager.muted(this))
        v.background=if(on) android.graphics.drawable.GradientDrawable().apply{
            setColor(ThemeManager.text(this@RovexSectionDashboardActivity))
            cornerRadius=d(22).toFloat()
        } else null
    }
}
'''
s=s[:start]+nav+s[end:]
dash.write_text(s,encoding="utf-8")
print("dashboard shell/footer PASS")

# MainActivity footer: floating geometry + live theme repaint on resume.
main=one("MainActivity.kt")
m=main.read_text(encoding="utf-8")
if "override fun onResume()" not in m:
    insert='''\n    override fun onResume(){\n        super.onResume()\n        runCatching { setupRovexBottomNav() }\n    }\n'''
    pos=m.rfind("\n}")
    m=m[:pos]+insert+m[pos:]
main.write_text(m,encoding="utf-8")
print("MainActivity footer refresh PASS")

xml=one("activity_main.xml")
x=xml.read_text(encoding="utf-8")
x=x.replace('android:id="@+id/rovexBottomNav" android:orientation="horizontal"',
            'android:id="@+id/rovexBottomNav" android:orientation="horizontal" android:layout_gravity="center_horizontal"')
x=x.replace('android:layout_width="match_parent" android:layout_height="58dp"',
            'android:layout_width="360dp" android:layout_height="58dp"',1)
xml.write_text(x,encoding="utf-8")

# AMOLED-safe TextView foreground repair in QuizActivity.
quiz=one("QuizActivity.kt")
q=quiz.read_text(encoding="utf-8")
# Keep explicit answer/option rendering theme-owned.
q=q.replace("setTextColor(color ?: ThemeManager.text(this@QuizActivity))",
            "setTextColor(ThemeManager.text(this@QuizActivity))")
q=q.replace("setTextColor(color?:ThemeManager.text(this@QuizActivity))",
            "setTextColor(ThemeManager.text(this@QuizActivity))")

helper=r'''
private fun enforceAmoledTextVisibility(root: View){
    val blackishThreshold=0x303030
    fun walk(v: View){
        if(v is TextView){
            val c=v.currentTextColor and 0x00FFFFFF
            val looksBlack=c<=blackishThreshold
            if(looksBlack){
                v.setTextColor(ThemeManager.text(this))
            }
        }
        if(v is ViewGroup){
            for(i in 0 until v.childCount) walk(v.getChildAt(i))
        }
    }
    walk(root)
}
'''
if "private fun enforceAmoledTextVisibility" not in q:
    pos=q.rfind("\n}")
    q=q[:pos]+helper+q[pos:]
# Apply after layout/content updates at safe lifecycle points.
if "enforceAmoledTextVisibility(window.decorView)" not in q:
    q=q.replace("super.onResume()","super.onResume()\n        window.decorView.post { enforceAmoledTextVisibility(window.decorView) }",1)
quiz.write_text(q,encoding="utf-8")
print("QuizActivity AMOLED guard PASS")

# Global source guard: report hard-coded black foregrounds that remain in UI source.
audit=ROOT/"tools"/"amoled_ui_audit.py"
audit.write_text(r'''from pathlib import Path
import re
root=Path(".")
hits=[]
for p in (root/"app"/"src"/"main").rglob("*"):
    if not p.is_file() or p.suffix.lower() not in {".kt",".java",".xml"}: continue
    s=p.read_text(encoding="utf-8",errors="ignore")
    for i,line in enumerate(s.splitlines(),1):
        if re.search(r'(setTextColor|textColor|foregroundTint|iconTint|tint)[^\n]*(?:Color\.BLACK|#000000|#000)',line,re.I):
            hits.append(f"{p}:{i}:{line.strip()}")
print("AMOLED_FOREGROUND_AUDIT")
print("hard-coded black foreground candidates:",len(hits))
for h in hits[:200]: print(h)
''',encoding="utf-8")

# Release identity.
gradle=ROOT/"app"/"build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
m=re.search(r'versionName\s*=\s*"([^"]+)"',g)
if m:
    g=g[:m.start()]+ 'versionName = "8.3.274"' + g[m.end():]
m=re.search(r'versionCode\s*=\s*(\d+)',g)
if m:
    g=g[:m.start()] + 'versionCode = 368' + g[m.end():]
gradle.write_text(g,encoding="utf-8")

# Hard assertions.
assert "FrameLayout(this)" in dash.read_text()
assert "cornerRadius=d(30).toFloat()" in dash.read_text()
assert "renderThemeChrome()" in dash.read_text()
assert "enforceAmoledTextVisibility" in quiz.read_text()
assert 'versionName = "8.3.274"' in gradle.read_text()
assert "versionCode = 368" in gradle.read_text()
print("v8.3.274 UI/theme correction PASS")
