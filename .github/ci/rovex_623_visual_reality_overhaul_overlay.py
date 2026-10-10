#!/usr/bin/env python3
"""Rovex v8.3.623 visual-reality overhaul.

This overlay is intentionally narrow:
- removes the 617 text-pill regression;
- makes production Home surfaces own their visuals at the actual card level;
- installs a richer, opaque, theme-aware backdrop;
- imports pinned Lottie assets into the APK;
- adds a visual contract manifest for screenshot/geometry verification.
It does not change QBank/DB/AI ownership.
"""
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
APP = ROOT / "app"
PKG = APP / "src/main/java/com/localqbank/library"
GRADLE = APP / "build.gradle.kts"
# In Adaptive CI the overlay script lives in the repository and ROOT is the
# extracted project. The repository root is passed separately when available.
REPO_ROOT = Path(__file__).resolve().parents[2] if len(Path(__file__).resolve().parents) > 2 else ROOT

if not GRADLE.is_file():
    raise SystemExit("v8.3.623: build.gradle.kts missing")
g = GRADLE.read_text(encoding="utf-8")
if 'versionCode = 708' not in g or 'versionName = "8.3.622"' not in g:
    raise SystemExit("v8.3.623: refusing non-8.3.622/708 baseline")

for name in ("RovexClinicalDayProductionLayer.kt", "RovexVisualSurfaceStyle.kt", "RovexVisualColors.kt"):
    if not (PKG / name).is_file():
        raise SystemExit(f"v8.3.623: required visual source missing: {name}")

assets = ROOT / "app/src/main/assets/rovex_motion"
assets.mkdir(parents=True, exist_ok=True)
repo_asset_dir = REPO_ROOT / "visual-assets/lottie"
if repo_asset_dir.is_dir():
    for name in ("checkmark.json", "bookmark.json", "loading.json"):
        src = repo_asset_dir / name
        if src.is_file():
            shutil.copy2(src, assets / name)

(PKG / "RovexPremiumHomeBackdropDrawable.kt").write_text(r'''package com.localqbank.library

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Production backdrop: static/opaque base + bounded decorative geometry.
 * No frame scheduling, no per-frame allocation, no shader animation.
 */
class RovexPremiumHomeBackdropDrawable(private val context: Context) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val dark get() = ThemeManager.isDark(context)

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        val a = ThemeManager.accent(context)

        val baseA = if (dark) Color.rgb(7, 13, 27) else Color.rgb(247, 250, 255)
        val baseB = if (dark) Color.rgb(13, 22, 42) else Color.rgb(238, 247, 255)
        val baseC = if (dark) Color.rgb(24, 12, 38) else Color.rgb(255, 247, 241)

        paint.shader = LinearGradient(0f, 0f, w, h, baseA, baseB, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.12f, h * 0.16f, min(w, h) * 0.62f,
            Color.argb(if (dark) 88 else 48, Color.red(a), Color.green(a), Color.blue(a)),
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.12f, h * 0.16f, min(w, h) * 0.62f, paint)

        paint.shader = RadialGradient(
            w * 0.88f, h * 0.72f, min(w, h) * 0.70f,
            if (dark) Color.argb(92, 96, 67, 214) else Color.argb(44, 225, 166, 224),
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.88f, h * 0.72f, min(w, h) * 0.70f, paint)

        paint.shader = LinearGradient(0f, h * 0.55f, w, h, baseB, baseC, Shader.TileMode.CLAMP)
        paint.alpha = if (dark) 90 else 125
        canvas.drawRect(0f, h * 0.52f, w, h, paint)
        paint.alpha = 255
        paint.shader = null

        // Fine clinical-grid geometry: intentionally sparse and static.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.resources.displayMetrics.density
        paint.color = Color.argb(if (dark) 30 else 24, Color.red(a), Color.green(a), Color.blue(a))
        val step = context.resources.displayMetrics.density * 44f
        var x = -step
        while (x < w + step) {
            canvas.drawLine(x, 0f, x + h * 0.10f, h, paint)
            x += step
        }

        // A single flowing contour anchors the lower composition.
        path.reset()
        path.moveTo(-w * 0.05f, h * 0.78f)
        path.cubicTo(w * 0.20f, h * 0.67f, w * 0.34f, h * 0.92f, w * 0.54f, h * 0.80f)
        path.cubicTo(w * 0.73f, h * 0.69f, w * 0.83f, h * 0.86f, w * 1.05f, h * 0.73f)
        paint.color = Color.argb(if (dark) 42 else 34, Color.red(a), Color.green(a), Color.blue(a))
        paint.strokeWidth = context.resources.displayMetrics.density * 1.5f
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (dark) 55 else 30, 255, 255, 255)
        val dotR = context.resources.displayMetrics.density * 1.2f
        for (i in 0..11) {
            val dx = w * (0.06f + i * 0.083f)
            val dy = h * (0.11f + ((i * 37) % 71) / 100f)
            canvas.drawCircle(dx, dy, dotR, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
''', encoding="utf-8")

(PKG / "RovexClinicalDayProductionLayer.kt").write_text(r'''package com.localqbank.library

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * Production visual ownership for Home.
 *
 * Critical rule: never decorate individual headline TextViews as cards.
 * Card ownership is applied to the real container IDs. This prevents the
 * previous "gray pill behind every headline" regression seen in screenshots.
 */
object RovexClinicalDayProductionLayer {
    private const val TAG = "ROVEX_CLINICAL_DAY_PRODUCTION_623"

    private val cardIds = intArrayOf(
        R.id.renCard,
        R.id.todaySolvedCard,
        R.id.studyMenuCard,
        R.id.flashcardCard,
        R.id.performanceLabCard,
        R.id.homeSearchCard
    )

    fun apply(activity: MainActivity) {
        val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot) ?: return
        if (root.getTag() == TAG) return
        root.setTag(TAG)

        root.background = RovexPremiumHomeBackdropDrawable(activity)

        cardIds.forEachIndexed { index, id ->
            val card = activity.findViewById<View>(id) ?: return@forEachIndexed
            styleCard(activity, card, index)
            bindPress(card)
        }

        normalizeTextTree(activity, root)
    }

    private fun styleCard(activity: MainActivity, card: View, index: Int) {
        val dark = ThemeManager.isDark(activity)
        val accent = ThemeManager.accent(activity)
        val fill = when (index % 4) {
            0 -> if (dark) Color.rgb(17, 29, 48) else Color.rgb(249, 253, 255)
            1 -> if (dark) Color.rgb(28, 24, 48) else Color.rgb(255, 249, 242)
            2 -> if (dark) Color.rgb(16, 35, 43) else Color.rgb(244, 253, 250)
            else -> if (dark) Color.rgb(29, 25, 48) else Color.rgb(249, 246, 255)
        }
        val border = Color.argb(
            if (dark) 135 else 105,
            Color.red(accent), Color.green(accent), Color.blue(accent)
        )
        card.background = GradientDrawable().apply {
            cornerRadius = activity.resources.displayMetrics.density * 24f
            setColor(fill)
            setStroke(activity.resources.displayMetrics.density.toInt().coerceAtLeast(1), border)
        }
        card.elevation = activity.resources.displayMetrics.density * if (dark) 1.5f else 3f
        card.clipToOutline = true
        if (card is ViewGroup) {
            card.setPadding(
                dp(activity, 18), dp(activity, 16),
                dp(activity, 18), dp(activity, 16)
            )
        }
    }

    private fun normalizeTextTree(activity: MainActivity, parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) normalizeTextTree(activity, child)
            val tv = child as? TextView ?: continue

            // The previous production overlay incorrectly put a surface behind
            // matching headline text. Remove that visual pollution everywhere.
            if (child !is Button && child !is EditText && tv.background != parent.background) {
                val isInsideRealCard = isCardDescendant(tv)
                if (isInsideRealCard) tv.background = null
            }

            tv.includeFontPadding = false
            if (tv.maxLines == Int.MAX_VALUE || tv.maxLines <= 0) tv.maxLines = 2
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            tv.setTextColor(ThemeManager.text(activity))

            val text = tv.text?.toString()?.trim().orEmpty()
            if (text.length > 42 && tv.textSize / activity.resources.displayMetrics.scaledDensity < 17f) {
                tv.maxLines = 2
            }
        }
    }

    private fun isCardDescendant(view: View): Boolean {
        var p = view.parent
        while (p is ViewGroup) {
            if (cardIds.contains(p.id)) return true
            p = p.parent
        }
        return false
    }

    private fun bindPress(view: View) {
        if (!view.isClickable && !view.hasOnClickListeners()) return
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                }
            }
            false
        }
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
''', encoding="utf-8")

# Install the corrected layer after 622, before app startup.
resilience = PKG / "ResilienceManager.kt"
if not resilience.is_file():
    raise SystemExit("v8.3.623: ResilienceManager.kt missing")
r = resilience.read_text(encoding="utf-8")
if "RovexClinicalDayProductionLayer.apply(a)" not in r:
    marker = "a.window.decorView.post { if (a is MainActivity) RovexClinicalDayProductionLayer.apply(a) }"
    if marker in r:
        r = r.replace(marker, marker, 1)
    else:
        anchor = "override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {"
        if anchor not in r:
            anchor = "override fun onActivityCreated(a: Activity, b: Bundle?) {"
        if anchor not in r:
            raise SystemExit("v8.3.623: no Activity-created hook")
        r = r.replace(anchor, anchor + "\n                a.window.decorView.post { if (a is MainActivity) RovexClinicalDayProductionLayer.apply(a) }", 1)
        resilience.write_text(r, encoding="utf-8")

# Visual contract consumed by CI-side screenshot analysis.
(APP / "visual-contract.json").write_text(r'''{
  "contract": "rovex-production-visual-623",
  "screens": ["home-light", "home-dark", "qbank-light", "qbank-dark", "quiz", "flashcards", "ben"],
  "home": {
    "realCardIds": ["renCard", "todaySolvedCard", "studyMenuCard", "flashcardCard", "performanceLabCard", "homeSearchCard"],
    "forbidden": ["headline-background-pill", "unbounded-text-overflow", "bottom-nav-content-collision"],
    "backdrop": "RovexPremiumHomeBackdropDrawable"
  },
  "motion": {
    "assets": ["rovex_motion/checkmark.json", "rovex_motion/bookmark.json", "rovex_motion/loading.json"],
    "source": "useAnimations/react-useanimations",
    "license": "MIT"
  },
  "visualTruth": {
    "capture": true,
    "geometryAudit": true,
    "screenshotArtifact": true
  }
}
''', encoding="utf-8")

g = g.replace('versionCode = 708', 'versionCode = 709', 1)
g = g.replace('versionName = "8.3.622"', 'versionName = "8.3.623"', 1)
GRADLE.write_text(g, encoding="utf-8")
print("v8.3.623 visual reality overhaul applied")
