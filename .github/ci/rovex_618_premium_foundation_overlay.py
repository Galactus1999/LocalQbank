#!/usr/bin/env python3
"""Apply Rovex v8.3.618 Phase 1 premium visual foundation to the verified 8.3.617 overlay output."""
from __future__ import annotations
from pathlib import Path
import sys

EXPECTED_VERSION = 'versionName = "8.3.617"'
EXPECTED_CODE = "versionCode = 703"

def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_618_premium_foundation_overlay.py <project>")
    project = Path(sys.argv[1]).resolve()
    app = project / "app"
    pkg = app / "src" / "main" / "java" / "com" / "localqbank" / "library"
    gradle = app / "build.gradle.kts"
    if not gradle.is_file() or not pkg.is_dir():
        raise SystemExit("v8.3.618 overlay: expected Android project files are missing")

    g = gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g:
        raise SystemExit("v8.3.618 overlay requires the v8.3.617 post-overlay baseline; refusing to patch")

    colors = pkg / "RovexVisualColors.kt"
    s = colors.read_text(encoding="utf-8")
    if "fun border(context: Context" in s:
        raise SystemExit("v8.3.618 overlay: visual color roles already present")
    anchor = "    fun muted(context: Context): Int = ThemeManager.muted(context)\n"
    addition = """    fun muted(context: Context): Int = ThemeManager.muted(context)

    fun border(context: Context, strong: Boolean = false): Int {
        val accent = ThemeManager.accent(context)
        val alpha = if (ThemeManager.isDark(context)) {
            if (strong) 150 else 92
        } else {
            if (strong) 105 else 58
        }
        return Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
    }

    fun glassFill(context: Context, prominent: Boolean = false): Int {
        val base = ThemeManager.elevated(context)
        val alpha = if (ThemeManager.isDark(context)) {
            if (prominent) 212 else 184
        } else {
            if (prominent) 228 else 198
        }
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    fun glassHighlight(context: Context): Int =
        Color.argb(if (ThemeManager.isDark(context)) 42 else 92, 255, 255, 255)
"""
    if anchor not in s:
        raise SystemExit("v8.3.618 overlay: visual color anchor missing")
    colors.write_text(s.replace(anchor, addition, 1), encoding="utf-8")

    surface = pkg / "RovexVisualSurfaceStyle.kt"
    if surface.exists():
        raise SystemExit("v8.3.618 overlay: surface style already exists")
    surface.write_text("""package com.localqbank.library

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View

/** Premium surface primitives. Uses bounded translucency/highlights; no device-wide blur dependency. */
object RovexVisualSurfaceStyle {
    fun glass(context: Context, radiusDp: Float, prominent: Boolean = false): LayerDrawable {
        val density = context.resources.displayMetrics.density
        val radius = radiusDp.coerceIn(8f, 28f) * density
        val fill = GradientDrawable().apply {
            setColor(RovexVisualColors.glassFill(context, prominent))
            cornerRadius = radius
            setStroke((1f * density).toInt().coerceAtLeast(1), RovexVisualColors.border(context, prominent))
        }
        val highlight = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = radius
            setStroke((1f * density).toInt().coerceAtLeast(1), RovexVisualColors.glassHighlight(context))
        }
        return LayerDrawable(arrayOf(fill, highlight))
    }

    fun apply(
        view: View,
        context: Context,
        radiusDp: Float,
        prominent: Boolean = false,
        touch: Boolean = true
    ) {
        view.background = glass(context, radiusDp, prominent)
        if (touch && (view.isClickable || view.hasOnClickListeners())) {
            RovexTouchFeedback.bind(view)
        }
    }
}
""", encoding="utf-8")

    button = pkg / "RovexVisualButtonStyle.kt"
    s = button.read_text(encoding="utf-8")
    if "RovexVisualColors.border(context, emphasized)" in s:
        raise SystemExit("v8.3.618 overlay: button border already present")
    import re
    pattern = re.compile(r"        view\.background = RippleDrawable\(.*?\n        \)\n        view\.isClickable = true", re.DOTALL)
    new = """        val density = context.resources.displayMetrics.density
        val radius = RovexVisualShapes.controlCornerDp(context) * density
        val fill = GradientDrawable().apply {
            setColor(if (emphasized) RovexVisualColors.accentFill(context) else RovexVisualColors.controlFill(context))
            cornerRadius = radius
            setStroke((1f * density).toInt().coerceAtLeast(1), RovexVisualColors.border(context, emphasized))
        }
        view.background = RippleDrawable(
            ColorStateList.valueOf(RovexVisualColors.controlRipple(context)),
            fill,
            null
        )
        view.isClickable = true"""
    s, count = pattern.subn(new, s, count=1)
    if count != 1:
        raise SystemExit("v8.3.618 overlay: button RippleDrawable baseline not found")

    if "RovexTouchFeedback.bind(view)" not in s:
        s = s.replace("        view.stateListAnimator = null\n", "        view.stateListAnimator = null\n        RovexTouchFeedback.bind(view)\n", 1)
    button.write_text(s, encoding="utf-8")

    modern = pkg / "RovexModernUi.kt"
    s = modern.read_text(encoding="utf-8")
    old = " private fun surface(view:View,fill:Int,stroke:Int,radius:Int=20)=GradientDrawable().apply{setColor(fill);setStroke(dp(1,view),stroke);cornerRadius=dp(radius,view).toFloat()}\n"
    if old not in s:
        raise SystemExit("v8.3.618 overlay: ModernUi surface baseline missing")
    modern.write_text(s.replace(old, " private fun surface(view:View,fill:Int,stroke:Int,radius:Int=20)=RovexVisualSurfaceStyle.glass(view.context,radius)\n", 1), encoding="utf-8")

    ben = pkg / "BenQuestionAiContextDialog.kt"
    s = ben.read_text(encoding="utf-8")
    old = """            setTextColor(ThemeManager.text(activity))
            background = rounded(activity, ThemeManager.pastelAccentFill(activity, 0), 16f)
            elevation = dp(activity, 2).toFloat()
"""
    if old not in s:
        raise SystemExit("v8.3.618 overlay: Ben action baseline missing")
    ben.write_text(s.replace(old, "            RovexVisualButtonStyle.apply(this, activity)\n            elevation = dp(activity, 2).toFloat()\n", 1), encoding="utf-8")

    motion = pkg / "RovexMotionSystem.kt"
    s = motion.read_text(encoding="utf-8")
    old = """    private const val DOWN_SCALE = 0.985f
    private const val UP_SCALE = 1f
    private const val DOWN_MS = 70L
    private const val UP_MS = 120L
"""
    if old not in s:
        raise SystemExit("v8.3.618 overlay: motion constants baseline missing")
    motion.write_text(s.replace(old, """    private const val DOWN_SCALE = RovexMotionSpec.PRESS_SCALE
    private const val UP_SCALE = 1f
    private const val DOWN_MS = RovexMotionSpec.PRESS_DOWN_MS
    private const val UP_MS = RovexMotionSpec.PRESS_UP_MS
""", 1), encoding="utf-8")

    g = g.replace(EXPECTED_CODE, "versionCode = 704", 1)
    g = g.replace(EXPECTED_VERSION, 'versionName = "8.3.618"', 1)
    gradle.write_text(g, encoding="utf-8")
    print("v8.3.618 premium visual foundation overlay: APPLIED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
