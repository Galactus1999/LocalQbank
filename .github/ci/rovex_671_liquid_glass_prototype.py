#!/usr/bin/env python3
"""Phase 671: add a debug-only real liquid-glass renderer preview; production UI stays unchanged."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle.kts"
settings = root / "settings.gradle.kts"
pkg = root / "app/src/debug/java/com/localqbank/library"
manifest = root / "app/src/debug/AndroidManifest.xml"
if not gradle.is_file() or not settings.is_file():
    raise SystemExit("[671] FATAL: expected app/build.gradle.kts and settings.gradle.kts")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.669"' not in g or re.search(r"\bversionCode\s*=\s*755\b", g) is None:
    raise SystemExit("[671] FATAL: expected v8.3.669 / code 755 after phase 670")
if "com.github.QWEA0:liquidglass" in g:
    raise SystemExit("[671] FATAL: liquid-glass dependency already present; refusing duplicate injection")

def find_block(text, pattern, after=0):
    m = re.search(pattern, text[after:])
    if not m:
        return None
    brace = text.find("{", after + m.start(), after + m.end() + 1)
    if brace < 0:
        return None
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return brace, i
    return None

s = settings.read_text(encoding="utf-8")
if "https://jitpack.io" not in s:
    drm = find_block(s, r"\bdependencyResolutionManagement\s*\{")
    if not drm:
        raise SystemExit("[671] FATAL: dependencyResolutionManagement block not found; cannot add JitPack safely")
    repos = find_block(s, r"\brepositories\s*\{", drm[0])
    if not repos or repos[0] > drm[1]:
        raise SystemExit("[671] FATAL: dependency repositories block not found inside dependencyResolutionManagement")
    insert = '\n        maven { url = uri("https://jitpack.io") } // Rovex debug-only liquid-glass prototype, pinned v2.0.11\n'
    s = s[:repos[1]] + insert + s[repos[1]:]
    settings.write_text(s, encoding="utf-8")

deps = find_block(g, r"\bdependencies\s*\{")
if not deps:
    raise SystemExit("[671] FATAL: app dependencies block not found")
g = g[:deps[0] + 1] + '\n    debugImplementation("com.github.QWEA0:liquidglass:v2.0.11") // isolated renderer lab only\n' + g[deps[0] + 1:]
gradle.write_text(g, encoding="utf-8")

pkg.mkdir(parents=True, exist_ok=True)
src = r'''package com.localqbank.library

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * Debug-only optical lab for evaluating real backdrop sampling/refraction over Rovex wallpaper
 * assets and high-frequency UI content. It is intentionally not wired into production navigation.
 * No sensor-driven highlight or continuous animation: thermal/per-frame costs stay measurable.
 */
class RovexLiquidGlassPreviewActivity : Activity() {
    private lateinit var backdrop: FrameLayout
    private lateinit var wallpaper: ImageView
    private lateinit var glass: LiquidGlassView
    private var palette = 0
    private val themes = listOf(
        PreviewTheme("AMOLED", "amoled", Color.rgb(160, 205, 255), Color.rgb(5, 7, 12)),
        PreviewTheme("SPACE", "space", Color.rgb(145, 135, 255), Color.rgb(13, 13, 36)),
        PreviewTheme("PANDORA", "pandora", Color.rgb(110, 225, 205), Color.rgb(7, 25, 27))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 7, 12)
        window.navigationBarColor = Color.rgb(5, 7, 12)
        backdrop = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 7, 12)) }
        wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Rovex wallpaper backdrop for optical evaluation"
        }
        backdrop.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        backdrop.addView(buildDetailContent(), FrameLayout.LayoutParams(-1, -1))

        val root = FrameLayout(this)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        glass = LiquidGlassView(this).apply {
            cornerRadius = dp(30).toFloat()
            material = GlassMaterial.REGULAR
            blurAmount = 0.045f
            saturation = 118f
            downsampleScale = 3
            refractionHeight = 82f
            bevelWidth = 25f
            refractionFalloff = 2f
            dispersionStrength = 0.075f
            glassTint = 0x241B2D48
            enableDynamicBackground = true
            enableSensorHighlight = false
            enableAdaptiveTint = true
            enablePressEffect = false
            collectFrameStats = false
            backdropSource = backdrop
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Live liquid glass surface preview"
        }
        val glassContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(22))
            addView(label("ROVEX  /  OPTICS LAB", 11f, Color.WHITE, true))
            addView(space(8))
            addView(label("Liquid glass", 30f, Color.WHITE, true))
            addView(space(8))
            addView(label("Real backdrop blur · SDF refraction · restrained spectral rim", 14f, 0xFFE2E8F0.toInt(), false))
            addView(space(18))
            val chips = LinearLayout(this@RovexLiquidGlassPreviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            themes.forEachIndexed { index, t ->
                val chip = label(t.name, 10f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = rounded(0x2AFFFFFF, dp(16).toFloat(), 0x55FFFFFF)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { applyTheme(index) }
                }
                val lp = LinearLayout.LayoutParams(0, -2, 1f)
                lp.marginEnd = dp(7)
                chips.addView(chip, lp)
            }
            addView(chips)
            addView(space(16))
            addView(label("API tier: ${android.os.Build.VERSION.SDK_INT}  •  sensor effects OFF  •  debug only", 11f, 0xFFCBD5E1.toInt(), false))
        }
        glass.addView(glassContent, FrameLayout.LayoutParams(-1, -1))
        val cardLp = FrameLayout.LayoutParams(-1, dp(300), Gravity.CENTER).apply {
            leftMargin = dp(22); rightMargin = dp(22)
        }
        root.addView(glass, cardLp)
        val close = label("CLOSE PREVIEW  ×", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0x33000000, dp(18).toFloat(), 0x66FFFFFF)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        root.addView(close, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(26)
        })
        setContentView(root)
        applyTheme(0)
    }

    private fun buildDetailContent(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(54), dp(24), dp(54))
        }
        column.addView(label("ROVEX", 17f, Color.WHITE, true))
        column.addView(space(8))
        column.addView(label("QUESTION BANK  /  DAILY REVISION", 10f, 0xFFCBD5E1.toInt(), true))
        column.addView(space(20))
        column.addView(detailRow("01", "NEUROANATOMY", "Corticospinal tract · lesion localization"))
        column.addView(space(12))
        column.addView(detailRow("02", "PHARMACOLOGY", "Receptor selectivity · dose response"))
        column.addView(space(12))
        column.addView(detailRow("03", "PATHOLOGY", "Cell injury · high-yield distinctions"))
        column.addView(space(20))
        column.addView(label("TODAY'S REVISION", 11f, 0xFFD7DCE7.toInt(), true))
        column.addView(space(8))
        column.addView(detailRow("84", "QUESTIONS REVIEWED", "SRS due · 12 cards pending"))
        column.addView(space(12))
        column.addView(detailRow("★", "BOOKMARKED", "Important differentials and traps"))
        column.addView(space(24))
        column.addView(label("Fine text and edges are intentional: they make optical displacement visible.", 11f, 0xFFB7C3D5.toInt(), false))
        scroll.addView(column)
        return scroll
    }

    private fun detailRow(index: String, title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(0x302B4262, dp(15).toFloat(), 0x557E9FC8)
            addView(label(index, 12f, themes[palette].accent, true), LinearLayout.LayoutParams(dp(38), -2))
            val text = LinearLayout(this@RovexLiquidGlassPreviewActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 11f, Color.WHITE, true))
                addView(space(5))
                addView(label(subtitle, 10f, 0xFFD1D8E4.toInt(), false))
            }
            addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun applyTheme(index: Int) {
        palette = index.coerceIn(themes.indices)
        val t = themes[palette]
        val id = resources.getIdentifier("rovex_wallpaper_${t.resource}", "drawable", packageName)
        val drawable: Drawable? = if (id != 0) runCatching { resources.getDrawable(id, theme) }.getOrNull() else null
        wallpaper.setImageDrawable(drawable ?: ColorDrawable(t.base))
        glass.glassTint = when (palette) {
            0 -> 0x241B2D48
            1 -> 0x282A1B59
            else -> 0x22206058
        }
        glass.invalidate()
        backdrop.invalidate()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 3
        }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private data class PreviewTheme(val name: String, val resource: String, val accent: Int, val base: Int)
}
'''
(pkg / "RovexLiquidGlassPreviewActivity.kt").write_text(src, encoding="utf-8")
manifest.parent.mkdir(parents=True, exist_ok=True)
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="com.localqbank.library.RovexLiquidGlassPreviewActivity"
            android:exported="true"
            android:label="Rovex Liquid Glass Preview"
            android:theme="@android:style/Theme.Material.NoActionBar" />
    </application>
</manifest>
''', encoding="utf-8")

if 'debugImplementation("com.github.QWEA0:liquidglass:v2.0.11")' not in g:
    raise SystemExit("[671] FATAL: pinned debug-only renderer dependency missing")
if "LiquidGlassView" not in src or "enableDynamicBackground = true" not in src or "enableSensorHighlight = false" not in src:
    raise SystemExit("[671] FATAL: preview must use live backdrop capture and keep sensor-driven effects disabled")
if "RovexLiquidGlassPreviewActivity" not in manifest.read_text(encoding="utf-8"):
    raise SystemExit("[671] FATAL: debug preview manifest registration missing")
if (root / "app/src/main/java/com/localqbank/library/RovexLiquidGlassPreviewActivity.kt").exists():
    raise SystemExit("[671] FATAL: debug preview must not leak into production source set")
print("[671] PASS: pinned QWEA0 liquidglass v2.0.11 is debug-only; isolated AMOLED/Space/Pandora backdrop preview added; production surface renderer untouched")
