package com.localqbank.library

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.LinearLayout

/** Lightweight animated gradient border for the Ren entry card. */
class RovexFlowBorderLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    init { setWillNotDraw(false) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (AnimationPolicy.enabled(context)) start() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
    private fun start() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 2 || height <= 2) return
        val c1 = RovexColorFlowTextView.colorOne(context)
        val c2 = RovexColorFlowTextView.colorTwo(context)
        val travel = width.toFloat() * 1.5f
        val start = -travel + phase * travel
        borderPaint.strokeWidth = dp(1.5f)
        borderPaint.shader = LinearGradient(start, 0f, start + travel, 0f, intArrayOf(c1, c2, c1), null, Shader.TileMode.MIRROR)
        val r = dp(16f)
        canvas.drawRoundRect(dp(0.75f), dp(0.75f), width - dp(0.75f), height - dp(0.75f), r, r, borderPaint)
    }
    private fun dp(v: Float) = v * resources.displayMetrics.density
}
