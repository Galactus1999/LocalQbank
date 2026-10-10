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
g = g.replace('versionName = "8.3.658"', 'versionName = "8.3.659"', 1)
g = g.replace("versionCode = 744", "versionCode = 745", 1)
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
                check(contrast(roles.onSurface, roles.surface) >= 4.5) {
                    "Insufficient normal-text contrast for palette=" + key + " dark=" + dark
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

    @Test
    fun compactHomeGeometryAndSemanticSurfaceColorsSurviveThemeChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousTheme = ThemeManager.get(context)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                for (theme in listOf(ThemeManager.LIGHT, ThemeManager.SPACE, ThemeManager.PASTEL)) {
                    scenario.onActivity { activity ->
                        ThemeManager.set(activity, theme)
                        RovexHomeRevolution.refreshTheme(activity)
                        activity.window.decorView.requestLayout()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot)
                            ?: error("Home dashboard root missing for theme=" + theme)
                        val density = activity.resources.displayMetrics.density
                        fun assertHeight(tag: String, maximumDp: Float) {
                            val view = root.findViewWithTag<View>(tag)
                                ?: error("Missing Home geometry target=" + tag + " theme=" + theme)
                            val heightDp = view.measuredHeight / density
                            check(heightDp > 0f && heightDp <= maximumDp) {
                                tag + " measured " + heightDp + "dp; expected 0 < height <= " +
                                    maximumDp + "dp for theme=" + theme
                            }
                        }
                        assertHeight("rovex_home_clinical_hero", 96f)
                        assertHeight("rovex_home_search", 50f)
                        assertHeight("rovex_home_online", 70f)
                        assertHeight("rovex_home_daily_motivation", 88f)
                        assertHeight("rovex_home_today_progress", 190f)
                        assertHeight("modern_feature_qbank", 132f)
                        assertHeight("modern_feature_flashcards", 132f)
                        val roles = RovexHomeThemeTokens.roles(activity)
                        check(contrast(roles.onSurface, roles.surface) >= 4.5) {
                            "Home semantic text contrast below 4.5:1 for theme=" + theme
                        }
                    }
                }
            }
        } finally {
            ThemeManager.set(context, previousTheme)
        }
    }
}
''', encoding="utf-8")

test = admission_test.read_text(encoding="utf-8")
old = "assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)"
new = """assertTrue(
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
if 'versionName = "8.3.659"' not in g or "versionCode = 745" not in g:
    raise SystemExit("[663] version postcondition failed")
if "Durable recovery work was not admitted; observed state=" not in admission_test.read_text(encoding="utf-8"):
    raise SystemExit("[663] WorkManager fast-completion regression fix missing")
if "RovexHomeThemeTokens.text(a)" not in home.read_text(encoding="utf-8"):
    raise SystemExit("[663] semantic text role not integrated into Home")
print("[663] applied v8.3.659 / versionCode 745")
print("[663] Home card surfaces now use shared semantic surface/elevation/outline roles; text and accents use semantic palette roles")
print("[663] compact measured geometry regression tightened and extended across light, dark/Space and Pastel compatibility themes")
print("[663] palette contrast tests cover eight palette families in light/dark variants")
print("[663] corrected WorkManager test race: an already-SUCCEEDED one-time worker is valid admission, while FAILED/BLOCKED/CANCELLED still fail")
