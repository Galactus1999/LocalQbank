#!/usr/bin/env python3
"""Phase 667: restore independently controlled live motion and make appearance controls discoverable."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
theme_path = pkg / "ThemeManager.kt"
settings_path = pkg / "SettingsScreen.kt"
for p in (gradle, theme_path, settings_path, pkg / "RovexLiveMotionSettings.kt", pkg / "AnimationPolicy.kt"):
    if not p.is_file():
        raise SystemExit(f"[667] FATAL: missing required input: {p}")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.661"' not in g or "versionCode = 747" not in g:
    raise SystemExit("[667] FATAL: expected v8.3.661 / code 747")

theme = theme_path.read_text(encoding="utf-8")
old_base = '''        // Static wallpaper foundation: no full-screen Lottie strokes/lines behind UI.
        val base: Drawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(profile(c).backgroundA, profile(c).backgroundB)
        )'''
new_base = '''        // Live motion is opt-in through the existing user setting and Android animation policy.
        // The Lottie layer is deliberately subtle; wallpaper is kept above it as the visual anchor.
        val base: Drawable = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) {
            RovexImportedMotionBackgroundDrawable(c, profile(c))
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(profile(c).backgroundA, profile(c).backgroundB))
        }'''
if theme.count(old_base) != 1:
    raise SystemExit("[667] FATAL: expected static background anchor not found exactly once")
theme = theme.replace(old_base, new_base, 1)
# Replace the older Lottie-based full-screen motion with a low-cost native Canvas renderer.
theme = theme.replace(
    "RovexImportedMotionBackgroundDrawable(c, profile(c))",
    "RovexAmbientMotionDrawable(c, profile(c).backgroundA, profile(c).backgroundB)",
    1
)
if "RovexImportedMotionBackgroundDrawable(c, profile(c))" in theme:
    raise SystemExit("[667] FATAL: legacy Lottie motion is still selected by ThemeManager")
ambient_source = "package com.localqbank.library\n\nimport android.content.Context\nimport android.graphics.Canvas\nimport android.graphics.Color\nimport android.graphics.LinearGradient\nimport android.graphics.Paint\nimport android.graphics.Path\nimport android.graphics.Shader\nimport android.graphics.drawable.Drawable\nimport android.os.SystemClock\nimport kotlin.math.cos\nimport kotlin.math.sin\n\n/**\n * Low-cost ambient motion: slow, non-geometric light ribbons and sparse drifting particles.\n * This is a background accent, not a foreground animation or a blur simulation.\n */\ninternal class RovexAmbientMotionDrawable(\n    context: Context,\n    private val colorA: Int,\n    private val colorB: Int\n) : Drawable() {\n    private val density = context.resources.displayMetrics.density\n    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {\n        style = Paint.Style.STROKE\n        strokeCap = Paint.Cap.ROUND\n        strokeJoin = Paint.Join.ROUND\n    }\n    private val path = Path()\n    private val startedAt = SystemClock.uptimeMillis()\n    private var frameScheduled = false\n    private var drawableAlpha = 255\n    private val frame = Runnable {\n        frameScheduled = false\n        invalidateSelf()\n    }\n\n    override fun draw(canvas: Canvas) {\n        val b = bounds\n        val w = b.width().toFloat()\n        val h = b.height().toFloat()\n        if (w <= 1f || h <= 1f || !isVisible) return\n        val phase = ((SystemClock.uptimeMillis() - startedAt) % 28000L) / 28000f * (Math.PI * 2.0)\n        val accent = mix(colorA, colorB, ((sin(phase) + 1.0) * .5).toFloat())\n        val colors = intArrayOf(\n            Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),\n            Color.argb(62, Color.red(accent), Color.green(accent), Color.blue(accent)),\n            Color.argb(30, Color.red(colorB), Color.green(colorB), Color.blue(colorB)),\n            Color.argb(0, Color.red(colorB), Color.green(colorB), Color.blue(colorB))\n        )\n        canvas.save()\n        canvas.translate(b.left.toFloat(), b.top.toFloat())\n        for (i in 0..2) {\n            val drift = sin(phase + i * 1.7).toFloat()\n            val y = h * (.30f + i * .19f) + drift * h * .055f\n            path.reset()\n            path.moveTo(-w * .12f, y + h * .13f)\n            path.cubicTo(\n                w * (.18f + .07f * cos(phase + i).toFloat()), y - h * .22f,\n                w * (.56f + .08f * sin(phase * .72 + i).toFloat()), y + h * .24f,\n                w * 1.12f, y - h * .10f\n            )\n            paint.shader = LinearGradient(0f, y - h * .18f, w, y + h * .18f, colors, null, Shader.TileMode.CLAMP)\n            paint.strokeWidth = h * if (i == 1) .032f else .021f\n            paint.alpha = ((if (i == 1) 100 else 66) * drawableAlpha / 255)\n            canvas.drawPath(path, paint)\n            paint.strokeWidth = (1.15f * density).coerceAtLeast(1f)\n            paint.alpha = ((if (i == 1) 92 else 54) * drawableAlpha / 255)\n            canvas.drawPath(path, paint)\n        }\n        paint.shader = null\n        paint.style = Paint.Style.FILL\n        for (i in 0 until 14) {\n            val seed = i * 2.3999632\n            val x = (((i * 37) % 101) / 100f) * w + sin(phase * .55 + seed).toFloat() * w * .025f\n            val y = (((i * 61 + 13) % 97) / 96f) * h + cos(phase * .42 + seed).toFloat() * h * .018f\n            val pulse = ((sin(phase * .8 + seed) + 1.0) * .5).toFloat()\n            paint.color = Color.argb(((12 + pulse * 28) * drawableAlpha / 255).toInt(), 235, 244, 255)\n            canvas.drawCircle(x, y, (0.7f + pulse * 1.4f) * density, paint)\n        }\n        paint.style = Paint.Style.STROKE\n        paint.alpha = drawableAlpha\n        canvas.restore()\n        if (!frameScheduled) {\n            frameScheduled = true\n            scheduleSelf(frame, SystemClock.uptimeMillis() + 42L) // approximately 24 fps\n        }\n    }\n\n    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {\n        val changed = super.setVisible(visible, restart)\n        if (!visible && frameScheduled) {\n            unscheduleSelf(frame)\n            frameScheduled = false\n        } else if (visible) invalidateSelf()\n        return changed\n    }\n\n    override fun setAlpha(alpha: Int) { drawableAlpha = alpha.coerceIn(0, 255); invalidateSelf() }\n    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {\n        paint.colorFilter = colorFilter\n        invalidateSelf()\n    }\n    @Deprecated(\"Deprecated by Android\")\n    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT\n\n    private fun mix(a: Int, b: Int, amount: Float): Int {\n        val f = amount.coerceIn(0f, 1f)\n        return Color.rgb(\n            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),\n            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),\n            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()\n        )\n    }\n}"
ambient_path = pkg / "RovexAmbientMotionDrawable.kt"
ambient_path.write_text(ambient_source, encoding="utf-8")
# Keep the wallpaper visually dominant while letting restrained motion remain perceptible.
if "val alpha = 255" not in theme:
    raise SystemExit("[667] FATAL: unexpected wallpaper alpha implementation; inspect manually")
theme = theme.replace("val alpha = 255", "val alpha = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) 218 else 255", 1)
theme_path.write_text(theme, encoding="utf-8")

settings = settings_path.read_text(encoding="utf-8")
normal_anchor = '''        root.addView(button(if (!glassEnabled) "✓  Normal / Solid" else "Normal / Solid") {
            RovexSurfaceSettings.setGlassEnabled(activity, false)
            activity.recreate()
        }, lp())
'''
if settings.count(normal_anchor) != 1:
    raise SystemExit("[667] FATAL: Glass/Normal controls not found in Appearance settings")
motion_control = normal_anchor + '''        section(root, "LIVE MOTION")
        val motionEnabled = RovexLiveMotionSettings.enabled(activity) && AnimationPolicy.enabled(activity)
        root.addView(TextView(activity).apply {
            text = "Animated wallpaper is independent of the surface style. Turn it off to keep the wallpaper still; Android animation accessibility settings are respected."
            textSize = 12f
            setTextColor(ThemeManager.muted(activity))
            setPadding(dp(2), 0, dp(2), dp(8))
        }, lp())
        root.addView(button(if (motionEnabled) "✓  Live Motion On" else "Live Motion Off") {
            RovexLiveMotionSettings.setEnabled(activity, !RovexLiveMotionSettings.enabled(activity))
            activity.recreate()
        }, lp())
'''
settings = settings.replace(normal_anchor, motion_control, 1)
settings_path.write_text(settings, encoding="utf-8")

g = g.replace('versionName = "8.3.661"', 'versionName = "8.3.664"', 1).replace("versionCode = 747", "versionCode = 750", 1)
if 'versionName = "8.3.664"' not in g or "versionCode = 750" not in g:
    raise SystemExit("[667] FATAL: version bump failed")
gradle.write_text(g, encoding="utf-8")

# Structural assertions are intentionally scoped to generated final source, not strings in the overlay.
assert "RovexAmbientMotionDrawable(c, profile(c).backgroundA, profile(c).backgroundB)" in theme
assert "RovexImportedMotionBackgroundDrawable(c, profile(c))" not in theme
assert "internal class RovexAmbientMotionDrawable" in ambient_source
assert "scheduleSelf(frame, SystemClock.uptimeMillis() + 42L)" in ambient_source
assert "saveLayer" not in ambient_source and "BlurMaskFilter" not in ambient_source
assert "RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)" in theme
assert "val alpha = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) 218 else 255" in theme
assert "section(root, \"LIVE MOTION\")" in settings
assert "Live Motion Off" in settings and "Live Motion On" in settings
assert "RovexLiveMotionSettings.setEnabled(activity, !RovexLiveMotionSettings.enabled(activity))" in settings
assert "Liquid Glass" in settings and "Normal / Solid" in settings
# The card the user actually sees must route to the surface selector; labels in addAppearance()
# alone are explicitly insufficient. Preserve the existing main-screen motion card and handler.
assert 'root.addView(category("Surface Style",' in settings
assert '{ showSurfaceStyleDialog() }' in settings
assert 'private fun showSurfaceStyleDialog()' in settings
assert 'RovexSurfaceSettings.isGlassEnabled(activity)' in settings
assert 'RovexSurfaceSettings.setGlassEnabled(activity, true)' in settings
assert 'RovexSurfaceSettings.setGlassEnabled(activity, false)' in settings
assert 'category("Live animated backgrounds"' in settings
assert '{showLiveMotionDialog()}' in settings
assert 'RovexAmbientMotionDrawable(c, profile(c).backgroundA, profile(c).backgroundB)' in theme
assert 'RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)' in theme
assert 'val alpha = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) 218 else 255' in theme
assert 'val alpha = 255' not in theme[theme.index("fun backgroundDrawable(c:Context):Drawable"):theme.index("\n    }", theme.index("fun backgroundDrawable(c:Context):Drawable"))]
assert 'RovexWallpaperManager.isEnabled(c, get(c))' in theme
assert 'AnimationPolicy.enabled(c)' in theme
# Emit bounded structural context so CI proves where controls live, not merely that labels exist.
lines = settings.splitlines()
for idx, line in enumerate(lines):
    if any(token in line for token in ('"Themes"', '"Settings Search"', '"Live animated backgrounds"', 'fun addAppearance', 'fun showSettings')):
        lo, hi = max(0, idx - 3), min(len(lines), idx + 5)
        print(f"[667][UI-TRACE] SettingsScreen.kt:{lo+1}-{hi}")
        for n in range(lo, hi):
            print(f"[667][UI-TRACE] {n+1}: {lines[n][:240]}")
print("[667] PASS: native smooth-ribbon motion at ~24fps; independent Appearance control; Glass/Normal retained; v8.3.664 / 750")
