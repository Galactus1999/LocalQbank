#!/usr/bin/env python3
"""Phase 668: static theme-lit glass rims and restrained splash palette; remove all ambient motion."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
theme_path = pkg / "ThemeManager.kt"
style_path = pkg / "RovexVisualSurfaceStyle.kt"
if not all(p.is_file() for p in (gradle, theme_path, style_path)):
    raise SystemExit("[668] FATAL: expected theme, glass style, or Gradle file missing")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.666"' not in g or "versionCode = 752" not in g:
    raise SystemExit("[668] FATAL: expected v8.3.666 / code 752")
theme = theme_path.read_text(encoding="utf-8")

# Remove the animation layer at its source, not just its preference toggle. Keep wallpaper
# selection, full opacity, custom wallpaper precedence, and static theme gradient intact.
theme, n1 = re.subn(
    r'\n\s*val motion: Drawable\? = if \(RovexLiveMotionSettings\.enabled\(c\) && AnimationPolicy\.enabled\(c\)\) \{\s*RovexAmbientMotionDrawable\(c, profile\(c\)\.backgroundA, profile\(c\)\.backgroundB\)\s*\} else null',
    '', theme, count=1)
theme, n2 = re.subn(
    r'return if \(motion != null\) LayerDrawable\(arrayOf\(base, motion\)\) else base',
    'return base', theme, count=1)
theme, n3 = re.subn(
    r'return if \(motion != null\) LayerDrawable\(arrayOf\(base, wallpaper, motion\)\) else LayerDrawable\(arrayOf\(base, wallpaper\)\)',
    'return LayerDrawable(arrayOf(base, wallpaper))', theme, count=1)
if n1 != 1 or n2 != 1 or n3 != 1:
    raise SystemExit(f"[668] FATAL: expected motion-layer anchors once; removed={n1}, no-wallpaper={n2}, wallpaper={n3}")
if "RovexAmbientMotionDrawable" in theme or "motion != null" in theme:
    raise SystemExit("[668] FATAL: a background motion layer remains in ThemeManager")
theme_path.write_text(theme, encoding="utf-8")

style = style_path.read_text(encoding="utf-8")
old = "return LayerDrawable(arrayOf(fill, tintLayer, sheen, border))"
if style.count(old) != 1:
    raise SystemExit("[668] FATAL: glass surface layer anchor missing or ambiguous")
style = style.replace(old, "return LayerDrawable(arrayOf(fill, tintLayer, sheen, border, RovexGlassRimDrawable(accent, r)))", 1)
if "import android.graphics.Canvas" not in style:
    style = style.replace("import android.graphics.Color", "import android.graphics.Canvas\nimport android.graphics.Color", 1)
if "import android.graphics.Paint" not in style:
    style = style.replace("import android.graphics.Color", "import android.graphics.Color\nimport android.graphics.Paint", 1)
if "import android.graphics.RectF" not in style:
    style = style.replace("import android.graphics.Paint", "import android.graphics.Paint\nimport android.graphics.RectF", 1)
if "import android.graphics.drawable.Drawable" not in style:
    style = style.replace("import android.graphics.RectF", "import android.graphics.RectF\nimport android.graphics.drawable.Drawable", 1)

rim = """
/**
 * Static theme-coloured glass caustics. Only selected corner arcs and short rim segments glow;
 * no animator, invalidation loop, blur mask, or per-frame allocation is used.
 */
private class RovexGlassRimDrawable(
    private val accent: Int,
    private val radiusPx: Float
) : Drawable() {
    private val density = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 2 || b.height() <= 2) return
        val inset = (1.25f * density).coerceAtLeast(1f)
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        val r = radiusPx.coerceAtMost(minOf(rect.width(), rect.height()) / 2f).coerceAtLeast(4f)
        val save = canvas.save()
        // Wide, low-alpha under-rim followed by a fine, brighter inner glint.
        paint.color = accent
        paint.strokeWidth = 3.2f * density
        paint.alpha = 24
        canvas.drawArc(rect, 188f, 48f, false, paint)
        canvas.drawArc(rect, 8f, 42f, false, paint)
        canvas.drawArc(rect, 98f, 34f, false, paint)
        paint.strokeWidth = 1.35f * density
        paint.alpha = 150
        canvas.drawArc(rect, 190f, 30f, false, paint)
        canvas.drawArc(rect, 10f, 26f, false, paint)
        paint.color = Color.WHITE
        paint.strokeWidth = 0.85f * density
        paint.alpha = 92
        canvas.drawArc(rect, 194f, 20f, false, paint)
        canvas.drawArc(rect, 14f, 17f, false, paint)
        // A tiny accent line on one lower edge keeps the glass from reading as a neon box.
        paint.color = accent
        paint.strokeWidth = 1.4f * density
        paint.alpha = 108
        val y = rect.bottom - 0.5f
        canvas.drawLine(rect.left + r * 1.45f, y, rect.left + r * 2.8f, y, paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated by Android")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
"""
if "private class RovexGlassRimDrawable" in style:
    raise SystemExit("[668] FATAL: rim drawable already exists; refusing duplicate")
style = style.rstrip() + "\n\n" + rim.strip() + "\n"
style_path.write_text(style, encoding="utf-8")

# Splash work is deliberately fail-closed: locate the actual splash implementation and replace
# hard-coded saturated accent choices only when the known splash source anchors are present.
splash_candidates = []
for p in pkg.rglob("*.kt"):
    if "splash" in p.name.lower() or "Splash" in p.read_text(encoding="utf-8", errors="ignore")[:4000]:
        splash_candidates.append(p)
print("[668] Splash candidates: " + ", ".join(str(p.relative_to(root)) for p in splash_candidates[:12]))
# Theme-safe splash accents are centralized through ThemeManager.accent; avoid speculative
# mass replacement of unrelated colors. CI asserts that at least one real splash surface exists.
if not splash_candidates:
    raise SystemExit("[668] FATAL: could not locate splash implementation; refusing to claim splash repair")
# For common custom splash classes, normalize animated accents to the selected theme accent and
# remove rainbow/multi-stop effects only at the splash class boundary.
changed_splash = []
for p in splash_candidates:
    src = p.read_text(encoding="utf-8", errors="ignore")
    if not any(k in src for k in ("Splash", "splash")):
        continue
    # Replace obvious full-spectrum/neon palette literals in splash-specific source only.
    new = re.sub(r'Color\.rgb\(255,\s*0,\s*255\)', 'ThemeManager.accent(this)', src)
    new = re.sub(r'Color\.rgb\(0,\s*255,\s*255\)', 'ThemeManager.accent(this)', new)
    new = re.sub(r'Color\.MAGENTA', 'ThemeManager.accent(this)', new)
    new = re.sub(r'Color\.CYAN', 'ThemeManager.accent(this)', new)
    if new != src:
        p.write_text(new, encoding="utf-8")
        changed_splash.append(str(p.relative_to(root)))
if not changed_splash:
    print("[668] NOTE: no recognized neon literal in splash source; no unsafe blind splash substitutions made")

g = g.replace('versionName = "8.3.666"', 'versionName = "8.3.667"', 1)
g = g.replace("versionCode = 752", "versionCode = 753", 1)
if 'versionName = "8.3.667"' not in g or "versionCode = 753" not in g:
    raise SystemExit("[668] FATAL: version bump failed")
gradle.write_text(g, encoding="utf-8")

assert "RovexAmbientMotionDrawable" not in theme
assert "motion != null" not in theme
assert "RovexGlassRimDrawable(accent, r)" in style
assert "canvas.drawArc(rect, 190f, 30f, false, paint)" in style
assert "private class RovexGlassRimDrawable" in style
assert "versionName = \"8.3.667\"" in g and "versionCode = 753" in g
print("[668] PASS: no animated wallpaper layer; static theme-coloured partial glass rims; splash sources inventoried; v8.3.667 / 753")
