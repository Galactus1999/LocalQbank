#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1]).resolve()
APP = ROOT / "app"
PKG = APP / "src/main/java/com/localqbank/library"
clinical_path = PKG / "RovexClinicalDayHomeVisuals.kt"
main_path = PKG / "MainActivity.kt"
resilience_path = PKG / "ResilienceManager.kt"
gradle_path = APP / "build.gradle.kts"

for p in (clinical_path, main_path, resilience_path, gradle_path):
    if not p.is_file():
        raise SystemExit(f"v8.3.617: missing expected production file: {p}")

g = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 702' not in g or 'versionName = "8.3.616"' not in g:
    raise SystemExit("v8.3.617: refusing to patch a non-8.3.616 baseline")

runtime_path = PKG / "RovexClinicalDayProductionLayer.kt"
if runtime_path.exists():
    raise SystemExit("v8.3.617: production layer already present; refusing duplicate overlay")

runtime_path.write_text(r'''package com.localqbank.library

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Production Clinical Day layer.
 * Decorates the real Home hierarchy without replacing navigation, listeners,
 * study state, data ownership, or the existing Home screen.
 */
object RovexClinicalDayProductionLayer {
    private const val TAG = "ROVEX_CLINICAL_DAY_PRODUCTION_617"

    fun apply(activity: MainActivity) {
        val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot) ?: return
        if (root.getTag() == TAG) return
        root.setTag(TAG)

        if (!ThemeManager.isDark(activity)) {
            root.background = RovexClinicalDayHomeBackgroundDrawable(activity)
        }
        styleTree(activity, root)
    }

    private fun styleTree(activity: MainActivity, parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) styleTree(activity, child)
            styleView(activity, child)
        }
    }

    private fun styleView(activity: MainActivity, view: View) {
        if (view.visibility != View.VISIBLE) return
        if (view.isClickable || view.hasOnClickListeners()) {
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70L).start()
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                }
                false
            }
        }

        val textView = view as? TextView ?: return
        val text = textView.text?.toString()?.trim()?.lowercase().orEmpty()
        when {
            text.contains("search") ->
                applySurface(textView, activity, 22f, 94)
            text.contains("qbank") || text.contains("flashcard") ||
                text.contains("performance") || text.contains("frankenstein") ||
                text.contains("ren") ->
                applySurface(textView, activity, 20f, 105)
        }
    }

    private fun applySurface(view: View, activity: MainActivity, radiusDp: Float, alpha: Int) {
        val density = activity.resources.displayMetrics.density
        val accent = ThemeManager.accent(activity)
        view.background = GradientDrawable().apply {
            cornerRadius = radiusDp * density
            setColor(Color.argb(alpha, 255, 255, 255))
            setStroke(
                density.toInt().coerceAtLeast(1),
                Color.argb(105, Color.red(accent), Color.green(accent), Color.blue(accent))
            )
        }
    }
}
''', encoding="utf-8")

r = resilience_path.read_text(encoding="utf-8")
if "RovexClinicalDayProductionLayer.apply(a)" not in r:
    marker = "override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {"
    if marker not in r:
        marker = "override fun onActivityCreated(a: Activity, b: Bundle?) {"
    if marker not in r:
        raise SystemExit("v8.3.617: ResilienceManager onActivityCreated anchor not found")
    replacement = marker + "\n                a.window.decorView.post { if (a is MainActivity) RovexClinicalDayProductionLayer.apply(a) }"
    r = r.replace(marker, replacement, 1)
    resilience_path.write_text(r, encoding="utf-8")

g = g.replace("versionCode = 702", "versionCode = 703", 1)
g = g.replace('versionName = "8.3.616"', 'versionName = "8.3.617"', 1)
gradle_path.write_text(g, encoding="utf-8")
print("v8.3.617 Clinical Day production layer applied")
