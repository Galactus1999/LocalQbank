package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Home "Flashcards" widget. Redesigned v2: one visual language (progress ring) shared with
 * RovexPerformanceLabView instead of a separate card-stack metaphor, one hero number, no
 * sub-7sp micro text. Legibility over density.
 */
class RovexFlashcardSummaryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cards = 0; private var due = 0; private var reviews = 0
    private var animatedPct = 0f
    private var running = false; private var started = 0L

    fun setStats(cards: Int, due: Int, reviews: Int) {
        this.cards = cards.coerceAtLeast(0); this.due = due.coerceAtLeast(0); this.reviews = reviews.coerceAtLeast(0)
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; started = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); running = visibility == View.VISIBLE; if (running) postInvalidateOnAnimation() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0 || !running) return
        val d = resources.displayMetrics.density
        val dark = ThemeManager.isDark(context)
        val accent = ThemeManager.accent(context)
        val target = if (cards > 0) (reviews.toFloat() / cards * 100f).coerceIn(0f, 100f) else 0f
        animatedPct += (target - animatedPct) * 0.14f
        val pct = animatedPct.roundToInt().coerceIn(0, 100)
        val pulse = ((sin((SystemClock.uptimeMillis() - started) / 700.0) + 1) / 2).toFloat()

        // Soft sheen backdrop — subtle radial glow instead of a flat pastel fill, so the
        // widget reads as elevated glass rather than a filled rectangle.
        val panelColor = ThemeManager.elevated(context)
        p.shader = RadialGradient(w * 0.3f, h * 0.15f, w * 1.1f, intArrayOf(lighten(panelColor, if (dark) 1.18f else 1.0f), panelColor), null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        c.drawRoundRect(0f, 0f, w, h, 18 * d, 18 * d, p)
        p.shader = null

        // Ring — the single hero visual, shared language with the Performance Lab widget.
        val cx = w * 0.30f; val cy = h * 0.50f; val r = minOf(w, h) * 0.30f
        p.style = Paint.Style.STROKE; p.strokeWidth = 7 * d; p.strokeCap = Paint.Cap.ROUND
        p.color = if (dark) 0xFF2C414E.toInt() else 0xFFDCE6E9.toInt()
        c.drawCircle(cx, cy, r, p)
        p.color = accent
        c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, pct * 3.6f, false, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
        p.color = ThemeManager.text(context); p.isFakeBoldText = true; p.textSize = 15f * d
        c.drawText("$pct%", cx, cy + 5 * d, p)
        p.textAlign = Paint.Align.LEFT

        // Right column — one hero number, one label, one status pill. No third tier of text.
        val labelX = w * 0.56f
        p.color = accent; p.isFakeBoldText = true; p.textSize = 9.5f * d
        c.drawText("FLASHCARDS", labelX, h * 0.28f, p)
        p.color = ThemeManager.text(context); p.textSize = 22f * d
        c.drawText(cards.toString(), labelX, h * 0.28f + 24 * d, p)
        p.isFakeBoldText = false; p.color = ThemeManager.muted(context); p.textSize = 9f * d
        c.drawText("cards", labelX, h * 0.28f + 36 * d, p)

        // Status pill: green "up to date" or amber/red "N due" — a single readable chip
        // instead of a stacked line of caption text.
        val pillY = h - 24 * d
        val pillColor = if (due > 0) 0xFFE0A23A.toInt() else 0xFF36B77C.toInt()
        val pillLabel = if (due > 0) "$due DUE" else "UP TO DATE"
        p.isFakeBoldText = true; p.textSize = 9f * d
        val textW = p.measureText(pillLabel)
        val pillLeft = labelX; val pillRight = pillLeft + textW + 16 * d
        p.style = Paint.Style.FILL
        p.color = if (dark) argbTint(pillColor, 55) else argbTint(pillColor, 30)
        c.drawRoundRect(pillLeft, pillY, pillRight, pillY + 18 * d, 9 * d, 9 * d, p)
        p.color = pillColor
        c.drawText(pillLabel, pillLeft + 8 * d, pillY + 12.5f * d, p)

        // Live pulse dot, top-right corner — kept, but small and unobtrusive.
        p.isFakeBoldText = false
        c.drawCircle(w - 12 * d, 12 * d, (2f + 1.5f * pulse) * d, p)

        if (running) postInvalidateOnAnimation()
    }

    private fun lighten(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    private fun argbTint(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)
}
