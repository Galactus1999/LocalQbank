#!/usr/bin/env python3
"""Phase 656: correct hidden-bar inset policy and unify Home cards as restrained glass surfaces."""
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
gpath = P / "app/build.gradle.kts"
home = P / "app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
quiz = P / "app/src/main/java/com/localqbank/library/QuizActivity.kt"
flash = P / "app/src/main/java/com/localqbank/library/FlashcardStudyActivity.kt"
layout = P / "app/src/main/java/com/localqbank/library/QuizLayoutBuilder.kt"
for f in (gpath, home, quiz, flash, layout):
    if not f.is_file():
        raise SystemExit(f"[656] required source missing: {f}")

g = gpath.read_text(encoding="utf-8")
if 'versionName = "8.3.653"' not in g or "versionCode = 739" not in g:
    raise SystemExit("[656] expected v8.3.653 / versionCode 739 baseline")

# Root cause: QuizActivity hides the status bar, but its adaptive layout installation
# used keepStatusBarVisible=true by default. AdaptiveLayoutManager then showed the bar
# again and applied a status-bar-sized top inset above the already compact quiz header.
qs = quiz.read_text(encoding="utf-8")
old = "AdaptiveLayoutManager.install(this@QuizActivity, built, topExtraDp = 0, bottomExtraDp = 0) { _ ->"
new = "AdaptiveLayoutManager.install(this@QuizActivity, built, topExtraDp = 0, bottomExtraDp = 0, keepStatusBarVisible = false, protectDisplayCutout = true) { _ ->"
if qs.count(old) == 1:
    qs = qs.replace(old, new, 1)
elif "keepStatusBarVisible = false, protectDisplayCutout = true" not in qs:
    raise SystemExit("[656] QuizActivity adaptive inset installation anchor missing")
if "keepStatusBarVisible = false, protectDisplayCutout = true" not in qs:
    raise SystemExit("[656] quiz hidden-status-bar policy postcondition failed")
quiz.write_text(qs, encoding="utf-8")

# The flashcard activity already requests immersive mode before content exists. Reapply
# after content attachment and focus restoration so theme/window transitions cannot leave
# a stale status-bar inset or restore the system bar over the compact reviewer header.
fs = flash.read_text(encoding="utf-8")
old = "            setContentView(build()); show(); refreshCardData(); timerHandler.post(timerTick)"
new = """            setContentView(build())
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    SystemUi.immersive(this)
                    window.decorView.requestApplyInsets()
                }
            }
            show(); refreshCardData(); timerHandler.post(timerTick)"""
if fs.count(old) == 1:
    fs = fs.replace(old, new, 1)
elif "window.decorView.requestApplyInsets()" not in fs:
    raise SystemExit("[656] FlashcardStudyActivity content-attach anchor missing")
if "override fun onWindowFocusChanged(hasFocus: Boolean)" not in fs:
    anchor = "    private fun key(s:String)="
    if fs.count(anchor) != 1:
        raise SystemExit("[656] cannot insert flashcard focus-restoration hook")
    hook = """    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing && !isDestroyed) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    SystemUi.immersive(this)
                    window.decorView.requestApplyInsets()
                }
            }
        }
    }

"""
    fs = fs.replace(anchor, hook + anchor, 1)
# Keep the header visually integrated with the page rather than drawing an opaque band.
old_bg = "setPadding(dp(8),dp(2),dp(8),dp(2));background=ThemeManager.backgroundDrawable(this@FlashcardStudyActivity)"
new_bg = "setPadding(dp(8),dp(1),dp(8),dp(1));background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)"
if fs.count(old_bg) == 1:
    fs = fs.replace(old_bg, new_bg, 1)
elif new_bg not in fs:
    raise SystemExit("[656] flashcard header surface anchor missing")
if "SystemUi.immersive(this)" not in fs or "override fun onWindowFocusChanged(hasFocus: Boolean)" not in fs:
    raise SystemExit("[656] flashcard immersive reapplication postcondition failed")
flash.write_text(fs, encoding="utf-8")

# Home cards: remove the special-case flat LIGHT drawable and the aggressive Pastel-only
# neon frame. All themes use the same layered translucent material, with restrained,
# theme-adaptive color refraction and a fine highlight rim.
hs = home.read_text(encoding="utf-8")
start = hs.index("    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {")
end = hs.index("\n    private fun withMotionSurface(", start)
new_card = """    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {
        val accent = if (ThemeManager.get(c) == ThemeManager.PASTEL) {
            val accents = intArrayOf(
                Color.rgb(255, 78, 164), Color.rgb(34, 198, 238),
                Color.rgb(157, 112, 255), Color.rgb(255, 163, 82),
                Color.rgb(54, 211, 164)
            )
            accents[Math.floorMod(index, accents.size)]
        } else ThemeManager.accent(c)
        val secondary = if (ThemeManager.get(c) == ThemeManager.PASTEL) {
            ThemeManager.accentSecondary(c)
        } else ThemeManager.accent(c)
        val dark = ThemeManager.isDark(c)
        val radius = d(22,c).toFloat()
        val glass = RovexVisualSurfaceStyle.glass(c,22f,true)
        val tintAlpha = if (dark) 20 else 15
        val colorRefraction = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(tintAlpha + 10,Color.red(accent),Color.green(accent),Color.blue(accent)),
                Color.argb(tintAlpha,Color.red(secondary),Color.green(secondary),Color.blue(secondary)),
                Color.TRANSPARENT
            )
        ).apply { cornerRadius = radius }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(if (dark) 48 else 62,255,255,255),
                Color.argb(if (dark) 12 else 18,255,255,255),
                Color.TRANSPARENT
            )
        ).apply { cornerRadius = radius }
        val rim = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = radius
            setStroke(d(1,c).coerceAtLeast(1),
                Color.argb(if (dark) 92 else 76,Color.red(accent),Color.green(accent),Color.blue(accent)))
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(glass,colorRefraction,sheen,rim))
    }
"""
hs = hs[:start] + new_card + hs[end:]
if "RovexVisualSurfaceStyle.glass(c,22f,true)" not in hs or "return android.graphics.drawable.LayerDrawable(arrayOf(glass,colorRefraction,sheen,rim))" not in hs:
    raise SystemExit("[656] all-theme glass card postcondition failed")
if "RovexClinicalSurfaceDrawable(22f * d" in hs:
    raise SystemExit("[656] opaque LIGHT-only Home card drawable remains")
home.write_text(hs, encoding="utf-8")

# Add stable semantic tags so runtime instrumentation can identify the real compact header
# and bookmark control without relying on child indexes or text labels.
ls = layout.read_text(encoding="utf-8")
if 'tag = "quiz:header"' not in ls or 'contentDescription = "Bookmark question"' not in ls:
    raise SystemExit("[656] quiz header/bookmark anchors missing")
if 'tag = "quiz:bookmark-action"' not in ls:
    ls = ls.replace('contentDescription = "Bookmark question"', 'tag = "quiz:bookmark-action"\n            contentDescription = "Bookmark question"', 1)
layout.write_text(ls, encoding="utf-8")

g = g.replace('versionName = "8.3.653"', 'versionName = "8.3.654"', 1).replace("versionCode = 739", "versionCode = 740", 1)
gpath.write_text(g, encoding="utf-8")
print("[656] quiz hidden-status-bar / adaptive-inset conflict fixed without sacrificing cutout safety")
print("[656] flashcard immersive policy reapplied after content attachment and focus restoration")
print("[656] all Home themes now share layered glass, subtle refraction, sheen and fine adaptive rim")
print("[656] compact quiz header/bookmark controls tagged for runtime visual verification")
print("[656] applied v8.3.654 / versionCode 740")
