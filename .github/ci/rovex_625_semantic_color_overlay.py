#!/usr/bin/env python3
"""Rovex v8.3.625 — semantic color-system phase.

Moves the visual system from scattered hard-coded colors toward a role-based
palette inspired by Material 3: primary/secondary/tertiary, surface tiers,
outline, state containers and on-colors. The existing ThemeManager remains the
single owner of the selected theme; this overlay only upgrades its palette.
"""
from pathlib import Path
import sys

EXPECTED_VERSION = 'versionName = "8.3.624"'
EXPECTED_CODE = "versionCode = 710"

PALETTE = r'''package com.localqbank.library

import android.graphics.Color

/** Semantic color roles for Rovex production surfaces. */
data class RovexColorRoles(
    val background: Int,
    val surface: Int,
    val surfaceContainer: Int,
    val surfaceElevated: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val tertiary: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineStrong: Int,
    val success: Int,
    val onSuccess: Int,
    val successContainer: Int,
    val onSuccessContainer: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val warning: Int,
    val onWarning: Int,
    val warningContainer: Int,
    val onWarningContainer: Int,
    val info: Int,
    val infoContainer: Int,
    val selectedContainer: Int,
    val navSurface: Int,
    val navSelected: Int,
    val navOnSelected: Int
)

object RovexPremiumPalette {
    const val CLINICAL = "clinical"
    const val PASTEL = "pastel"
    const val MINT = "mint"
    const val SOLAR = "solar"
    const val IRIS = "iris"
    const val OLED = "oled"
    const val PANDORA = "pandora"
    const val COSMOS = "cosmos"

    fun forKey(key: String, dark: Boolean): RovexColorRoles {
        return when (key) {
            PASTEL -> pastel(dark)
            MINT -> mint(dark)
            SOLAR -> solar(dark)
            IRIS -> iris(dark)
            OLED -> oled()
            PANDORA -> pandora(dark)
            COSMOS -> cosmos(dark)
            else -> clinical(dark)
        }
    }

    private fun clinical(d: Boolean) = if (!d) roles(
        bg = Color.rgb(246, 249, 253), surface = Color.rgb(255, 255, 255),
        container = Color.rgb(241, 246, 252), elevated = Color.rgb(255, 255, 255),
        primary = Color.rgb(32, 91, 214), onPrimary = Color.WHITE,
        primaryContainer = Color.rgb(220, 232, 255), onPrimaryContainer = Color.rgb(15, 48, 108),
        secondary = Color.rgb(0, 132, 145), tertiary = Color.rgb(117, 72, 184),
        onSurface = Color.rgb(18, 31, 58), onVariant = Color.rgb(77, 94, 125),
        outline = Color.rgb(173, 188, 211), outlineStrong = Color.rgb(105, 128, 165),
        success = Color.rgb(0, 125, 86), onSuccess = Color.WHITE,
        successContainer = Color.rgb(218, 247, 234), onSuccessContainer = Color.rgb(0, 74, 49),
        error = Color.rgb(190, 45, 77), onError = Color.WHITE,
        errorContainer = Color.rgb(255, 226, 233), onErrorContainer = Color.rgb(112, 24, 48),
        warning = Color.rgb(166, 101, 0), onWarning = Color.WHITE,
        warningContainer = Color.rgb(255, 239, 202), onWarningContainer = Color.rgb(91, 55, 0),
        info = Color.rgb(0, 105, 170), infoContainer = Color.rgb(220, 240, 255),
        selected = Color.rgb(225, 235, 255),
        nav = Color.rgb(250, 252, 255), navSelected = Color.rgb(215, 229, 255), navOn = Color.rgb(20, 66, 155)
    ) else roles(
        bg = Color.rgb(6, 12, 24), surface = Color.rgb(12, 21, 38),
        container = Color.rgb(16, 27, 46), elevated = Color.rgb(20, 32, 53),
        primary = Color.rgb(91, 169, 255), onPrimary = Color.rgb(5, 25, 57),
        primaryContainer = Color.rgb(20, 57, 101), onPrimaryContainer = Color.rgb(206, 229, 255),
        secondary = Color.rgb(77, 215, 222), tertiary = Color.rgb(193, 135, 255),
        onSurface = Color.rgb(238, 245, 255), onVariant = Color.rgb(169, 190, 220),
        outline = Color.rgb(69, 91, 122), outlineStrong = Color.rgb(101, 130, 170),
        success = Color.rgb(75, 225, 169), onSuccess = Color.rgb(0, 48, 31),
        successContainer = Color.rgb(8, 67, 51), onSuccessContainer = Color.rgb(157, 255, 218),
        error = Color.rgb(255, 108, 145), onError = Color.rgb(70, 5, 28),
        errorContainer = Color.rgb(82, 18, 39), onErrorContainer = Color.rgb(255, 207, 219),
        warning = Color.rgb(255, 198, 79), onWarning = Color.rgb(65, 39, 0),
        warningContainer = Color.rgb(74, 55, 9), onWarningContainer = Color.rgb(255, 226, 152),
        info = Color.rgb(91, 200, 255), infoContainer = Color.rgb(8, 49, 75),
        selected = Color.rgb(19, 57, 103),
        nav = Color.rgb(8, 15, 29), navSelected = Color.rgb(22, 55, 91), navOn = Color.rgb(204, 230, 255)
    )

    private fun pastel(d: Boolean) = if (!d) roles(
        Color.rgb(244,247,255),Color.rgb(255,255,255),Color.rgb(239,243,255),Color.rgb(255,255,255),
        Color.rgb(78,94,226),Color.WHITE,Color.rgb(226,231,255),Color.rgb(43,54,129),
        Color.rgb(110,91,205),Color.rgb(202,83,154),Color.rgb(28,35,72),Color.rgb(91,99,132),
        Color.rgb(177,183,211),Color.rgb(118,128,171),Color.rgb(25,139,99),Color.WHITE,Color.rgb(220,248,237),Color.rgb(0,75,49),
        Color.rgb(193,55,93),Color.WHITE,Color.rgb(255,226,236),Color.rgb(113,22,51),
        Color.rgb(164,104,0),Color.WHITE,Color.rgb(255,240,205),Color.rgb(88,55,0),
        Color.rgb(65,110,191),Color.rgb(226,238,255),Color.rgb(230,234,255),
        Color.rgb(250,251,255),Color.rgb(220,225,255),Color.rgb(48,57,143)
    ) else clinical(true)

    private fun mint(d: Boolean) = if (!d) roles(
        Color.rgb(239,250,247),Color.rgb(255,255,255),Color.rgb(232,247,243),Color.WHITE,
        Color.rgb(0,157,137),Color.WHITE,Color.rgb(207,244,236),Color.rgb(0,78,68),
        Color.rgb(39,116,150),Color.rgb(96,76,172),Color.rgb(18,49,46),Color.rgb(72,105,99),
        Color.rgb(160,193,186),Color.rgb(91,137,127),Color.rgb(0,130,91),Color.WHITE,Color.rgb(215,248,235),Color.rgb(0,72,48),
        Color.rgb(193,57,84),Color.WHITE,Color.rgb(255,226,232),Color.rgb(112,20,44),
        Color.rgb(157,102,0),Color.WHITE,Color.rgb(255,239,202),Color.rgb(85,53,0),
        Color.rgb(0,113,154),Color.rgb(219,241,255),Color.rgb(211,246,239),
        Color.rgb(248,254,252),Color.rgb(210,244,237),Color.rgb(0,83,73)
    ) else clinical(true)

    private fun solar(d: Boolean) = if (!d) roles(
        Color.rgb(255,247,239),Color.rgb(255,255,255),Color.rgb(255,240,228),Color.WHITE,
        Color.rgb(226,83,32),Color.WHITE,Color.rgb(255,225,210),Color.rgb(118,35,8),
        Color.rgb(190,96,0),Color.rgb(146,60,139),Color.rgb(61,30,21),Color.rgb(117,83,67),
        Color.rgb(207,170,147),Color.rgb(157,113,91),Color.rgb(0,127,91),Color.WHITE,Color.rgb(220,248,236),Color.rgb(0,73,49),
        Color.rgb(192,49,77),Color.WHITE,Color.rgb(255,226,233),Color.rgb(111,21,48),
        Color.rgb(159,92,0),Color.WHITE,Color.rgb(255,239,201),Color.rgb(83,50,0),
        Color.rgb(0,111,161),Color.rgb(222,240,255),Color.rgb(255,226,210),
        Color.rgb(255,251,247),Color.rgb(255,229,211),Color.rgb(112,41,9)
    ) else clinical(true)

    private fun iris(d: Boolean) = if (!d) roles(
        Color.rgb(247,244,255),Color.WHITE,Color.rgb(240,236,255),Color.WHITE,
        Color.rgb(108,74,226),Color.WHITE,Color.rgb(230,223,255),Color.rgb(60,38,126),
        Color.rgb(28,128,151),Color.rgb(192,73,157),Color.rgb(30,25,60),Color.rgb(93,87,124),
        Color.rgb(181,171,214),Color.rgb(120,107,164),Color.rgb(0,128,92),Color.WHITE,Color.rgb(218,247,235),Color.rgb(0,73,48),
        Color.rgb(193,54,91),Color.WHITE,Color.rgb(255,226,235),Color.rgb(112,21,48),
        Color.rgb(160,100,0),Color.WHITE,Color.rgb(255,239,201),Color.rgb(87,53,0),
        Color.rgb(0,105,164),Color.rgb(224,239,255),Color.rgb(232,226,255),
        Color.rgb(251,249,255),Color.rgb(228,221,255),Color.rgb(68,43,151)
    ) else clinical(true)

    private fun oled() = roles(
        Color.BLACK,Color.rgb(3,5,9),Color.rgb(7,10,16),Color.rgb(11,15,23),
        Color.rgb(65,194,255),Color.rgb(0,31,47),Color.rgb(8,48,72),Color.rgb(184,229,255),
        Color.rgb(75,226,191),Color.rgb(184,119,255),Color.rgb(239,247,255),Color.rgb(157,180,211),
        Color.rgb(54,77,107),Color.rgb(93,125,164),Color.rgb(66,232,171),Color.rgb(0,44,29),Color.rgb(5,67,50),Color.rgb(154,255,218),
        Color.rgb(255,90,132),Color.rgb(70,4,25),Color.rgb(75,13,34),Color.rgb(255,202,218),
        Color.rgb(255,196,72),Color.rgb(65,39,0),Color.rgb(71,52,6),Color.rgb(255,226,146),
        Color.rgb(79,205,255),Color.rgb(6,48,72),Color.rgb(10,45,70),
        Color.rgb(2,4,8),Color.rgb(13,45,70),Color.rgb(195,231,255)
    )

    private fun pandora(d: Boolean) = if (!d) roles(
        Color.rgb(247,243,255),Color.rgb(255,255,255),Color.rgb(240,234,255),Color.WHITE,
        Color.rgb(103,73,210),Color.WHITE,Color.rgb(231,223,255),Color.rgb(57,38,118),
        Color.rgb(0,145,158),Color.rgb(198,72,147),Color.rgb(29,25,57),Color.rgb(91,84,124),
        Color.rgb(181,169,215),Color.rgb(119,105,164),Color.rgb(0,128,91),Color.WHITE,Color.rgb(218,247,235),Color.rgb(0,73,48),
        Color.rgb(192,52,88),Color.WHITE,Color.rgb(255,226,235),Color.rgb(110,20,46),
        Color.rgb(160,100,0),Color.WHITE,Color.rgb(255,239,201),Color.rgb(86,53,0),
        Color.rgb(0,106,163),Color.rgb(223,239,255),Color.rgb(232,225,255),
        Color.rgb(251,249,255),Color.rgb(228,221,255),Color.rgb(66,43,148)
    ) else clinical(true)

    private fun cosmos(d: Boolean) = if (!d) iris(true) else roles(
        Color.rgb(5,7,17),Color.rgb(10,12,27),Color.rgb(15,18,37),Color.rgb(20,23,48),
        Color.rgb(143,112,255),Color.rgb(22,12,55),Color.rgb(44,31,95),Color.rgb(224,215,255),
        Color.rgb(66,210,210),Color.rgb(245,91,178),Color.rgb(242,240,255),Color.rgb(178,176,210),
        Color.rgb(76,74,115),Color.rgb(120,117,171),Color.rgb(74,226,171),Color.rgb(0,48,31),Color.rgb(8,68,52),Color.rgb(157,255,219),
        Color.rgb(255,95,140),Color.rgb(73,5,28),Color.rgb(80,17,41),Color.rgb(255,205,218),
        Color.rgb(255,199,73),Color.rgb(65,39,0),Color.rgb(76,56,8),Color.rgb(255,228,153),
        Color.rgb(90,207,255),Color.rgb(8,49,76),Color.rgb(47,32,99),
        Color.rgb(7,8,21),Color.rgb(43,32,95),Color.rgb(232,225,255)
    )

    private fun roles(
        bg:Int,surface:Int,container:Int,elevated:Int,
        primary:Int,onPrimary:Int,primaryContainer:Int,onPrimaryContainer:Int,
        secondary:Int,tertiary:Int,onSurface:Int,onVariant:Int,
        outline:Int,outlineStrong:Int,success:Int,onSuccess:Int,successContainer:Int,onSuccessContainer:Int,
        error:Int,onError:Int,errorContainer:Int,onErrorContainer:Int,
        warning:Int,onWarning:Int,warningContainer:Int,onWarningContainer:Int,
        info:Int,infoContainer:Int,selected:Int,nav:Int,navSelected:Int,navOn:Int
    ) = RovexColorRoles(bg,surface,container,elevated,primary,onPrimary,primaryContainer,onPrimaryContainer,
        secondary,tertiary,onSurface,onVariant,outline,outlineStrong,success,onSuccess,successContainer,onSuccessContainer,
        error,onError,errorContainer,onErrorContainer,warning,onWarning,warningContainer,onWarningContainer,
        info,infoContainer,selected,nav,navSelected,navOn)
}
'''

VISUAL_COLORS = r'''package com.localqbank.library

import android.content.Context
import android.graphics.Color

/** Single semantic adapter between production views and the selected palette. */
object RovexVisualColors {
    private fun p(context: Context) =
        RovexPremiumPalette.forKey(ThemeManager.get(context), ThemeManager.isDark(context))

    fun background(context: Context): Int = p(context).background
    fun surface(context: Context): Int = p(context).surface
    fun container(context: Context): Int = p(context).surfaceContainer
    fun elevated(context: Context): Int = p(context).surfaceElevated
    fun controlFill(context: Context): Int = p(context).surfaceContainer
    fun controlText(context: Context): Int = p(context).onSurface
    fun accentFill(context: Context): Int = p(context).primary
    fun onAccent(context: Context): Int = p(context).onPrimary
    fun muted(context: Context): Int = p(context).onSurfaceVariant
    fun primaryContainer(context: Context): Int = p(context).primaryContainer
    fun onPrimaryContainer(context: Context): Int = p(context).onPrimaryContainer
    fun secondary(context: Context): Int = p(context).secondary
    fun tertiary(context: Context): Int = p(context).tertiary
    fun success(context: Context): Int = p(context).success
    fun successContainer(context: Context): Int = p(context).successContainer
    fun error(context: Context): Int = p(context).error
    fun errorContainer(context: Context): Int = p(context).errorContainer
    fun warning(context: Context): Int = p(context).warning
    fun warningContainer(context: Context): Int = p(context).warningContainer
    fun info(context: Context): Int = p(context).info
    fun selected(context: Context): Int = p(context).selectedContainer
    fun nav(context: Context): Int = p(context).navSurface
    fun navSelected(context: Context): Int = p(context).navSelected
    fun navOnSelected(context: Context): Int = p(context).navOnSelected

    fun border(context: Context, strong: Boolean = false): Int {
        val c = if (strong) p(context).outlineStrong else p(context).outline
        return c
    }

    fun glassFill(context: Context, prominent: Boolean = false): Int {
        val base = if (prominent) p(context).surfaceElevated else p(context).surface
        val alpha = if (ThemeManager.isDark(context)) if (prominent) 232 else 218 else if (prominent) 238 else 224
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    fun glassHighlight(context: Context): Int =
        Color.argb(if (ThemeManager.isDark(context)) 45 else 105, 255, 255, 255)

    fun controlRipple(context: Context): Int {
        val c = p(context).primary
        return Color.argb(if (ThemeManager.isDark(context)) 64 else 42, Color.red(c), Color.green(c), Color.blue(c))
    }
}
'''

PRODUCTION_PATCH = r'''package com.localqbank.library

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Home color treatment: semantic, restrained and theme-specific. */
object RovexClinicalDayProductionLayer {
    private const val TAG = "ROVEX_COLOR_SYSTEM_625"
    private val cardIds = intArrayOf(
        R.id.renCard, R.id.todaySolvedCard, R.id.studyMenuCard,
        R.id.flashcardCard, R.id.performanceLabCard, R.id.homeSearchCard
    )

    fun apply(activity: MainActivity) {
        val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot) ?: return
        if (root.getTag() == TAG) return
        root.setTag(TAG)
        root.background = RovexPremiumHomeBackdropDrawable(activity)
        cardIds.forEachIndexed { index, id ->
            activity.findViewById<View>(id)?.let { card ->
                styleCard(activity, card, index)
                bindPress(card)
            }
        }
        normalizeTextTree(activity, root)
    }

    private fun styleCard(activity: MainActivity, card: View, index: Int) {
        val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), ThemeManager.isDark(activity))
        val fills = intArrayOf(p.surface, p.primaryContainer, p.surfaceContainer, p.selectedContainer)
        val fill = fills[index % fills.size]
        val stroke = if (index == 0) p.primary else if (index == 1) p.tertiary else p.outline
        card.background = GradientDrawable().apply {
            cornerRadius = activity.resources.displayMetrics.density * 24f
            setColor(fill)
            setStroke(activity.resources.displayMetrics.density.toInt().coerceAtLeast(1), stroke)
        }
        card.elevation = activity.resources.displayMetrics.density * if (ThemeManager.isDark(activity)) 2f else 3f
        card.clipToOutline = true
        if (card is ViewGroup) card.setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16))
    }

    private fun normalizeTextTree(activity: MainActivity, parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) normalizeTextTree(activity, child)
            val tv = child as? TextView ?: continue
            if (child !is Button && child !is EditText && isCardDescendant(tv)) tv.background = null
            tv.includeFontPadding = false
            if (tv.maxLines <= 0 || tv.maxLines == Int.MAX_VALUE) tv.maxLines = 2
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.setTextColor(ThemeManager.text(activity))
        }
    }

    private fun isCardDescendant(view: View): Boolean {
        var p = view.parent
        while (p is ViewGroup) {
            if (cardIds.contains(p.id)) return true
            p = p.parent
        }
        return false
    }

    private fun bindPress(view: View) {
        if (!view.isClickable && !view.hasOnClickListeners()) return
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(.985f).scaleY(.985f).setDuration(70L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
            false
        }
    }

    private fun dp(c: android.content.Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
}
'''

def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_625_semantic_color_overlay.py <project>")
    root = Path(sys.argv[1]).resolve()
    app = root / "app"
    pkg = app / "src/main/java/com/localqbank/library"
    gradle = app / "build.gradle.kts"
    if not gradle.is_file():
        raise SystemExit("v8.3.625: missing build.gradle.kts")
    g = gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g:
        raise SystemExit("v8.3.625: requires v8.3.624/710")
    tm = pkg / "ThemeManager.kt"
    vc = pkg / "RovexVisualColors.kt"
    layer = pkg / "RovexClinicalDayProductionLayer.kt"
    if not tm.is_file() or not vc.is_file() or not layer.is_file():
        raise SystemExit("v8.3.625: required visual files missing")
    t = tm.read_text(encoding="utf-8")
    if "RovexPremiumPalette" in t:
        raise SystemExit("v8.3.625: palette already applied")
    # Preserve the established ThemeManager API while routing its core roles through
    # the semantic palette. Existing specialized study-state helpers remain intact.
    additions = """
    fun onAccent(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).onPrimary
    fun surface(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).surface
    fun surfaceContainer(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).surfaceContainer
    fun surfaceElevated(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).surfaceElevated
    fun outline(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).outline
    fun outlineStrong(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).outlineStrong
    fun primaryContainer(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).primaryContainer
    fun onPrimaryContainer(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).onPrimaryContainer
    fun secondary(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).secondary
    fun tertiary(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).tertiary
    fun success(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).success
    fun successContainer(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).successContainer
    fun error(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).error
    fun errorContainer(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).errorContainer
    fun warning(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).warning
    fun warningContainer(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).warningContainer
    fun info(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).info
    fun selectedContainer(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).selectedContainer
    fun navSurface(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).navSurface
    fun navSelected(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).navSelected
    fun navOnSelected(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).navOnSelected
"""
    anchor = "    fun aiText(c:Context)=if(isDark(c))Color.rgb(99,221,255) else Color.rgb(62,55,174)"
    if anchor not in t:
        raise SystemExit("v8.3.625: ThemeManager aiText anchor missing")
    t = t.replace(anchor, additions + "
" + anchor, 1)
    # Upgrade only the core generic roles; keep legacy specialized helpers stable.
    replacements = {
        "    fun bg(c:Context)=when(get(c)){PASTEL->Color.rgb(241,246,255);MINT->Color.rgb(239,250,247);SUNSET->Color.rgb(255,247,239);LAVENDER->Color.rgb(246,243,255);AMOLED->Color.BLACK;else->Color.rgb(247,249,255)}":
        "    fun bg(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).background",
        "    fun panel(c:Context)=when(get(c)){PASTEL->Color.rgb(249,251,255);MINT->Color.rgb(248,255,252);SUNSET->Color.rgb(255,251,246);LAVENDER->Color.rgb(252,250,255);AMOLED->Color.rgb(4,7,12);else->Color.rgb(252,253,255)}":
        "    fun panel(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).surface",
        "    fun elevated(c:Context)=when(get(c)){PASTEL,MINT,SUNSET,LAVENDER->Color.WHITE;AMOLED->Color.rgb(9,13,22);else->Color.WHITE}":
        "    fun elevated(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).surfaceElevated",
        "    fun text(c:Context)=if(isDark(c))Color.rgb(241,247,255) else Color.rgb(18,32,72)":
        "    fun text(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).onSurface",
        "    fun muted(c:Context)=if(isDark(c))Color.rgb(153,177,208) else Color.rgb(77,94,125)":
        "    fun muted(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).onSurfaceVariant",
        "    fun accent(c:Context)=when(get(c)){MINT->Color.rgb(0,190,165);SUNSET->Color.rgb(255,113,50);LAVENDER->Color.rgb(112,72,255);AMOLED->Color.rgb(54,189,255);PASTEL->Color.rgb(74,91,255);else->Color.rgb(45,91,230)}":
        "    fun accent(c:Context)=RovexPremiumPalette.forKey(get(c),isDark(c)).primary"
    }
    for old,new in replacements.items():
        if old not in t:
            raise SystemExit("v8.3.625: ThemeManager core role anchor missing")
        t=t.replace(old,new,1)
    tm.write_text(t,encoding="utf-8")
    # Add the new roadmap theme constants without removing legacy aliases.
    c_line='    const val LIGHT="light"; const val PASTEL="pastel"; const val MINT="mint"; const val SUNSET="sunset"; const val LAVENDER="lavender"; const val AMOLED="amoled"'
    if c_line not in t:
        raise SystemExit("v8.3.625: theme constants anchor missing")
    t=t.replace(c_line,'    const val LIGHT="light"; const val PASTEL="pastel"; const val MINT="mint"; const val SUNSET="sunset"; const val LAVENDER="lavender"; const val AMOLED="amoled"; const val IRIS="iris"; const val PANDORA="pandora"; const val SPACE="space"',1)
    norm='    private fun normalize(raw:String?):String=when(raw){PASTEL,MINT,SUNSET,LAVENDER,AMOLED,LIGHT->raw; "amoled_dark",DARK,OBSIDIAN_NIGHT,MIDNIGHT,COSMOS,AVATAR->AMOLED; SEPIA->SUNSET; else->LIGHT}'
    if norm not in t:
        raise SystemExit("v8.3.625: normalize anchor missing")
    t=t.replace(norm,'    private fun normalize(raw:String?):String=when(raw){PASTEL,MINT,SUNSET,LAVENDER,AMOLED,LIGHT,IRIS,PANDORA,SPACE->raw; "amoled_dark",DARK,OBSIDIAN_NIGHT,MIDNIGHT,COSMOS,AVATAR->AMOLED; SEPIA->SUNSET; else->LIGHT}',1)
    tm.write_text(t,encoding="utf-8")
    (pkg/"RovexPremiumPalette.kt").write_text(PALETTE,encoding="utf-8")
    vc.write_text(VISUAL_COLORS,encoding="utf-8")
    layer.write_text(PRODUCTION_PATCH,encoding="utf-8")
    # Visual Lab's theme list can expose the new roadmap names if the profile supports them.
    # Do not force it here; profile ownership stays with the existing theme engine.
    g=g.replace(EXPECTED_VERSION,'versionName = "8.3.625"',1).replace(EXPECTED_CODE,"versionCode = 711",1)
    gradle.write_text(g,encoding="utf-8")
    print("v8.3.625 semantic color system: APPLIED")
    print("Eight-role palette + theme-specific surfaces + state colors + Home semantic cards installed")
if __name__=="__main__":
    raise SystemExit(main())
