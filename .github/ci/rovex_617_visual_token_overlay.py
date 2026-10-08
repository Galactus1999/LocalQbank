#!/usr/bin/env python3
"""Apply the v8.3.617 visual-token phase to the audited v8.3.615 source archive."""
from __future__ import annotations
import sys
from pathlib import Path

EXPECTED_VERSION = 'versionName = "8.3.616"'
EXPECTED_CODE = "versionCode = 702"

TOKENS = {
"RovexVisualColors.kt": '''package com.localqbank.library

import android.content.Context
import android.graphics.Color

/** Semantic visual colors. ThemeManager remains the single palette owner. */
object RovexVisualColors {
    fun controlFill(context: Context): Int = ThemeManager.elevated(context)
    fun controlText(context: Context): Int = ThemeManager.text(context)
    fun accentFill(context: Context): Int = ThemeManager.accent(context)
    fun onAccent(context: Context): Int = ThemeManager.onAccent(context)
    fun muted(context: Context): Int = ThemeManager.muted(context)

    fun controlRipple(context: Context): Int {
        val accent = ThemeManager.accent(context)
        return Color.argb(
            if (ThemeManager.isDark(context)) 58 else 42,
            Color.red(accent), Color.green(accent), Color.blue(accent)
        )
    }
}
''',
"RovexVisualShapes.kt": '''package com.localqbank.library

import android.content.Context

/** Semantic shape tokens. Values derive from the authoritative theme profile. */
object RovexVisualShapes {
    fun controlCornerDp(context: Context): Float =
        (ThemeManager.profile(context).cornerDp * 0.5f).coerceIn(10f, 14f)
    fun prominentCornerDp(context: Context): Float =
        (ThemeManager.profile(context).cornerDp * 0.55f).coerceIn(11f, 15f)
}
''',
"RovexVisualSpacing.kt": '''package com.localqbank.library

/** Small semantic spacing vocabulary for the visual system. */
object RovexVisualSpacing {
    const val XXS_DP = 2
    const val XS_DP = 4
    const val SM_DP = 8
    const val MD_DP = 12
    const val LG_DP = 16
    const val XL_DP = 24
}
''',
"RovexVisualTypography.kt": '''package com.localqbank.library

/** Semantic text sizes for compact, action-oriented View UI. */
object RovexVisualTypography {
    const val ACTION_SP = 11.5f
    const val ACTION_EMPHASIS_SP = 10.5f
}
''',
"RovexMotionSpec.kt": '''package com.localqbank.library

/** Shared motion constants; animation ownership remains in RovexMotionSystem. */
object RovexMotionSpec {
    const val PRESS_SCALE = 0.985f
    const val PRESS_DOWN_MS = 70L
    const val PRESS_UP_MS = 120L
}
''',
"RovexVisualButtonStyle.kt": '''package com.localqbank.library

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView

/** Applies the first consolidated Rovex action-button contract without owning theme state. */
object RovexVisualButtonStyle {
    fun apply(view: TextView, context: Context, emphasized: Boolean = false) {
        view.setTextColor(
            if (emphasized) RovexVisualColors.onAccent(context)
            else RovexVisualColors.controlText(context)
        )
        view.background = RippleDrawable(
            ColorStateList.valueOf(RovexVisualColors.controlRipple(context)),
            GradientDrawable().apply {
                setColor(
                    if (emphasized) RovexVisualColors.accentFill(context)
                    else RovexVisualColors.controlFill(context)
                )
                cornerRadius =
                    RovexVisualShapes.controlCornerDp(context) *
                    context.resources.displayMetrics.density
            },
            null
        )
        view.isClickable = true
        view.isFocusable = true
        view.stateListAnimator = null
    }
}
'''
}

def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_617_visual_token_overlay.py <project>")
    project = Path(sys.argv[1]).resolve()
    gradle = project / "app" / "build.gradle.kts"
    ren = project / "app" / "src" / "main" / "java" / "com" / "localqbank" / "library" / "RenActivity.kt"
    if not gradle.is_file() or not ren.is_file():
        raise SystemExit("v8.3.617 overlay target files missing")
    g = gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g:
        raise SystemExit("v8.3.617 overlay requires the v8.3.616 post-overlay baseline; refusing to patch")
    s = ren.read_text(encoding="utf-8")
    if "RovexVisualButtonStyle.apply" in s:
        raise SystemExit("v8.3.617 overlay appears already applied; refusing duplicate patch")
    old_action = """text=label; textSize=11.5f; gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(this@RenActivity))
            background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),11f)
            setOnClickListener{click()}"""
    new_action = """text=label; textSize=RovexVisualTypography.ACTION_SP; gravity = Gravity.CENTER
            RovexVisualButtonStyle.apply(this, this@RenActivity)
            setOnClickListener{click()}"""
    if old_action not in s:
        raise SystemExit("Ren action-button baseline block not found")
    s = s.replace(old_action, new_action, 1)
    old_open = """setTextColor(ThemeManager.bg(this@RenActivity))
            background = UiDrawableUtils.roundedDrawable(this@RenActivity, ThemeManager.accent(this@RenActivity), 12f)"""
    new_open = """RovexVisualButtonStyle.apply(this, this@RenActivity, emphasized = true)"""
    if old_open not in s:
        raise SystemExit("Ren emphasized-button baseline block not found")
    s = s.replace(old_open, new_open, 1)
    s = s.replace("textSize = 10.5f", "textSize = RovexVisualTypography.ACTION_EMPHASIS_SP", 1)
    g = g.replace(EXPECTED_CODE, "versionCode = 703", 1).replace(EXPECTED_VERSION, 'versionName = "8.3.617"', 1)
    gradle.write_text(g, encoding="utf-8")
    ren.write_text(s, encoding="utf-8")
    base = ren.parent
    for name, body in TOKENS.items():
        target = base / name
        if target.exists():
            raise SystemExit(f"refusing to overwrite existing token file: {target}")
        target.write_text(body, encoding="utf-8")
    print("v8.3.617 visual token overlay: APPLIED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
