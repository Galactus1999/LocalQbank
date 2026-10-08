#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1]).resolve()

theme = ROOT / "app/src/main/java/com/localqbank/library/ThemeAtmosphereDrawable.kt"
gradle = ROOT / "app/build.gradle.kts"

if not theme.is_file():
    raise SystemExit(f"Missing expected visual source: {theme}")
if not gradle.is_file():
    raise SystemExit(f"Missing expected app Gradle file: {gradle}")

old = theme.read_text(encoding="utf-8")
if "class ThemeAtmosphereDrawable" not in old:
    raise SystemExit("ThemeAtmosphereDrawable signature not found; refusing blind patch")

new_theme = r'''package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable

/**
 * Clinical Day background foundation.
 *
 * Deliberately separates the opaque theme base from future decorative assets.
 * The old implementation used full-screen procedural blobs/orbs, which made
 * the light UI read as a dark canvas and produced a generic "aurora blob"
 * appearance. This drawable is intentionally restrained: an opaque theme
 * base plus two precomputed contour paths. Imported Lottie/SVG/bitmap assets
 * will be hosted above this base in a dedicated visual layer in a later phase.
 *
 * No allocations or shader creation occur in draw().
 */
class ThemeAtmosphereDrawable(private val context: Context) : Drawable() {
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val contourPath = Path()
    private val contourPathSecondary = Path()
    private var cachedWidth = -1f
    private var cachedHeight = -1f

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        cachedWidth = -1f
        cachedHeight = -1f
        rebuildPaths()
    }

    private fun rebuildPaths() {
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        if (w == cachedWidth && h == cachedHeight) return

        cachedWidth = w
        cachedHeight = h

        contourPath.reset()
        contourPath.moveTo(-0.08f * w, 0.23f * h)
        contourPath.cubicTo(
            0.18f * w, 0.10f * h,
            0.36f * w, 0.33f * h,
            0.57f * w, 0.21f * h
        )
        contourPath.cubicTo(
            0.76f * w, 0.10f * h,
            0.91f * w, 0.22f * h,
            1.08f * w, 0.12f * h
        )

        contourPathSecondary.reset()
        contourPathSecondary.moveTo(-0.08f * w, 0.79f * h)
        contourPathSecondary.cubicTo(
            0.19f * w, 0.68f * h,
            0.39f * w, 0.91f * h,
            0.62f * w, 0.79f * h
        )
        contourPathSecondary.cubicTo(
            0.81f * w, 0.69f * h,
            0.94f * w, 0.81f * h,
            1.08f * w, 0.73f * h
        )
    }

    override fun draw(canvas: Canvas) {
        rebuildPaths()

        // Always paint an opaque base first. This prevents a stale/dark
        // window background from bleeding through the light theme.
        basePaint.shader = null
        basePaint.color = ThemeManager.bg(context)
        basePaint.alpha = 255
        canvas.drawRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), basePaint)

        val dark = ThemeManager.isDark(context)
        val accent = ThemeManager.accent(context)

        contourPaint.strokeWidth = (if (dark) 1.5f else 1.2f) *
            context.resources.displayMetrics.density
        contourPaint.color = if (dark) {
            Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent))
        } else {
            Color.argb(22, Color.red(accent), Color.green(accent), Color.blue(accent))
        }

        canvas.drawPath(contourPath, contourPaint)

        contourPaint.color = if (dark) {
            Color.argb(18, 255, 255, 255)
        } else {
            Color.argb(14, Color.red(accent), Color.green(accent), Color.blue(accent))
        }
        canvas.drawPath(contourPathSecondary, contourPaint)
    }

    override fun setAlpha(alpha: Int) {
        basePaint.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        basePaint.colorFilter = colorFilter
        contourPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
'''

theme.write_text(new_theme, encoding="utf-8")

s = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.615"' not in s or "versionCode = 701" not in s:
    raise SystemExit("Expected v8.3.615/versionCode 701 baseline not found; refusing version drift")
s = s.replace('versionCode = 701', 'versionCode = 702', 1)
s = s.replace('versionName = "8.3.615"', 'versionName = "8.3.616"', 1)
gradle.write_text(s, encoding="utf-8")

print("Applied Clinical Day background foundation overlay.")
print("Version: 8.3.615/701 -> 8.3.616/702")
print(f"Changed: {theme.relative_to(ROOT)}")
print(f"Changed: {gradle.relative_to(ROOT)}")
