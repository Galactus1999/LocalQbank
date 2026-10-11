#!/usr/bin/env python3
"""Phase 670: replace ornamental neon arcs with restrained optical highlights on shared surfaces."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
style_path = pkg / "RovexVisualSurfaceStyle.kt"
theme_path = pkg / "ThemeManager.kt"
for p in (gradle, style_path, theme_path):
    if not p.is_file():
        raise SystemExit(f"[670] FATAL: required source missing: {p}")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.668"' not in g or "versionCode = 754" not in g:
    raise SystemExit("[670] FATAL: expected v8.3.668 / code 754")

style = style_path.read_text(encoding="utf-8")
old = "return LayerDrawable(arrayOf(fill, tintLayer, sheen, border, RovexGlassRimDrawable(accent, r)))"
if style.count(old) != 1:
    raise SystemExit("[670] FATAL: expected shared glass stack missing/ambiguous")
# Remove the high-contrast, repeated white outline and multi-arc cyan decoration. Those arcs
# are the most visually distinctive defect in the supplied screenshots and are not refraction.
style = style.replace(old, "return LayerDrawable(arrayOf(fill, tintLayer, sheen, border, RovexGlassOpticsDrawable(accent, dark, ThemeManager.get(context), r)))", 1)
# A subtler border lets the highlight supply depth instead of outlining every card.
style = style.replace(
    "setStroke(d.toInt().coerceAtLeast(1), Color.argb(if (dark) 112 else 135, 255, 255, 255))",
    "setStroke(d.toInt().coerceAtLeast(1), Color.argb(if (dark) 76 else 88, 255, 255, 255))",
    1
)
start = style.find("private class RovexGlassRimDrawable(")
if start < 0 or style.count("private class RovexGlassRimDrawable(") != 1:
    raise SystemExit("[670] FATAL: old arc renderer missing/ambiguous")
style = style[:start].rstrip() + """

/**
 * Static optical highlight for a glass surface. A broad, off-centre radial reflection and a
 * restrained top-edge glint replace the old repeated neon arcs. This is an overlay highlight,
 * not backdrop refraction; it allocates no bitmap and runs no animation.
 */
private class RovexGlassOpticsDrawable(
    private val accent: Int,
    private val darkTheme: Boolean,
    private val themeName: String,
    private val radiusPx: Float
) : Drawable() {
    private val reflection = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val highlight = RectF()
    private var reflectionShader: RadialGradient? = null
    private var drawableAlpha = 255
    private var cornerRadius = 4f

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val inset = 1.25f
        rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        if (rect.width() < 4f || rect.height() < 4f) {
            reflectionShader = null
            return
        }
        cornerRadius = radiusPx.coerceAtMost(minOf(rect.width(), rect.height()) / 2f).coerceAtLeast(4f)
        highlight.set(
            rect.left - rect.width() * .12f,
            rect.top - rect.height() * .42f,
            rect.left + rect.width() * .84f,
            rect.top + rect.height() * .72f
        )
        val centerAlpha = when (themeName) { ThemeManager.AMOLED -> 18; ThemeManager.SPACE -> 27; ThemeManager.PANDORA -> 25; else -> if (darkTheme) 24 else 30 }
        val accentAlpha = when (themeName) { ThemeManager.AMOLED -> 5; ThemeManager.SPACE -> 14; ThemeManager.PANDORA -> 12; else -> if (darkTheme) 11 else 13 }
        reflectionShader = RadialGradient(
            highlight.centerX(), highlight.centerY(),
            maxOf(highlight.width(), highlight.height()) * .58f,
            intArrayOf(
                Color.argb(centerAlpha, 255, 255, 255),
                Color.argb(accentAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .42f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() < 4 || b.height() < 4) return
        if (b.width() < 4 || b.height() < 4 || rect.width() < 4f || rect.height() < 4f) return
        val save = canvas.save()
        canvas.clipRect(b)
        reflection.shader = reflectionShader
        reflection.alpha = drawableAlpha
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, reflection)
        reflection.shader = null

        // A short, low-contrast top glint; no long corner arcs or decorative bottom streaks.
        edge.color = Color.WHITE
        edge.strokeWidth = .9f
        edge.alpha = (if (darkTheme) 78 else 86) * drawableAlpha / 255
        val y = rect.top + .8f
        canvas.drawLine(rect.left + r * .72f, y, rect.right - r * 1.05f, y, edge)
        edge.color = accent
        edge.strokeWidth = 1.1f
        edge.alpha = (when (themeName) { ThemeManager.AMOLED -> 24; ThemeManager.SPACE -> 58; ThemeManager.PANDORA -> 52; else -> if (darkTheme) 48 else 38 }) * drawableAlpha / 255
        canvas.drawLine(rect.left + r * 1.1f, rect.bottom - .8f,
            rect.left + r * 1.75f, rect.bottom - .8f, edge)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { drawableAlpha = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        reflection.colorFilter = colorFilter
        edge.colorFilter = colorFilter
        invalidateSelf()
    }
    @Deprecated("Deprecated by Android")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
"""
# Ensure required Android imports are present.
imports = {
    "import android.graphics.RadialGradient": "import android.graphics.RadialGradient",
    "import android.graphics.Shader": "import android.graphics.Shader",
    "import android.graphics.drawable.Drawable": "import android.graphics.drawable.Drawable",
}
for imp in imports:
    if imp not in style:
        anchor = "import android.graphics.RectF"
        if anchor not in style:
            raise SystemExit("[670] FATAL: import insertion anchor missing")
        style = style.replace(anchor, anchor + "\n" + imp, 1)
if "RovexGlassRimDrawable" in style or "canvas.drawArc(" in style:
    raise SystemExit("[670] FATAL: legacy arc renderer remains in shared surface style")
if "RovexGlassOpticsDrawable(accent, dark, ThemeManager.get(context), r)" not in style:
    raise SystemExit("[670] FATAL: new optical highlight not wired to shared glass primitive")
if "private var reflectionShader: RadialGradient? = null" not in style or "override fun onBoundsChange" not in style:
    raise SystemExit("[670] FATAL: radial shader must be cached when bounds change, not allocated on every draw")
style_path.write_text(style, encoding="utf-8")

g = g.replace('versionName = "8.3.668"', 'versionName = "8.3.669"', 1).replace("versionCode = 754", "versionCode = 755", 1)
if 'versionName = "8.3.669"' not in g or "versionCode = 755" not in g:
    raise SystemExit("[670] FATAL: version bump failed")
gradle.write_text(g, encoding="utf-8")
print("[670] PASS: removed repeated arc/line ornament; shared surfaces now use a static localized radial reflection and restrained glint; v8.3.669 / 755")
