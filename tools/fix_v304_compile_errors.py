#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__import__("sys").argv[1]).resolve() if len(__import__("sys").argv) > 1 else Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/localqbank/library"

def patch(name, replacements):
    p = JAVA / name
    s = p.read_text()
    original = s
    for old, new in replacements:
        if old not in s:
            raise SystemExit(f"ROVEX_CI_PATCH_MISSING {name}: {old[:120]}")
        s = s.replace(old, new, 1)
    if s != original:
        p.write_text(s)

patch("BackupActivity.kt", [
    ("setTextColor(ThemeManager.text(this));gravity=Gravity.CENTER;background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@BackupActivity))",
     "setTextColor(ThemeManager.text(this@BackupActivity));gravity=Gravity.CENTER;background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@BackupActivity))"),
    ("setTextColor(ThemeManager.text(this))};bar.addView(title",
     "setTextColor(ThemeManager.text(this@BackupActivity))};bar.addView(title"),
])

p = JAVA / "MainActivity.kt"
s = p.read_text()
old_line = 'override fun onDraw(c:android.graphics.Canvas){super.onDraw(c);val cx=width/2f;val cy=height/2f;val r=minOf(width,height)*.34f;p.color=ThemeManager.panel(this@MainActivity);c.drawCircle(cx,cy,r,p);p.color=if(accuracy>=80)ThemeManager.accent2(this@MainActivity)else ThemeManager.accent(this@MainActivity);c.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,accuracy*3.6f,false,p);t.color=ThemeManager.text(this@MainActivity);t.textSize=25f*density;c.drawText("$accuracy%",cx,cy+8f*density,t);t.textSize=9.5f*density;t.color=ThemeManager.muted(this@MainActivity);c.drawText("ACCURACY",cx,cy+25f*density,t)}'
new_line = old_line.replace('this@MainActivity', 'context')
if old_line not in s: raise SystemExit("ROVEX_CI_PATCH_MISSING MainActivity PerformanceRing")
s = s.replace(old_line, new_line, 1)
p.write_text(s)

patch("RovexHomeRevolution.kt", [
    ("import android.content.Context", "import android.content.Context\nimport android.content.Intent"),
])
patch("RovexSearchChaseView.kt", [
    ("import android.graphics.Canvas", "import android.graphics.Canvas\nimport android.graphics.Color"),
])
patch("RovexSectionDashboardActivity.kt", [
    ("setColor(ThemeManager.bg(this))", "setColor(ThemeManager.bg(this@RovexSectionDashboardActivity))"),
])
p = JAVA / "SearchActivity.kt"
s = p.read_text()
s = s.replace("ThemeManager.transparentSectionDrawable(this)", "ThemeManager.transparentSectionDrawable(this@SearchActivity)")
s = s.replace("setTextColor(ThemeManager.text(this));gravity=Gravity.CENTER", "setTextColor(ThemeManager.text(this@SearchActivity));gravity=Gravity.CENTER")
s = s.replace("setTextColor(ThemeManager.text(this))}", "setTextColor(ThemeManager.text(this@SearchActivity))}")
p.write_text(s)
patch("SettingsScreen.kt", [
    ("private fun themeSwatch(key:String):Int=when(key){\n        ThemeManager.presets.firstOrNull{it.key==key}?.c1 ?: Color.rgb(247,249,255)\n    }",
     "private fun themeSwatch(key:String):Int = ThemeManager.presets.firstOrNull{it.key==key}?.c1 ?: Color.rgb(247,249,255)"),
])
print("ROVEX_CI_COMPILE_FIXES=APPLIED")
