#!/usr/bin/env python3
"""Phase 672: remove Light/Pastel default wallpaper and add contrast-safe semantic color roles."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
pkg = app / "src/main/java/com/localqbank/library"
gradle = app / "build.gradle.kts"
theme_path = pkg / "ThemeManager.kt"
if not gradle.is_file() or not theme_path.is_file():
    raise SystemExit("[672] FATAL: expected app/build.gradle.kts and ThemeManager.kt")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.669"' not in g or not re.search(r"\\bversionCode\\s*=\\s*755\\b", g):
    raise SystemExit("[672] FATAL: expected v8.3.669 / code 755 after Phase 671")
theme = theme_path.read_text(encoding="utf-8")
marker = "fun backgroundDrawable(c:Context):Drawable"
if theme.count(marker) != 1:
    raise SystemExit("[672] FATAL: ThemeManager.backgroundDrawable anchor missing/ambiguous")
# Do not load bundled Light/Pastel art. Keep the base gradient and any user-selected wallpaper.
old = "val bundled = if (!customSelected) RovexBundledVisualAssets.bitmapIfReady(c, get(c)) else null"
new = """// Light and Pastel intentionally use clean theme gradients; the bundled artwork in these
        // palettes was rejected for visual clutter. Explicit user wallpapers remain supported.
        val selectedTheme = get(c)
        val bundled = if (!customSelected && selectedTheme != LIGHT && selectedTheme != PASTEL)
            RovexBundledVisualAssets.bitmapIfReady(c, selectedTheme) else null"""
if theme.count(old) != 1:
    raise SystemExit("[672] FATAL: wallpaper selection anchor missing/ambiguous")
theme = theme.replace(old, new, 1)
theme_path.write_text(theme, encoding="utf-8")

# Central semantic colors: select foregrounds by the actual paired surface, not the app-wide
# theme alone. Explicit state colors (correct/wrong/selected) remain caller-owned.
colors_path = pkg / "RovexSemanticColors.kt"
if colors_path.exists():
    raise SystemExit("[672] FATAL: semantic color helper already exists; inspect before overlaying")
colors_path.write_text(r'''package com.localqbank.library

import android.content.Context
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Semantic foreground/background pairs for dynamically themed UI.
 *
 * Callers must pass the surface that will actually be painted behind the text. This avoids the
 * common failure mode where a dark-theme foreground is reused on a light dialog/card (or vice
 * versa). State colors such as correct/wrong answers remain explicit and are not auto-recolored.
 */
object RovexSemanticColors {
    enum class Role { PRIMARY_TEXT, SECONDARY_TEXT, DISABLED_TEXT, ICON, SURFACE, ELEVATED_SURFACE }

    fun surface(context: Context, elevated: Boolean = false): Int =
        if (ThemeManager.isDark(context)) {
            ThemeManager.elevated(context)
        } else {
            if (elevated) Color.rgb(250, 252, 255) else Color.rgb(244, 247, 252)
        }

    fun foreground(context: Context, background: Int, role: Role = Role.PRIMARY_TEXT): Int {
        val dark = ThemeManager.isDark(context)
        val primary = if (dark) Color.rgb(246, 248, 252) else Color.rgb(24, 29, 39)
        val secondary = if (dark) Color.rgb(201, 209, 222) else Color.rgb(66, 75, 91)
        val disabled = if (dark) Color.rgb(164, 174, 190) else Color.rgb(92, 101, 116)
        val candidate = when (role) {
            Role.PRIMARY_TEXT -> primary
            Role.SECONDARY_TEXT -> secondary
            Role.DISABLED_TEXT -> disabled
            Role.ICON -> if (dark) Color.rgb(225, 231, 241) else Color.rgb(48, 58, 76)
            Role.SURFACE, Role.ELEVATED_SURFACE -> return background
        }
        return ensureContrast(candidate, background, if (role == Role.PRIMARY_TEXT) 4.5 else 3.0)
    }

    fun ensureContrast(foreground: Int, background: Int, minimum: Double = 4.5): Int {
        if (contrastRatio(foreground, background) >= minimum) return foreground
        val black = Color.BLACK
        val white = Color.WHITE
        val target = if (contrastRatio(black, background) >= contrastRatio(white, background)) black else white
        var low = 0
        var high = 255
        var best = target
        // Find the smallest blend toward black/white that satisfies the WCAG threshold.
        while (low <= high) {
            val mid = (low + high) ushr 1
            val mixed = blend(foreground, target, mid)
            if (contrastRatio(mixed, background) >= minimum) {
                best = mixed
                high = mid - 1
            } else low = mid + 1
        }
        return best
    }

    fun contrastRatio(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun blend(from: Int, to: Int, amount: Int): Int {
        val t = amount / 255.0
        fun channel(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255)
        return Color.rgb(channel(Color.red(from), Color.red(to)),
            channel(Color.green(from), Color.green(to)), channel(Color.blue(from), Color.blue(to)))
    }

    private fun luminance(color: Int): Double {
        fun linear(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color))
    }
}
''', encoding="utf-8")

g = g.replace('versionName = "8.3.669"', 'versionName = "8.3.670"', 1).replace("versionCode = 755", "versionCode = 756", 1)
if 'versionName = "8.3.670"' not in g or "versionCode = 756" not in g:
    raise SystemExit("[672] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")

# Fail closed if we cannot identify the question screen in this baseline; the header is handled
# in the next overlay after this source scan can name its actual layout/container safely.
candidates = [p for p in pkg.glob("*.kt") if re.search(r"Question|Quiz|Solve", p.name, re.I)]
if not candidates:
    raise SystemExit("[672] FATAL: no question/quiz source candidates found; header must not be guessed")
print("[672] PASS: Light/Pastel bundled wallpaper disabled; user-selected wallpapers preserved.")
print("[672] PASS: WCAG contrast-aware semantic color utility added (4.5:1 normal, 3:1 secondary/large).")
print("[672] Question-screen candidates: " + ", ".join(p.name for p in candidates[:20]))
print("[672] NOTE: header compaction remains gated on identifying the actual root/header container.")
