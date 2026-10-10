#!/usr/bin/env python3
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"

def file(name):
    path = K / name
    if not path.is_file():
        raise SystemExit("[640] missing expected source: " + str(path))
    return path

def replace_exact(path, old, new, label, expected=1):
    source = path.read_text()
    count = source.count(old)
    if count != expected:
        raise SystemExit("[640] anchor count mismatch for " + label + ": " + str(count) + " expected " + str(expected))
    path.write_text(source.replace(old, new))

gradle = P / "app/build.gradle.kts"
g = gradle.read_text()
if 'versionName = "8.3.639"' not in g or "versionCode = 725" not in g:
    raise SystemExit("[640] wrong baseline; expected v8.3.639 / versionCode 725")
gradle.write_text(g.replace('versionName = "8.3.639"', 'versionName = "8.3.640"', 1).replace("versionCode = 725", "versionCode = 726", 1))

# Root cause: an opaque white clipping background masked the theme card underneath the motion.
p = file("RovexHomeRevolution.kt")
replace_exact(
    p,
    'background = GradientDrawable().apply { cornerRadius = d(radiusDp.toInt(),a).toFloat(); setColor(Color.WHITE) }',
    'background = GradientDrawable().apply { cornerRadius = d(radiusDp.toInt(),a).toFloat(); setColor(Color.TRANSPARENT) }',
    "transparent motion clip background"
)
replace_exact(p, "motionAlpha(c, false).coerceAtMost(0.12f)", "motionAlpha(c, false)", "preserve theme-specific card motion alpha", expected=2)

# Root cause: the previous recursive scan wrapped descendants before their parent card, then
# descended into explicitly wrapped surfaces. Select the outer eligible surface and never nest wrappers.
start = p.read_text().index("    private fun installMotionSurfaces(root:ViewGroup, a:MainActivity) {")
end = p.read_text().index("    private fun feature(", start)
new_scan = """    private fun installMotionSurfaces(root:ViewGroup, a:MainActivity) {
        if (root.getTag(R.id.rovexMotionSurfaceScan) == true) return
        root.setTag(R.id.rovexMotionSurfaceScan, true)
        val density = a.resources.displayMetrics.density
        fun scan(parent:ViewGroup) {
            val children = (0 until parent.childCount).map { parent.getChildAt(it) }
            for (child in children) {
                if (child.parent !== parent) continue
                val tag = child.tag?.toString()
                if (tag != null) {
                    // These nodes already own their clipping/motion surface. Descending into
                    // them produced nested white boxes and motion layers on inner labels/icons.
                    if (tag == "rovex_motion_wrapped_content" ||
                        tag.startsWith("rovex_motion_surface:") ||
                        tag == "rovex_motion_clip" ||
                        tag.startsWith(MOTION_CARD_PREFIX)) continue
                    if (child is ViewGroup) scan(child)
                    continue
                }
                val bg = child.background
                val surfaceBackground = bg is GradientDrawable ||
                    bg is android.graphics.drawable.LayerDrawable ||
                    bg is RovexClinicalSurfaceDrawable
                val eligibleSize = child.width >= (120f * density).toInt() &&
                    child.height >= (52f * density).toInt()
                if (surfaceBackground && eligibleSize) {
                    val lp = child.layoutParams ?: continue
                    val index = parent.indexOfChild(child)
                    if (index < 0) continue
                    parent.removeViewAt(index)
                    val wrapper = withMotionSurface(
                        child, a, "auto-" + child.javaClass.simpleName + "-" + index,
                        22f, lp.height != ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    parent.addView(wrapper, index, lp)
                    // The original card is now owned by the wrapper; do not wrap its descendants.
                    continue
                }
                if (child is ViewGroup) scan(child)
            }
        }
        scan(root)
    }

"""
src = p.read_text()
src = src[:start] + new_scan + src[end:]
p.write_text(src)

# Replace inherited pastel yellow/pink default accent pair with coordinated cool gradients. Preserve
# genuinely custom saved colours; migrate only absent or known legacy-default values.
p = file("RovexColorFlowTextView.kt")
old = """        fun colorOne(c:Context):Int { val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val old=Color.rgb(255,59,48); val saved=p.getInt(C1,old); return if(!p.contains(C1)||saved==old) ThemeManager.accent(c) else saved }
        fun colorTwo(c:Context):Int { val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val old=Color.rgb(255,212,59); val saved=p.getInt(C2,old); return if(!p.contains(C2)||saved==old) ThemeManager.accentSecondary(c) else saved }"""
new = """        private fun defaultFlowColors(c:Context):Pair<Int,Int> = when(ThemeManager.get(c)) {
            ThemeManager.PASTEL -> Color.rgb(75,105,255) to Color.rgb(43,196,181)
            ThemeManager.MINT -> Color.rgb(0,166,145) to Color.rgb(48,151,230)
            ThemeManager.SUNSET -> Color.rgb(255,111,91) to Color.rgb(95,111,255)
            ThemeManager.LAVENDER -> Color.rgb(116,87,245) to Color.rgb(54,184,218)
            ThemeManager.AMOLED -> Color.rgb(52,189,255) to Color.rgb(90,255,204)
            ThemeManager.PANDORA -> Color.rgb(70,235,255) to Color.rgb(181,104,255)
            ThemeManager.SPACE -> Color.rgb(120,174,255) to Color.rgb(165,139,255)
            else -> Color.rgb(50,107,255) to Color.rgb(22,184,166)
        }
        fun colorOne(c:Context):Int {
            val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            val old=Color.rgb(255,59,48); val saved=p.getInt(C1,old); val defaults=defaultFlowColors(c)
            return if(!p.contains(C1)||saved==old||saved==ThemeManager.accent(c)) defaults.first else saved
        }
        fun colorTwo(c:Context):Int {
            val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            val old=Color.rgb(255,212,59); val saved=p.getInt(C2,old); val defaults=defaultFlowColors(c)
            return if(!p.contains(C2)||saved==old||saved==ThemeManager.accentSecondary(c)) defaults.second else saved
        }"""
replace_exact(p, old, new, "theme-aware flow palette migration")

# Settings label explicitly describes scope and the user-selected flow palette.
p = file("SettingsScreen.kt")
replace_exact(
    p,
    'text="Animate pastel card surfaces using these colours"',
    'text="Flow these colours across pastel cards (keep text readable)"',
    "clear Colour Flow surface control label"
)

# Root cause: quiz immersive mode hid only the status bar, leaving navigation/system UI unlike
# the full-screen Ben+AI surface. Hide both bars and retain transient swipe access.
p = file("QuizActivity.kt")
replace_exact(p, "                isAppearanceLightStatusBars = !ThemeManager.isDark(this@QuizActivity)\n                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE\n                hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())",
"""                isAppearanceLightStatusBars = !ThemeManager.isDark(this@QuizActivity)
                isAppearanceLightNavigationBars = !ThemeManager.isDark(this@QuizActivity)
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())""",
"hide all quiz system bars")

# Add a real instrumentation regression test over the rendered Home view hierarchy. This verifies
# card coverage, transparent clipping, and the presence of motion on actual views after inflation.
test = P / "app/src/androidTest/java/com/localqbank/library/RovexFlowSurfaceRegressionTest.kt"
test.write_text("""package com.localqbank.library

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.LottieAnimationView
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RovexFlowSurfaceRegressionTest {
    @Test
    fun homeCardFlowUsesTransparentClipsAndCoversMultipleSections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot)
                    ?: error("Home dashboard root missing")
                val surfaces = mutableListOf<View>()
                val clips = mutableListOf<View>()
                val cardMotion = mutableListOf<LottieAnimationView>()
                fun walk(view: View) {
                    val tag = view.tag?.toString().orEmpty()
                    if (tag.startsWith("rovex_motion_surface:")) surfaces.add(view)
                    if (tag == "rovex_motion_clip") clips.add(view)
                    if (tag.startsWith("rovex_home_motion_card:") && view is LottieAnimationView) cardMotion.add(view)
                    if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
                walk(root)
                check(surfaces.size >= 8) { "Expected motion on at least eight Home card surfaces; found " + surfaces.size }
                check(cardMotion.size >= surfaces.size) {
                    "Every wrapped card must own a Lottie motion layer; wrappers=" + surfaces.size + ", motion=" + cardMotion.size
                }
                check(clips.size >= surfaces.size) {
                    "Every wrapped card must have a rounded clipping viewport; wrappers=" + surfaces.size + ", clips=" + clips.size
                }
                for (clip in clips) {
                    check(clip.clipToOutline) { "Motion clip must clip to its rounded outline" }
                    val shape = clip.background as? GradientDrawable
                        ?: error("Motion clip must use a GradientDrawable outline")
                    check(shape.color?.defaultColor == Color.TRANSPARENT) {
                        "Motion clip must not mask the theme surface with an opaque fill"
                    }
                }
                check(cardMotion.all { it.alpha >= 0.10f }) {
                    "Card motion alpha was clamped too low to be visible"
                }
            }
        }
    }
}
""")

# Postconditions fail the CI overlay before compilation if any key repair silently misses.
home = file("RovexHomeRevolution.kt").read_text()
quiz = file("QuizActivity.kt").read_text()
flow = file("RovexColorFlowTextView.kt").read_text()
settings = file("SettingsScreen.kt").read_text()
if 'setColor(Color.WHITE)' in home or 'motionAlpha(c, false).coerceAtMost(0.12f)' in home:
    raise SystemExit("[640] opaque/low-alpha card overlay regression remains")
if home.count('setColor(Color.TRANSPARENT)') < 2:
    raise SystemExit("[640] transparent clipping postcondition failed")
if "hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())" not in quiz:
    raise SystemExit("[640] quiz full system-bar immersive policy missing")
if "defaultFlowColors" not in flow or "Color.rgb(255,212,59)" not in flow:
    raise SystemExit("[640] safe theme palette migration missing")
if "Flow these colours across pastel cards" not in settings:
    raise SystemExit("[640] Colour Flow control label missing")
if not test.is_file():
    raise SystemExit("[640] rendered-view regression test missing")
print("[640] applied v8.3.640 / versionCode 726")
print("[640] fixed opaque white card mask, nested auto-wrapping, legacy yellow/pink default flow palette, and quiz system-bar immersive mode")
print("[640] added instrumented assertions for actual Home surface count, motion layers, rounded clipping, and transparent theme surfaces")
