package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/**
 * Compact, accurate HSV colour picker.
 * The upper field controls saturation/value; the lower spectrum controls hue.
 * This avoids the old picker bug where every selection was forced to full
 * saturation/value and the marker did not represent the saved colour accurately.
 */
class RovexColorSpectrumView(context: Context) : View(context) {
    private var syncing = false
    var color: Int = Color.RED
        set(value) {
            field = value
            if (!syncing) syncFromColor()
            invalidate()
        }
    var onColorChanged: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hueColors = intArrayOf(
        Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
        Color.BLUE, Color.MAGENTA, Color.RED
    )
    private var hue = 0f
    private var saturation = 1f
    private var value = 1f

    init {
        isClickable = true
        minimumHeight = dp(76f).toInt()
        syncFromColor()
    }

    private fun syncFromColor() {
        syncing = true
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        syncing = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(4f)
        val right = width - dp(4f)
        val top = dp(4f)
        val hueH = dp(14f)
        val gap = dp(7f)
        val bottom = height - dp(5f)
        val squareBottom = bottom - hueH - gap
        if (right <= left || squareBottom <= top) return

        // Saturation/value field for the currently selected hue.
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        paint.shader = LinearGradient(left, top, right, top, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, top, right, squareBottom, dp(7f), dp(7f), paint)
        paint.shader = LinearGradient(0f, top, 0f, squareBottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, top, right, squareBottom, dp(7f), dp(7f), paint)
        paint.shader = null

        // Hue spectrum line.
        paint.shader = LinearGradient(left, squareBottom + gap, right, squareBottom + gap, hueColors, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(left, squareBottom + gap, right, bottom, dp(7f), dp(7f), paint)
        paint.shader = null

        // Selection marker in the SV field.
        val sx = left + saturation * (right - left)
        val sy = squareBottom - value * (squareBottom - top)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(sx, sy, dp(7f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.BLACK
        canvas.drawCircle(sx, sy, dp(7f), paint)

        // Selection marker on hue line.
        val hx = left + (hue / 360f) * (right - left)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(hx - dp(5f), squareBottom + gap - dp(2f), hx + dp(5f), bottom + dp(2f), dp(4f), dp(4f), paint)
        paint.color = Color.BLACK
        paint.strokeWidth = dp(1f)
        canvas.drawRoundRect(hx - dp(5f), squareBottom + gap - dp(2f), hx + dp(5f), bottom + dp(2f), dp(4f), dp(4f), paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked !in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) return true
        val left = dp(4f)
        val right = width - dp(4f)
        val top = dp(4f)
        val hueH = dp(14f)
        val gap = dp(7f)
        val bottom = height - dp(5f)
        val squareBottom = bottom - hueH - gap
        val x = event.x.coerceIn(left, right)
        if (event.y <= squareBottom) {
            saturation = ((x - left) / (right - left)).coerceIn(0f, 1f)
            value = (1f - ((event.y - top) / (squareBottom - top))).coerceIn(0f, 1f)
        } else {
            hue = (((x - left) / (right - left)) * 360f).coerceIn(0f, 360f)
        }
        color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        onColorChanged?.invoke(color)
        invalidate()
        return true
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
