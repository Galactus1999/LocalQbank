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
old = 'override fun onDraw(c:android.graphics.Canvas){super.onDraw(c);val cx=width/2f;val cy=height/2f;val r=minOf(width,height)*.34f;p.color=ThemeManager.panel(this@MainActivity);c.drawCircle'
new = 'override fun onDraw(c:android.graphics.Canvas){super.onDraw(c);val cx=width/2f;val cy=height/2f;val r=minOf(width,height)*.34f;p.color=ThemeManager.panel(context);c.drawCircle'
if old not in s:
    raise SystemExit("ROVEX_CI_PATCH_MISSING MainActivity PerformanceRing")
s = s.replace(old,new,1)
s = s.replace('ThemeManager.accent2(this@MainActivity)', 'ThemeManager.accent2(context)')
s = s.replace('ThemeManager.accent(this@MainActivity)', 'ThemeManager.accent(context)')
s = s.replace('ThemeManager.text(this@MainActivity)', 'ThemeManager.text(context)')
s = s.replace('ThemeManager.muted(this@MainActivity)', 'ThemeManager.muted(context)')
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
patch("SearchActivity.kt", [
    ("background=ThemeManager.transparentSectionDrawable(this)}", "background=ThemeManager.transparentSectionDrawable(this@SearchActivity)}"),
    ("setTextColor(ThemeManager.text(this));gravity=Gravity.CENTER", "setTextColor(ThemeManager.text(this@SearchActivity));gravity=Gravity.CENTER"),
    ("setTextColor(ThemeManager.text(this))}", "setTextColor(ThemeManager.text(this@SearchActivity))}"),
])
patch("SettingsScreen.kt", [
    ("private fun themeSwatch(key:String):Int=when(key){\n        ThemeManager.presets.firstOrNull{it.key==key}?.c1 ?: Color.rgb(247,249,255)\n    }",
     "private fun themeSwatch(key:String):Int = ThemeManager.presets.firstOrNull{it.key==key}?.c1 ?: Color.rgb(247,249,255)"),
])
print("ROVEX_CI_COMPILE_FIXES=APPLIED")
