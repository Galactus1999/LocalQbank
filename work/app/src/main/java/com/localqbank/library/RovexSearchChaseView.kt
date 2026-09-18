package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** Lightweight cinematic sci-fi header animation. The ship lives ABOVE the search bar. */
class RovexSearchChaseView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private var started = 0L
    private var running = false
    private val cycle = 9000L

    override fun onAttachedToWindow() { super.onAttachedToWindow(); started = SystemClock.uptimeMillis(); running = true; postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); running = visibility == View.VISIBLE; if (running) postInvalidateOnAnimation() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f || !running) return
        val d = resources.displayMetrics.density
        val t = ((SystemClock.uptimeMillis() - started) % cycle).toFloat() / cycle
        val x = w * (0.18f + 0.70f * t)
        val y = h * (0.38f + 0.045f * sin(t * Math.PI * 4.0).toFloat())
        c.save(); c.translate(x, y); c.scale(d, d)
        drawShip(c)
        c.restore()
        postInvalidateOnAnimation()
    }

    private fun drawShip(c: Canvas) {
        // Original generic sci-fi silhouette; deliberately not a replica of a named franchise craft.
        val glow = 0x665CB8FF
        stroke.color = glow; stroke.strokeWidth = 3f
        c.drawLine(-62f, 4f, -18f, 4f, stroke)
        c.drawLine(-58f, 9f, -20f, 9f, stroke)
        fill.color = if (ThemeManager.isDark(context)) 0xFFB8C5D3.toInt() else 0xFF3F5568.toInt()
        val hull = Path().apply { moveTo(-32f, -2f); lineTo(42f, -1f); lineTo(62f, 5f); lineTo(28f, 9f); lineTo(-38f, 5f); close() }
        c.drawPath(hull, fill)
        fill.color = if (ThemeManager.isDark(context)) 0xFF53667B.toInt() else 0xFF263746.toInt()
        c.drawPath(Path().apply { moveTo(-8f,-8f); lineTo(25f,-4f); lineTo(38f,1f); lineTo(-12f,1f); close() }, fill)
        fill.color = 0xFF74C9FF.toInt()
        c.drawCircle(-37f, 3f, 4.2f, fill); c.drawCircle(-48f, 3f, 2.5f, fill)
        fill.color = 0xFF9AE6FF.toInt(); c.drawCircle(-37f,3f,1.7f,fill)
        stroke.color = if (ThemeManager.isDark(context)) 0xFF8298AA.toInt() else 0xFF6B7F8F.toInt(); stroke.strokeWidth = 1.3f
        c.drawLine(-22f,3f,32f,3f,stroke); c.drawLine(5f,-2f,18f,7f,stroke)
    }
}
