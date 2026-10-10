#!/usr/bin/env python3
from pathlib import Path
import re, sys

EXPECTED_VERSION = 'versionName = "8.3.626"'
EXPECTED_CODE = "versionCode = 712"
NEW_VERSION = 'versionName = "8.3.627"'
NEW_CODE = "versionCode = 713"

LAYER = r'''package com.localqbank.library

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Premium Ren/Ben visual treatment on the real RenActivity hierarchy. */
object RovexBenProductionLayer {
    private const val TAG = "ROVEX_BEN_VISUAL_627"

    fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.getTag() == TAG) return
        content.setTag(TAG)
        content.background = RovexBenBackdropDrawable(activity)
        styleTree(activity, content, 0)
    }

    private fun styleTree(activity: Activity, parent: ViewGroup, depth: Int) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val name = resourceName(activity, child.id).lowercase()
            val surface = isSurfaceCandidate(child, name, depth)
            if (surface) styleSurface(activity, child, name)
            if (child is TextView) styleText(activity, child, name)
            if (child is Button || child is EditText) styleControl(activity, child, name)
            if (child is ViewGroup &&
                child !is android.widget.ScrollView &&
                child !is android.widget.HorizontalScrollView &&
                child !is androidx.recyclerview.widget.RecyclerView) {
                styleTree(activity, child, depth + 1)
            }
        }
    }

    private fun isSurfaceCandidate(view: View, name: String, depth: Int): Boolean {
        if (depth == 0 || view is android.widget.ScrollView || view is androidx.recyclerview.widget.RecyclerView) return false
        if (name.contains("toolbar") || name.contains("appbar") || name.contains("nav")) return false
        return name.contains("card") || name.contains("panel") || name.contains("chat") ||
            name.contains("message") || name.contains("response") || name.contains("prompt") ||
            name.contains("input") || name.contains("composer") || name.contains("ren") ||
            name.contains("ben") || name.contains("ai") || name.contains("container")
    }

    private fun styleSurface(activity: Activity, view: View, name: String) {
        val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), ThemeManager.isDark(activity))
        val d = activity.resources.displayMetrics.density
        val fill = when {
            name.contains("response") || name.contains("message") -> p.surfaceElevated
            name.contains("input") || name.contains("composer") -> p.surface
            else -> p.surfaceContainer
        }
        val stroke = when {
            name.contains("response") || name.contains("ben") -> p.primary
            name.contains("input") || name.contains("composer") -> p.secondary
            else -> p.outline
        }
        view.background = GradientDrawable().apply {
            cornerRadius = 22f * d
            setColor(fill)
            setStroke((1f*d).toInt().coerceAtLeast(1), stroke)
        }
        view.elevation = 2.5f * d
        if (view is ViewGroup) {
            view.clipToOutline = true
            view.setPadding((16*d).toInt(), (14*d).toInt(), (16*d).toInt(), (14*d).toInt())
        }
        bindPress(view)
    }

    private fun styleText(activity: Activity, tv: TextView, name: String) {
        tv.includeFontPadding = false
        if (tv !is Button && tv !is EditText) tv.background = null
        tv.ellipsize = TextUtils.TruncateAt.END
        if (tv.maxLines <= 0 || tv.maxLines == Int.MAX_VALUE) tv.maxLines = if (tv.textSize >= 18f) 3 else 5
        val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), ThemeManager.isDark(activity))
        tv.setTextColor(
            when {
                name.contains("title") || name.contains("header") || tv.textSize >= 20f -> p.onSurface
                name.contains("error") -> p.error
                else -> p.onSurfaceVariant
            }
        )
    }

    private fun styleControl(activity: Activity, view: View, name: String) {
        val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), ThemeManager.isDark(activity))
        val d = activity.resources.displayMetrics.density
        val accent = name.contains("send") || name.contains("ask") || name.contains("submit")
        view.background = GradientDrawable().apply {
            cornerRadius = 17f * d
            setColor(if (accent) p.primary else p.surfaceElevated)
            setStroke((1f*d).toInt().coerceAtLeast(1), if (accent) p.primary else p.outline)
        }
        if (view is Button) {
            view.setTextColor(if (accent) p.onPrimary else p.onSurface)
            view.isAllCaps = false
        }
        bindPress(view)
    }

    private fun bindPress(view: View) {
        if (!view.isClickable && !view.hasOnClickListeners()) return
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(.985f).scaleY(.985f).setDuration(70L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
            false
        }
    }

    private fun resourceName(activity: Activity, id: Int): String =
        try { if (id == View.NO_ID) "" else activity.resources.getResourceEntryName(id) } catch (_: Exception) { "" }
}

private class RovexBenBackdropDrawable(activity: Activity) : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dark = ThemeManager.isDark(activity)
    private val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), dark)

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        paint.shader = LinearGradient(
            0f, 0f, bounds.width().toFloat().coerceAtLeast(1f),
            bounds.height().toFloat().coerceAtLeast(1f),
            intArrayOf(
                p.background,
                BenColorUtils.blend(p.background, p.primaryContainer, if (dark) .20f else .11f),
                BenColorUtils.blend(p.background, p.tertiary, if (dark) .08f else .05f)
            ),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) { canvas.drawRect(bounds, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

private object BenColorUtils {
    fun blend(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b)-Color.red(a))*t).toInt(),
            (Color.green(a) + (Color.green(b)-Color.green(a))*t).toInt(),
            (Color.blue(a) + (Color.blue(b)-Color.blue(a))*t).toInt()
        )
    }
}
'''

def patch_activity(root: Path):
    targets = list(root.rglob("RenActivity.kt"))
    if not targets:
        raise SystemExit("v8.3.627: RenActivity.kt not found; refusing Ben visual patch")
    target = targets[0]
    s = target.read_text(encoding="utf-8")
    if "RovexBenProductionLayer.apply" in s:
        return
    m = re.search(r'(setContentView\([^\n]+\))', s)
    if not m:
        raise SystemExit("v8.3.627: RenActivity has no setContentView anchor")
    insertion = m.group(1) + '\n        window.decorView.post { RovexBenProductionLayer.apply(this@RenActivity) }'
    s = s[:m.start()] + insertion + s[m.end():]
    target.write_text(s, encoding="utf-8")

def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_627_ben_premium_overlay.py <project>")
    root = Path(sys.argv[1]).resolve()
    gradle = root / "app/build.gradle.kts"
    if not gradle.is_file():
        raise SystemExit("v8.3.627: missing build.gradle.kts")
    g = gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g:
        raise SystemExit("v8.3.627: requires v8.3.626/712")
    pkg = root / "app/src/main/java/com/localqbank/library"
    pkg.mkdir(parents=True, exist_ok=True)
    (pkg / "RovexBenProductionLayer.kt").write_text(LAYER, encoding="utf-8")
    patch_activity(root)
    g = g.replace(EXPECTED_VERSION, NEW_VERSION, 1).replace(EXPECTED_CODE, NEW_CODE, 1)
    gradle.write_text(g, encoding="utf-8")
    print("v8.3.627 Ben/Ren premium visual phase: APPLIED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
