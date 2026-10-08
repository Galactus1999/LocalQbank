#!/usr/bin/env python3
from pathlib import Path
import re, sys

EXPECTED_VERSION = 'versionName = "8.3.625"'
EXPECTED_CODE = "versionCode = 711"
NEW_VERSION = 'versionName = "8.3.626"'
NEW_CODE = "versionCode = 712"

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

/** Premium QBank-library treatment applied to the real MainQBankLibraryActivity hierarchy. */
object RovexQBankProductionLayer {
    private const val TAG = "ROVEX_QBANK_VISUAL_626"

    fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.getTag() == TAG) return
        content.setTag(TAG)
        content.background = RovexQBankBackdropDrawable(activity)
        styleTree(activity, content, 0)
    }

    private fun styleTree(activity: Activity, parent: ViewGroup, depth: Int) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val name = resourceName(activity, child.id).lowercase()
            val candidate = isSurfaceCandidate(child, name, depth)
            if (candidate) styleSurface(activity, child, name)
            if (child is TextView) styleText(activity, child, name, depth)
            if (child is Button || child is EditText) styleControl(activity, child)
            if (child is ViewGroup && child !is android.widget.ScrollView &&
                child !is android.widget.HorizontalScrollView &&
                child !is android.widget.RecyclerView) {
                styleTree(activity, child, depth + 1)
            }
        }
    }

    private fun isSurfaceCandidate(view: View, name: String, depth: Int): Boolean {
        if (depth == 0 || view is android.widget.ScrollView || view is android.widget.RecyclerView) return false
        if (name.contains("nav") || name.contains("toolbar") || name.contains("appbar")) return false
        return name.contains("card") || name.contains("item") || name.contains("row") ||
            name.contains("source") || name.contains("qbank") || name.contains("library") ||
            name.contains("panel") || name.contains("container")
    }

    private fun styleSurface(activity: Activity, view: View, name: String) {
        val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), ThemeManager.isDark(activity))
        val fill = when {
            name.contains("selected") || view.isSelected -> p.selectedContainer
            name.contains("source") || name.contains("qbank") -> p.surface
            else -> p.surfaceContainer
        }
        val stroke = when {
            name.contains("selected") || view.isSelected -> p.primary
            name.contains("source") -> p.secondary
            else -> p.outline
        }
        val d = activity.resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            cornerRadius = 20f * d
            setColor(fill)
            setStroke((1f * d).toInt().coerceAtLeast(1), stroke)
        }
        view.elevation = 2.5f * d
        if (view is ViewGroup) view.clipToOutline = true
        if (view is ViewGroup) view.setPadding((14*d).toInt(), (12*d).toInt(), (14*d).toInt(), (12*d).toInt())
        bindPress(view)
    }

    private fun styleText(activity: Activity, tv: TextView, name: String, depth: Int) {
        tv.includeFontPadding = false
        if (tv !is Button && tv !is EditText) tv.background = null
        tv.ellipsize = TextUtils.TruncateAt.END
        if (tv.maxLines <= 0 || tv.maxLines == Int.MAX_VALUE) tv.maxLines = if (tv.textSize >= 18f) 2 else 3
        val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), ThemeManager.isDark(activity))
        tv.setTextColor(if (tv.textSize >= 18f || name.contains("title") || name.contains("header")) p.onSurface else p.onSurfaceVariant)
    }

    private fun styleControl(activity: Activity, view: View) {
        val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), ThemeManager.isDark(activity))
        val d = activity.resources.displayMetrics.density
        view.background = GradientDrawable().apply {
            cornerRadius = 16f * d
            setColor(p.surfaceElevated)
            setStroke((1f*d).toInt().coerceAtLeast(1), p.outline)
        }
        if (view is Button) {
            view.setTextColor(p.onSurface)
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

private class RovexQBankBackdropDrawable(activity: Activity) : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dark = ThemeManager.isDark(activity)
    private val p = RovexPremiumPalette.forKey(ThemeManager.get(activity), dark)

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val top = if (dark) p.background else p.background
        paint.shader = LinearGradient(
            0f, 0f, 0f, bounds.height().toFloat().coerceAtLeast(1f),
            intArrayOf(top, ColorUtils.blend(top, p.primaryContainer, if (dark) .16f else .10f), top),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

private object ColorUtils {
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

def patch_activity(project: Path):
    candidates = list(project.rglob("MainQBankLibraryActivity.kt"))
    if not candidates:
        raise SystemExit("v8.3.626: MainQBankLibraryActivity.kt not found; refusing visual patch")
    target = candidates[0]
    s = target.read_text(encoding="utf-8")
    if "RovexQBankProductionLayer.apply" in s:
        return
    # Prefer injection immediately after the first setContentView in onCreate.
    m = re.search(r'(setContentView\([^\n]+\))', s)
    if not m:
        raise SystemExit("v8.3.626: MainQBankLibraryActivity has no setContentView anchor")
    insertion = m.group(1) + '\n        window.decorView.post { RovexQBankProductionLayer.apply(this@MainQBankLibraryActivity) }'
    s = s[:m.start()] + insertion + s[m.end():]
    target.write_text(s, encoding="utf-8")

def patch_visual_truth(project: Path):
    tests = list(project.rglob("RovexVisualTruthCaptureTest.kt"))
    if not tests:
        raise SystemExit("v8.3.626: visual truth test not found")
    target = tests[0]
    s = target.read_text(encoding="utf-8")
    old = '''ActivityScenario.launch<RovexSectionDashboardActivity>(
            Intent(context, RovexSectionDashboardActivity::class.java).putExtra("section", "qbank")
        ).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("02_qbank")
        }'''
    new = '''ActivityScenario.launch<MainQBankLibraryActivity>(
            Intent(context, MainQBankLibraryActivity::class.java)
        ).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("02_qbank")
        }'''
    if old not in s:
        raise SystemExit("v8.3.626: QBank visual-truth launch block not found")
    s = s.replace(old, new, 1)
    target.write_text(s, encoding="utf-8")

def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_626_qbank_premium_overlay.py <project>")
    root = Path(sys.argv[1]).resolve()
    gradle = root / "app/build.gradle.kts"
    if not gradle.is_file():
        raise SystemExit("v8.3.626: missing build.gradle.kts")
    g = gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g:
        raise SystemExit("v8.3.626: requires v8.3.625/711")
    pkg = root / "app/src/main/java/com/localqbank/library"
    pkg.mkdir(parents=True, exist_ok=True)
    (pkg / "RovexQBankProductionLayer.kt").write_text(LAYER, encoding="utf-8")
    patch_activity(root)
    patch_visual_truth(root)
    g = g.replace(EXPECTED_VERSION, NEW_VERSION, 1).replace(EXPECTED_CODE, NEW_CODE, 1)
    gradle.write_text(g, encoding="utf-8")
    print("v8.3.626 QBank premium visual phase: APPLIED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
