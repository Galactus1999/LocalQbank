package com.localqbank.library

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.TextView

/**
 * Theme-aware animated wordmark. The shader travels left-to-right continuously, so the
 * Rovex identity remains visible without using a heavy animation or changing layout bounds.
 */
class RovexWaveTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextView(context, attrs) {
    private var animator: ValueAnimator? = null
    private var phase = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (AnimationPolicy.enabled(context)) startWave()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun startWave() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                applyShader()
                invalidate()
            }
            start()
        }
    }

    private fun applyShader() {
        if (width <= 0 || text.isNullOrBlank()) return
        val accent = ThemeManager.accent(context)
        val colors = if (ThemeManager.isDark(context)) {
            intArrayOf(accent, 0xFF8FE7FF.toInt(), 0xFFFFFFFF.toInt(), accent)
        } else {
            intArrayOf(accent, 0xFF36A9E1.toInt(), 0xFF7C4DFF.toInt(), accent)
        }
        val travel = width.toFloat() * 2f
        val start = -travel + phase * travel
        paint.shader = LinearGradient(
            start, 0f, start + travel, 0f,
            colors, null, Shader.TileMode.MIRROR
        )
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        applyShader()
        super.onDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyShader()
    }
}
