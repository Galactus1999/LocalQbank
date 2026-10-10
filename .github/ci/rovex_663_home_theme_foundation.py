#!/usr/bin/env python3
"""Phase 663: compact Home geometry + semantic theme foundation; also stabilize a fast-worker test race."""
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
pkg = P / "app/src/main/java/com/localqbank/library"
gradle = P / "app/build.gradle.kts"
home = pkg / "RovexHomeRevolution.kt"
compact_test = P / "app/src/androidTest/java/com/localqbank/library/RovexHomeCompactGeometryRegressionTest.kt"
admission_test = P / "app/src/androidTest/java/com/localqbank/library/QBankDeletionRecoveryAdmissionTest.kt"
for path in (gradle, home, compact_test, admission_test):
    if not path.is_file():
        raise SystemExit("[663] required source missing: " + str(path))

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.658"' not in g or "versionCode = 744" not in g:
    raise SystemExit("[663] wrong baseline; expected v8.3.658 / versionCode 744")
g = g.replace('versionName = "8.3.658"', 'versionName = "8.3.660"', 1)
g = g.replace("versionCode = 744", "versionCode = 746", 1)
gradle.write_text(g, encoding="utf-8")

tokens = pkg / "RovexHomeThemeTokens.kt"
if tokens.exists():
    raise SystemExit("[663] refusing to overwrite existing Home semantic token file")
tokens.write_text("""package com.localqbank.library

import android.content.Context

/**
 * Stateless semantic adapter for Home. ThemeManager remains the sole theme-selection owner;
 * RovexPremiumPalette supplies the semantic role values for every supported theme.
 */
object RovexHomeThemeTokens {
    fun roles(context: Context): RovexColorRoles =
        RovexPremiumPalette.forKey(ThemeManager.get(context), ThemeManager.isDark(context))

    fun surface(context: Context, raised: Boolean = false): Int {
        val palette = roles(context)
        return if (raised) palette.surfaceElevated else palette.surfaceContainer
    }

    fun text(context: Context): Int = roles(context).onSurface
    fun muted(context: Context): Int = roles(context).onSurfaceVariant
    fun accent(context: Context): Int = roles(context).primary
    fun secondary(context: Context): Int = roles(context).secondary
    fun border(context: Context, strong: Boolean = false): Int {
        val palette = roles(context)
        return if (strong) palette.outlineStrong else palette.outline
    }
    fun selectedSurface(context: Context): Int = roles(context).selectedContainer
    fun interactionSurface(context: Context): Int = roles(context).primaryContainer
}
""", encoding="utf-8")

s = home.read_text(encoding="utf-8")
start = "    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {"
end = "\n    private fun withMotionSurface("
if s.count(start) != 1 or s.count(end) != 1:
    raise SystemExit("[663] Home card helper boundaries are not unique; refusing broad replacement")
a = s.index(start)
b = s.index(end, a)
replacement = """    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {
        // One restrained, semantic surface family across themes. No text-flow colours, neon rims,
        // per-card hard-coded pastel palette, or extra view wrappers own Home card appearance.
        val palette = RovexHomeThemeTokens.roles(c)
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(palette.surfaceContainer, palette.surfaceElevated)
        ).apply {
            cornerRadius = d(20,c).toFloat()
            setStroke(d(1,c).coerceAtLeast(1), palette.outline)
        }
    }
"""
s = s[:a] + replacement + s[b:]
for old, new in (
    ("ThemeManager.text(a)", "RovexHomeThemeTokens.text(a)"),
    ("ThemeManager.muted(a)", "RovexHomeThemeTokens.muted(a)"),
    ("ThemeManager.accent(a)", "RovexHomeThemeTokens.accent(a)"),
    ("ThemeManager.accentSecondary(a)", "RovexHomeThemeTokens.secondary(a)"),
):
    s = s.replace(old, new)
s = s.replace(
    "if (cardSurface) ThemeManager.accent(c) else RovexColorFlowTextView.colorOne(c)",
    "if (cardSurface) RovexHomeThemeTokens.accent(c) else RovexColorFlowTextView.colorOne(c)"
)
s = s.replace(
    "if (cardSurface) ThemeManager.accentSecondary(c) else RovexColorFlowTextView.colorTwo(c)",
    "if (cardSurface) RovexHomeThemeTokens.secondary(c) else RovexColorFlowTextView.colorTwo(c)"
)

# The measured progress card still overflowed by 13.4dp after the initial compact pass.
# Reduce its internal vertical stack, not just the regression threshold.
progress_start = s.index('val progress=LinearLayout(a).apply{tag="rovex_home_today_progress"')
progress_end = s.index('content.addView(withMotionSurface(progress,a,"today-progress"', progress_start)
progress_block = s[progress_start:progress_end]
for old, new in (
    ("setPadding(d(12,a),d(10,a),d(12,a),d(10,a))", "setPadding(d(12,a),d(6,a),d(12,a),d(6,a))"),
    ("tv(a,\"Today's Progress\",15.5f,RovexHomeThemeTokens.text(a),true)", "tv(a,\"Today's Progress\",14.5f,RovexHomeThemeTokens.text(a),true).apply{includeFontPadding=false}"),
    ("tv(a,\"Loading calibrated progress…\",24f,RovexHomeThemeTokens.text(a),true)", "tv(a,\"Loading calibrated progress…\",22f,RovexHomeThemeTokens.text(a),true).apply{includeFontPadding=false}"),
    ("LinearLayout.LayoutParams(-1,d(76,a)).apply{topMargin=d(5,a)}", "LinearLayout.LayoutParams(-1,d(72,a)).apply{topMargin=d(4,a)}"),
    ("LinearLayout.LayoutParams(-1,d(34,a)).apply{topMargin=d(6,a)}", "LinearLayout.LayoutParams(-1,d(34,a)).apply{topMargin=d(4,a)}"),
):
    if progress_block.count(old) != 1:
        raise SystemExit("[663] progress-card compact anchor count " + str(progress_block.count(old)) + ": " + old)
    progress_block = progress_block.replace(old, new, 1)
s = s[:progress_start] + progress_block + s[progress_end:]

if "private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable" not in s:
    raise SystemExit("[663] Home card helper missing after edit")
if "RovexPremiumPalette.forKey(ThemeManager.get(context), ThemeManager.isDark(context))" not in tokens.read_text(encoding="utf-8"):
    raise SystemExit("[663] semantic palette adapter postcondition failed")
if "ThemeManager.pastelAccentFill(c,index)" in s[s.index(start):s.index(end, s.index(start))]:
    raise SystemExit("[663] legacy hard-coded Pastel card palette remains in Home card helper")
home.write_text(s, encoding="utf-8")

t = compact_test.read_text(encoding="utf-8")
t = t.replace('assertHeight("rovex_home_today_progress", 225f)', 'assertHeight("rovex_home_today_progress", 190f)')
compact_test.write_text(t, encoding="utf-8")

theme_test = P / "app/src/androidTest/java/com/localqbank/library/RovexHomeThemeFoundationRegressionTest.kt"
if theme_test.exists():
    raise SystemExit("[663] refusing to overwrite existing theme foundation test")
theme_test.write_text(r'''package com.localqbank.library

import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RovexHomeThemeFoundationRegressionTest {
    private fun captureThemeScreenshot(context: android.content.Context, name: String) {
        val directory = context.getExternalFilesDir("rovex-phase1")
            ?: error("External screenshot directory unavailable")
        check(directory.exists() || directory.mkdirs()) { "Cannot create Phase 1 screenshot directory" }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            val file = java.io.File(directory, "phase1_home_" + name + ".png")
            java.io.FileOutputStream(file).use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                    "PNG compression failed for theme=" + name
                }
            }
            check(file.length() > 1024L) { "Captured Home screenshot is empty for theme=" + name }
        } finally {
            bitmap.recycle()
        }
    }
    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test
    fun semanticPaletteRolesRemainReadableAcrossSupportedPaletteFamilies() {
        val keys = listOf(
            RovexPremiumPalette.CLINICAL,
            RovexPremiumPalette.PASTEL,
            RovexPremiumPalette.MINT,
            RovexPremiumPalette.SOLAR,
            RovexPremiumPalette.IRIS,
            RovexPremiumPalette.OLED,
            RovexPremiumPalette.PANDORA,
            RovexPremiumPalette.COSMOS
        )
        for (key in keys) {
            for (dark in listOf(false, true)) {
                val roles = RovexPremiumPalette.forKey(key, dark)
                check(
                    contrast(roles.onSurface, roles.surfaceContainer) >= 4.5 &&
                        contrast(roles.onSurface, roles.surfaceElevated) >= 4.5 &&
                        contrast(roles.onSurfaceVariant, roles.surfaceContainer) >= 4.5 &&
                        contrast(roles.onSurfaceVariant, roles.surfaceElevated) >= 4.5
                ) {
                    "Insufficient primary/muted text contrast for palette=" + key + " dark=" + dark
                }
                check(roles.surfaceContainer != roles.surfaceElevated) {
                    "Surface hierarchy collapsed for palette=" + key + " dark=" + dark
                }
                check(roles.outline != roles.onSurface) {
                    "Outline role must remain distinct from body text for palette=" + key + " dark=" + dark
                }
            }
        }
    }

    /**
     * MainActivity may restore a non-Home bottom-navigation destination from prior
     * instrumentation tests. A fresh ActivityScenario alone does not reset that
     * persisted destination. Explicitly select Home before measuring Home geometry.
     */
    private fun isHomeDashboardAttached(root: ViewGroup): Boolean =
        root.findViewWithTag<View>("ROVEX_HOME_SHELL") != null &&
            root.findViewWithTag<View>("ROVEX_HOME_SCROLL") != null &&
            root.findViewWithTag<View>("rovex_home_motion_header") != null &&
            root.findViewWithTag<View>("rovex_home_search") != null

    private fun selectHomeTab(scenario: ActivityScenario<MainActivity>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun rootAndHomeAttached(): Pair<ViewGroup, Boolean> {
            var result: Pair<ViewGroup, Boolean>? = null
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    ?: error("Activity content root missing while selecting Home")
                result = root to (isHomeDashboardAttached(root))
            }
            return result ?: error("Could not inspect MainActivity while selecting Home")
        }
        if (rootAndHomeAttached().second) return

        // Do not assume the first navigation item is Home. Destination order can change, and
        // performClick() returning true only means a listener ran—not that Home was selected.
        // Probe candidate navigation controls one at a time from the instrumentation thread and
        // verify the actual Home hero after each click.
        val clickCandidates = ArrayList<View>()
        var navDescription = "missing"
        scenario.onActivity { activity ->
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
                ?: error("Activity content root missing while selecting Home")
            val nav = root.findViewWithTag<View>("rovex_bottom_navigation")
            navDescription = if (nav is ViewGroup) {
                "children=" + nav.childCount + "; " + (0 until nav.childCount).joinToString(",") { index ->
                    val child = nav.getChildAt(index)
                    child.javaClass.simpleName + "(tag=" + child.tag + ",clickable=" + child.isClickable +
                        ",description=" + child.contentDescription + ")"
                }
            } else {
                "found=" + (nav != null) + "; not a ViewGroup"
            }
            fun collectClickable(view: View) {
                if (view.visibility != View.VISIBLE || !view.isEnabled) return
                if (view.isClickable) clickCandidates.add(view)
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) collectClickable(view.getChildAt(index))
                }
            }
            if (nav is ViewGroup) collectClickable(nav)
            fun visitHomeLabels(view: View) {
                if (view.visibility == View.VISIBLE && view.isEnabled) {
                    val label = view.contentDescription?.toString().orEmpty()
                    val text = if (view is android.widget.TextView) view.text?.toString().orEmpty() else ""
                    val tag = view.tag?.toString().orEmpty()
                    if (label.equals("home", true) || label.contains("home tab", true) ||
                        text.trim().equals("home", true) || tag.equals("home", true) ||
                        tag.equals("rovex_home", true)) {
                        var clickable: View? = view
                        while (clickable != null && clickable !== root && !clickable.isClickable) {
                            clickable = clickable.parent as? View
                        }
                        if (clickable != null && clickable !== root && !clickCandidates.contains(clickable)) {
                            clickCandidates.add(0, clickable)
                        }
                    }
                }
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) visitHomeLabels(view.getChildAt(index))
                }
            }
            visitHomeLabels(root)
        }

        // Bound the probe: a click gets a short window to perform the real destination change.
        // All waiting happens off the UI thread; no sleeps or waitForIdleSync calls inside
        // ActivityScenario.onActivity callbacks.
        for (candidate in clickCandidates) {
            scenario.onActivity {
                if (candidate.isShown && candidate.isEnabled && candidate.isClickable) {
                    candidate.performClick()
                }
            }
            val deadline = android.os.SystemClock.uptimeMillis() + 1200L
            do {
                instrumentation.waitForIdleSync()
                if (rootAndHomeAttached().second) return
                android.os.SystemClock.sleep(50L)
            } while (android.os.SystemClock.uptimeMillis() < deadline)
        }
        val (root, attached) = rootAndHomeAttached()
        if (attached) return
        val labels = ArrayList<String>()
        fun visitLabels(view: View) {
            val label = view.contentDescription?.toString().orEmpty()
            val text = if (view is android.widget.TextView) view.text?.toString().orEmpty() else ""
            val tag = view.tag?.toString().orEmpty()
            if (label.isNotBlank() || text.isNotBlank() || tag.isNotBlank()) {
                labels.add("tag=" + tag + ",description=" + label + ",text=" + text)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visitLabels(view.getChildAt(index))
            }
        }
        visitLabels(root)
        error("Could not select Home tab after probing " + clickCandidates.size +
            " clickable controls; nav=" + navDescription + "; views=" + labels.take(40).joinToString("; "))
    }

    @Test
    fun compactHomeGeometryAndSemanticSurfaceColorsSurviveThemeChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousTheme = ThemeManager.get(context)
        try {
            val themeCases = listOf(
                "light" to ThemeManager.LIGHT,
                "amoled" to ThemeManager.AMOLED,
                "mint" to ThemeManager.MINT,
                "sunset" to ThemeManager.SUNSET,
                "lavender" to ThemeManager.LAVENDER,
                "pastel" to ThemeManager.PASTEL
            )
            // Launch a fresh MainActivity for each theme. Recreating one Activity after
            // ThemeManager.set() can leave Home's dynamically rebuilt view tree stale, making
            // a geometry assertion test the theme-switch lifecycle rather than Home geometry.
            for ((themeName, theme) in themeCases) {
                ThemeManager.set(context, theme)
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        // This is a fresh MainActivity launched AFTER ThemeManager.set(context, theme).
                        // onCreate must build Home using that selected theme. Calling refreshTheme()
                        // again here races the Activity's own initialization and can detach the
                        // dynamically built Home tree while the regression test is observing it.
                    }
                    # MainActivity is the Home entry point. Its dashboard is attached asynchronously;
                    # probing arbitrary navigation controls before that attachment caused a false CI failure.
                    # The bounded 20s Home-attachment poll immediately below is the authoritative gate.
                    scenario.onActivity { activity -> activity.window.decorView.requestLayout()
                    }
                    // MainActivity builds Home dynamically. On slower GitHub-hosted emulators,
                    // theme preference I/O and first-run initialization can outlast a short 5s gate.
                    // Poll from the instrumentation thread for up to 20s; never sleep on the UI thread.
                    var homeAttached = false
                    var observedRootChildren = "not-observed"
                    for (attempt in 0 until 400) {
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { activity ->
                            val root = activity.findViewById<ViewGroup>(android.R.id.content)
                                ?: error("Activity content root missing for theme=" + theme)
                            homeAttached = isHomeDashboardAttached(root)
                            observedRootChildren = (0 until root.childCount).joinToString(",") { index ->
                                val child = root.getChildAt(index)
                                child.javaClass.simpleName + "(tag=" + child.tag + ",visibility=" +
                                    child.visibility + ",measured=" + child.measuredWidth + "x" +
                                    child.measuredHeight + ")"
                            }
                        }
                        if (homeAttached) break
                        Thread.sleep(50)
                    }
                    check(homeAttached) {
                        "Home dashboard did not attach within 20s after fresh MainActivity launch=" + themeName +
                            " theme=" + theme + "; rootChildren=" + observedRootChildren
                    }
                    scenario.onActivity { activity ->
                        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
                            ?: error("Activity content root missing for theme=" + theme)
                        val density = activity.resources.displayMetrics.density
                        fun assertHeight(tag: String, maximumDp: Float) {
                            val view = contentRoot.findViewWithTag<View>(tag)
                                ?: error("Missing Home geometry target=" + tag + " theme=" + theme)
                            val heightDp = view.measuredHeight / density
                            check(heightDp > 0f && heightDp <= maximumDp) {
                                tag + " measured " + heightDp + "dp; expected 0 < height <= " +
                                    maximumDp + "dp for theme=" + theme
                            }
                        }
                        assertHeight("rovex_home_motion_header", 96f)
                        assertHeight("rovex_home_search", 50f)
                        assertHeight("rovex_home_online", 70f)
                        assertHeight("rovex_home_daily_motivation", 88f)
                        assertHeight("rovex_home_today_progress", 190f)
                        assertHeight("modern_feature_qbank", 132f)
                        assertHeight("modern_feature_flashcards", 132f)
                        val roles = RovexHomeThemeTokens.roles(activity)
                        check(
                            contrast(roles.onSurface, roles.surfaceContainer) >= 4.5 &&
                                contrast(roles.onSurface, roles.surfaceElevated) >= 4.5 &&
                                contrast(roles.onSurfaceVariant, roles.surfaceContainer) >= 4.5 &&
                                contrast(roles.onSurfaceVariant, roles.surfaceElevated) >= 4.5
                        ) {
                            "Home primary/muted text contrast below 4.5:1 for theme=" + theme
                        }
                    }
                    instrumentation.waitForIdleSync()
                    captureThemeScreenshot(context, themeName)
                }
            }        } finally {
            ThemeManager.set(context, previousTheme)
        }
    }
}
''', encoding="utf-8")

test = admission_test.read_text(encoding="utf-8")
# Preserve the purpose of this regression while allowing a valid one-time worker to
# finish before WorkManagerTestInitHelper's observer samples it.
for required_constraint in ("requiresBatteryNotLow()", "requiresDeviceIdle()"):
    if required_constraint not in test:
        raise SystemExit("[663] admission regression must continue checking constraint " + required_constraint)
old = "assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)"
new = """println("[663] WorkManager admission observed state=" + info.state +
            "; batteryNotLow=" + info.constraints.requiresBatteryNotLow() +
            "; deviceIdle=" + info.constraints.requiresDeviceIdle())
        assertTrue(
            "Durable recovery work was not admitted; observed state=" + info.state,
            info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.SUCCEEDED
        )"""
if test.count(old) == 1:
    test = test.replace(old, new, 1)
elif "Durable recovery work was not admitted; observed state=" not in test:
    raise SystemExit("[663] deletion admission assertion anchor mismatch")
admission_test.write_text(test, encoding="utf-8")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.660"' not in g or "versionCode = 746" not in g:
    raise SystemExit("[663] version postcondition failed; expected v8.3.660 / versionCode 746")
if "Durable recovery work was not admitted; observed state=" not in admission_test.read_text(encoding="utf-8"):
    raise SystemExit("[663] WorkManager fast-completion regression fix missing")
if "WorkManager admission observed state=" not in admission_test.read_text(encoding="utf-8"):
    raise SystemExit("[663] WorkManager admission runtime diagnostic missing")
if "RovexHomeThemeTokens.text(a)" not in home.read_text(encoding="utf-8"):
    raise SystemExit("[663] semantic text role not integrated into Home")
print("[663] applied v8.3.660 / versionCode 746")
print("[663] Home card surfaces now use shared semantic surface/elevation/outline roles; text and accents use semantic palette roles")
print("[663] compact measured geometry regression covers Light, AMOLED, Mint, Sunset, Lavender and Pastel compatibility themes")
print("[663] palette contrast tests cover eight palette families in light/dark variants")
print("[663] configured instrumented Home PNG capture for Light, AMOLED, Mint, Sunset, Lavender and Pastel themes")
print("[663] corrected WorkManager test race: an already-SUCCEEDED one-time worker is valid admission, while FAILED/BLOCKED/CANCELLED still fail")
