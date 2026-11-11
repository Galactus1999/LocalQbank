#!/usr/bin/env python3
"""Phase 665: static wallpaper base + consistent Glass/Normal surface mode."""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
APP = ROOT / "app"
PKG = APP / "src/main/java/com/localqbank/library"
GRADLE = APP / "build.gradle.kts"
required = [GRADLE, *(PKG / name for name in (
    "ThemeManager.kt", "RovexVisualSurfaceStyle.kt", "SettingsScreen.kt",
    "RovexHomeRevolution.kt", "RovexDailyStudyHub.kt", "RovexBottomNavigation.kt"))]
for path in required:
    if not path.is_file():
        raise SystemExit(f"[665] FATAL: missing expected file: {path}")
g = GRADLE.read_text(encoding="utf-8")
if 'versionName = "8.3.659"' not in g or "versionCode = 745" not in g:
    raise SystemExit("[665] FATAL: expected Phase 664 output v8.3.659 / code 745")

def read(name):
    return (PKG / name).read_text(encoding="utf-8")
def write(name, value):
    (PKG / name).write_text(value, encoding="utf-8")
def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"[665] FATAL: {label} anchor missing or ambiguous")
    return text.replace(old, new, 1)

# Remove animated Lottie from the wallpaper foundation. It was bleeding decorative strokes
# through translucent surfaces and created persistent lines in every theme.
theme = read("ThemeManager.kt")
theme = replace_once(theme,
'''        val base: Drawable = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) {
            RovexImportedMotionBackgroundDrawable(c, profile(c))
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(profile(c).backgroundA, profile(c).backgroundB))
        }''',
'''        // Static wallpaper foundation: no full-screen Lottie strokes/lines behind UI.
        val base: Drawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(profile(c).backgroundA, profile(c).backgroundB)
        )''', "animated wallpaper base")
write("ThemeManager.kt", theme)

(PKG / "RovexSurfaceSettings.kt").write_text("""package com.localqbank.library

import android.content.Context

/** Persistent user choice for the app's surface treatment, independent of theme/motion. */
object RovexSurfaceSettings {
    private const val PREFS = "rovex_surface_appearance"
    private const val KEY_GLASS = "liquid_glass_enabled"
    fun isGlassEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_GLASS, true)
    fun setGlassEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_GLASS, enabled).apply()
    }
}
""", encoding="utf-8")

# Central surface primitive: clear hierarchy, subtle rim, no stacked opaque white wash.
style = read("RovexVisualSurfaceStyle.kt")
start = "    fun glass(context: Context, radiusDp: Float, prominent: Boolean = false): LayerDrawable {"
end = "    fun apply(view:View,context:Context,radiusDp:Float,prominent:Boolean=false,touch:Boolean=true){"
if style.count(start) != 1 or style.count(end) != 1:
    raise SystemExit("[665] FATAL: surface style anchors missing/ambiguous")
i, j = style.index(start), style.index(end)
func = """    fun glass(context: Context, radiusDp: Float, prominent: Boolean = false): Drawable {
        val d = context.resources.displayMetrics.density
        val r = radiusDp.coerceIn(8f, 28f) * d
        val dark = ThemeManager.isDark(context)
        if (!RovexSurfaceSettings.isGlassEnabled(context)) {
            val solid = if (dark) ThemeManager.elevated(context) else Color.rgb(250, 252, 255)
            val stroke = if (dark) Color.argb(72, 210, 226, 245) else Color.argb(105, 54, 91, 160)
            return GradientDrawable().apply {
                cornerRadius = r
                setColor(solid)
                setStroke(d.toInt().coerceAtLeast(1), stroke)
            }
        }
        val palette = if (prominent) RovexVisualColors.elevated(context) else RovexVisualColors.surface(context)
        val alpha = if (dark) { if (prominent) 190 else 172 } else { if (prominent) 174 else 150 }
        val fillColor = Color.argb(alpha, Color.red(palette), Color.green(palette), Color.blue(palette))
        val accent = ThemeManager.accent(context)
        val tint = Color.argb(if (prominent) 18 else 10, Color.red(accent), Color.green(accent), Color.blue(accent))
        val fill = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            fillColor, fillColor,
            Color.argb((alpha * .92f).toInt(), Color.red(palette), Color.green(palette), Color.blue(palette))
        )).apply { cornerRadius = r }
        val tintLayer = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(tint, Color.TRANSPARENT)).apply { cornerRadius = r }
        val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.argb(if (dark) 28 else 34, 255, 255, 255), Color.TRANSPARENT,
            Color.argb(if (dark) 18 else 10, 0, 0, 0)
        )).apply { cornerRadius = r }
        val border = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = r
            setStroke(d.toInt().coerceAtLeast(1), Color.argb(if (dark) 112 else 135, 255, 255, 255))
        }
        return LayerDrawable(arrayOf(fill, tintLayer, sheen, border))
    }
"""
style = style[:i] + func + style[j:]
if "import android.graphics.drawable.Drawable" not in style:
    style = replace_once(style, "import android.graphics.drawable.GradientDrawable",
                         "import android.graphics.drawable.Drawable\nimport android.graphics.drawable.GradientDrawable",
                         "Drawable import")
if "import android.graphics.drawable.Drawable\\nimport android.graphics.drawable.GradientDrawable" in style:
    raise SystemExit("[665] FATAL: Drawable import separator was emitted as a literal escape")
write("RovexVisualSurfaceStyle.kt", style)

# Home cards no longer add a second independent refraction/sheen stack over the shared surface.
home = read("RovexHomeRevolution.kt")
cs = "    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {"
ce = "    private fun withMotionSurface("
if home.count(cs) != 1 or home.count(ce) != 1:
    raise SystemExit("[665] FATAL: Home card anchors missing/ambiguous")
i, j = home.index(cs), home.index(ce)
home = home[:i] + """    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {
        return RovexVisualSurfaceStyle.glass(c,22f,true)
    }

""" + home[j:]
home = replace_once(home, "it.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)",
                    "it.background = ThemeManager.backgroundDrawable(a)", "Home theme refresh background")
home = replace_once(home,
    'val shell=FrameLayout(a).apply{tag=TAG;contentDescription="rovexHomeShell";background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)}',
    'val shell=FrameLayout(a).apply{tag=TAG;contentDescription="rovexHomeShell";background=ThemeManager.backgroundDrawable(a)}',
    "Home shell background")
# Keep bookmark meaning through a subtle tint while respecting the global style toggle.
home = replace_once(home,
    "background=GradientDrawable().apply{setColor(item.third);cornerRadius=d(16,a).toFloat();setStroke(d(1,a),Color.argb(55,100,120,140))}",
    "background=bookmarkSurface(a,item.third,16f)", "bookmark tile background")
bookmark = """    private fun bookmarkSurface(c:Context, tintColor:Int, radiusDp:Float):android.graphics.drawable.Drawable {
        val radius = d(radiusDp.toInt(),c).toFloat()
        if (!RovexSurfaceSettings.isGlassEnabled(c)) {
            return GradientDrawable().apply { setColor(tintColor); cornerRadius=radius; setStroke(d(1,c),Color.argb(55,100,120,140)) }
        }
        val glass = RovexVisualSurfaceStyle.glass(c,radiusDp,true)
        val tint = GradientDrawable().apply {
            setColor(Color.argb(if(ThemeManager.isDark(c))34 else 24,Color.red(tintColor),Color.green(tintColor),Color.blue(tintColor)))
            cornerRadius=radius
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(glass,tint))
    }

"""
home = replace_once(home, ce, bookmark + ce, "bookmark helper insertion")
write("RovexHomeRevolution.kt", home)

# Mission and Daily Progress previously used their own opaque pastel gradient, ignoring the mode.
daily = read("RovexDailyStudyHub.kt")
ds = "    private fun card(c: Context, index: Int = 0): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ThemeManager.pastelAccentFill(c,index), ThemeManager.elevated(c))).apply {"
de = "    private fun text(c: Context, s: String, size: Float, color: Int = ThemeManager.text(c), bold: Boolean = true)"
if daily.count(ds) != 1 or daily.count(de) != 1:
    raise SystemExit("[665] FATAL: Daily Study Hub card anchors missing/ambiguous")
i, j = daily.index(ds), daily.index(de)
daily = daily[:i] + """    private fun card(c: Context, index: Int = 0): android.graphics.drawable.Drawable =
        RovexVisualSurfaceStyle.glass(c, 22f, true)

""" + daily[j:]
write("RovexDailyStudyHub.kt", daily)

# Floating navigation belongs to the functional layer and uses the same selectable treatment.
nav = read("RovexBottomNavigation.kt")
nav = replace_once(nav,
    "            background = UiDrawableUtils.roundedDrawable(activity, ThemeManager.elevated(activity), 14f)",
    "            background = RovexVisualSurfaceStyle.glass(activity, 22f, true)", "bottom navigation background")
nav = replace_once(nav,
    "shell.background = UiDrawableUtils.roundedDrawable(activity, ThemeManager.elevated(activity), 14f)",
    "shell.background = RovexVisualSurfaceStyle.glass(activity, 22f, true)", "bottom navigation refresh")
write("RovexBottomNavigation.kt", nav)

# Visible opt-in choice in Appearance settings. Persist choice and recreate to refresh every screen.
settings = read("SettingsScreen.kt")
anchor = '''        themes.forEach{(key,label)-> root.addView(button(if(ThemeManager.get(activity)==key)"✓  $label" else label){viewModel.setTheme(key);RovexThemeTransition.apply(activity)},lp())}
    }
'''
replacement = '''        themes.forEach{(key,label)-> root.addView(button(if(ThemeManager.get(activity)==key)"✓  $label" else label){viewModel.setTheme(key);RovexThemeTransition.apply(activity)},lp())}
        section(root,"SURFACE STYLE")
        root.addView(TextView(activity).apply {
            text = "Choose how cards and controls are rendered. Liquid Glass-inspired layered tint and edge highlights (no live backdrop blur on this path); Normal uses opaque solid surfaces. Wallpaper and text remain unchanged."
            textSize = 12f
            setTextColor(ThemeManager.muted(activity))
            setPadding(dp(2), 0, dp(2), dp(8))
        }, lp())
        val glassEnabled = RovexSurfaceSettings.isGlassEnabled(activity)
        root.addView(button(if (glassEnabled) "✓  Liquid Glass" else "Liquid Glass") {
            RovexSurfaceSettings.setGlassEnabled(activity, true)
            activity.recreate()
        }, lp())
        root.addView(button(if (!glassEnabled) "✓  Normal / Solid" else "Normal / Solid") {
            RovexSurfaceSettings.setGlassEnabled(activity, false)
            activity.recreate()
        }, lp())
    }
'''
settings = replace_once(settings, anchor, replacement, "Appearance surface mode controls")

# Add an entry to the actual root Settings screen. Its visible Themes card calls
# showThemeDialog(), so addAppearance() is not a reachable navigation destination.
root_card = '''        root.addView(category("Themes","Light, Pastel, Mint, Sunset, Lavender, AMOLED, Pandora and Space","●",Color.rgb(255,180,70)){showThemeDialog()})'''
if settings.count(root_card) != 1:
    raise SystemExit("[665] FATAL: actual root Settings Themes card missing/ambiguous; refusing to guess navigation")
root_card_replacement = root_card + """
        root.addView(category("Surface Style",
            if (RovexSurfaceSettings.isGlassEnabled(activity)) "Liquid Glass is active • tap to choose Normal / Solid"
            else "Normal / Solid is active • tap to choose Liquid Glass",
            "◈", ThemeManager.accent(activity)) { showSurfaceStyleDialog() },
            LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(6),0,0) })"""
settings = replace_once(settings, root_card, root_card_replacement, "reachable root Surface Style card")

# The card opens the same settings sheet used elsewhere; choices are persisted and selected state is explicit.
sheet_anchor = '''    /** Shared Material-style settings sheet used for secondary settings flows. */
    private fun showSettingsSheet(title: String, subtitle: String, fill: (LinearLayout, BottomSheetDialog) -> Unit) {'''
surface_dialog = """    private fun showSurfaceStyleDialog() {
        showSettingsSheet("Surface Style",
            "Choose the card and control finish. Liquid Glass is a restrained layered approximation, not live backdrop sampling/blur. This choice is saved independently of theme and wallpaper.") { body, dialog ->
            val glassEnabled = RovexSurfaceSettings.isGlassEnabled(activity)
            body.addView(TextView(activity).apply {
                text = "The active option is checked. Changes apply immediately and remain selected when you change themes."
                textSize = 13f
                setTextColor(ThemeManager.muted(activity))
                setPadding(0, dp(4), 0, dp(10))
            }, lp())
            body.addView(button(if (glassEnabled) "✓  Liquid Glass" else "Liquid Glass") {
                RovexSurfaceSettings.setGlassEnabled(activity, true)
                dialog.dismiss()
                activity.recreate()
            }, lp())
            body.addView(button(if (!glassEnabled) "✓  Normal / Solid" else "Normal / Solid") {
                RovexSurfaceSettings.setGlassEnabled(activity, false)
                dialog.dismiss()
                activity.recreate()
            }, lp())
        }
    }

"""
if settings.count(sheet_anchor) != 1:
    raise SystemExit("[665] FATAL: shared settings sheet anchor missing/ambiguous")
settings = settings.replace(sheet_anchor, surface_dialog + sheet_anchor, 1)

write("SettingsScreen.kt", settings)

# Fail closed unless the visible root card and its dialog are connected to the persistent setting.
if 'root.addView(category("Surface Style",' not in settings or '{ showSurfaceStyleDialog() }' not in settings:
    raise SystemExit("[665] FATAL: Surface Style is not reachable from the root Settings list")
if 'private fun showSurfaceStyleDialog()' not in settings:
    raise SystemExit("[665] FATAL: Surface Style selector implementation missing")
if 'RovexSurfaceSettings.setGlassEnabled(activity, true)' not in settings or 'RovexSurfaceSettings.setGlassEnabled(activity, false)' not in settings:
    raise SystemExit("[665] FATAL: both persisted surface choices are not wired")
if 'dialog.dismiss()' not in settings or 'activity.recreate()' not in settings:
    raise SystemExit("[665] FATAL: surface choice does not refresh UI")


g = g.replace('versionName = "8.3.659"', 'versionName = "8.3.660"', 1).replace("versionCode = 745", "versionCode = 746", 1)
if 'versionName = "8.3.660"' not in g or "versionCode = 746" not in g:
    raise SystemExit("[665] FATAL: version bump failed")
GRADLE.write_text(g, encoding="utf-8")
checks = [
    ("static wallpaper base", "Static wallpaper foundation" in theme),
    ("global mode setting", "object RovexSurfaceSettings" in (PKG / "RovexSurfaceSettings.kt").read_text(encoding="utf-8")),
    ("shared glass style", "RovexSurfaceSettings.isGlassEnabled(context)" in style),
    ("mission/progress shared", "RovexVisualSurfaceStyle.glass(c, 22f, true)" in daily),
    ("Home wallpaper background", "background=ThemeManager.backgroundDrawable(a)" in home),
    ("bookmark style", "background=bookmarkSurface(a,item.third,16f)" in home),
    ("navigation style", "RovexVisualSurfaceStyle.glass(activity, 22f, true)" in nav),
    ("version 8.3.660", 'versionName = "8.3.660"' in g and "versionCode = 746" in g),
]
failed = [label for label, ok in checks if not ok]
if failed:
    raise SystemExit("[665] FATAL: postconditions failed: " + ", ".join(failed))
print("[665] PASS: static wallpaper base; consistent Glass/Normal surfaces; v8.3.660 / 746")
