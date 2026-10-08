#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
APP = ROOT / "app"
PKG = APP / "src/main/java/com/localqbank/library"

clinical = PKG / "RovexClinicalDayHomeVisuals.kt"
living = PKG / "RovexLivingBackgroundDrawable.kt"
theme = PKG / "ThemeManager.kt"
gradle = APP / "build.gradle.kts"

for p in (clinical, living, theme, gradle):
    if not p.is_file():
        raise SystemExit(f"v8.3.616 visual repair: missing expected file: {p}")

g = gradle.read_text(encoding="utf-8")
if 'versionCode = 701' not in g or 'versionName = "8.3.615"' not in g:
    raise SystemExit("v8.3.616 visual repair: refusing to patch a non-8.3.615 baseline")

c = clinical.read_text(encoding="utf-8")
cs = c.index("class RovexClinicalDayHomeBackgroundDrawable(")
ce = c.index("\n\n/** Premium Clinical Day surface", cs)
if "Clinical Day is intentionally a static, opaque foundation." in c[cs:ce]:
    raise SystemExit("v8.3.616 visual repair: Clinical Day background patch already present")
new_class = r'''class RovexClinicalDayHomeBackgroundDrawable(
    context: android.content.Context
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var backgroundShader: LinearGradient? = null

    /**
     * Clinical Day is intentionally a static, opaque foundation.
     * Decorative motion belongs in isolated foreground views/layers.
     */
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        paint.shader = backgroundShader
        if (paint.shader != null) {
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        } else {
            canvas.drawColor(Color.rgb(247, 250, 255))
        }
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        backgroundShader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.rgb(247, 250, 255),
                Color.rgb(236, 245, 255),
                Color.rgb(255, 247, 238)
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean =
        super.setVisible(visible, restart)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}'''
clinical = c[:cs] + new_class + c[ce:]
clinical.write_text(clinical, encoding="utf-8")

l = living.read_text(encoding="utf-8")
if "Clinical Day foundation: opaque + static." in l:
    raise SystemExit("v8.3.616 visual repair: living background patch already present")
l = l.replace(
    "private var shader: android.graphics.RuntimeShader? = null\n",
    "private var shader: android.graphics.RuntimeShader? = null\n    private var lightBackgroundShader: LinearGradient? = null\n",
    1,
)
marker = """    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat().coerceAtLeast(1f)"""
replacement = """    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)

        // Clinical Day foundation: opaque + static. Full-screen motion is kept out
        // of the light background so theme luminance is deterministic.
        if (profile.scene < 5) {
            paint.shader = lightBackgroundShader
            if (paint.shader != null) {
                canvas.drawRect(0f, 0f, w, h, paint)
            } else {
                canvas.drawColor(profile.backgroundA)
            }
            paint.shader = null
            return
        }"""
if marker not in l:
    raise SystemExit("v8.3.616 visual repair: living draw marker missing")
l = l.replace(marker, replacement, 1)
l = l.replace(
    "    override fun onBoundsChange(bounds: android.graphics.Rect) { super.onBoundsChange(bounds) }",
    """    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        lightBackgroundShader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(profile.backgroundA, profile.backgroundB, profile.backgroundC),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
    }""",
    1,
)
living.write_text(l, encoding="utf-8")

t = theme.read_text(encoding="utf-8")
if "alpha=if(isDark(c))70 else 45" not in t:
    raise SystemExit("v8.3.616 visual repair: expected wallpaper alpha not found")
t = t.replace("alpha=if(isDark(c))70 else 45", "alpha=if(isDark(c))70 else 10", 1)
theme.write_text(t, encoding="utf-8")

g = g.replace("versionCode = 701", "versionCode = 702", 1)
g = g.replace('versionName = "8.3.615"', 'versionName = "8.3.616"', 1)
gradle.write_text(g, encoding="utf-8")

print("v8.3.616 Clinical Day background repair applied")
print("Changed: Home Clinical Day background -> opaque cached static gradient")
print("Changed: global light background -> opaque cached static gradient")
print("Changed: light wallpaper overlay alpha 45 -> 10")
print("Version: 8.3.615/701 -> 8.3.616/702")
