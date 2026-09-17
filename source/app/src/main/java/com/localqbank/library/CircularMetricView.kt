package com.localqbank.library

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/** Compact, theme-aware circular metric used by Performance Lab and other dashboards. */
class CircularMetricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bounds = RectF()
    private var target = 0
    private var animated = 0f
    private var label = ""
    private var detail = ""
    private var animator: ValueAnimator? = null
    private var live = false

    init {
        isClickable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun setMetric(value: Int, label: String, detail: String = "") {
        target = value.coerceIn(0, 100)
        this.label = label
        this.detail = detail
        animateTo(target)
    }

    fun metricValue(): Int = target
    fun metricLabel(): String = label
    fun metricDetail(): String = detail

    private fun animateTo(value: Int) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(animated, value.toFloat()).apply {
            duration = 520L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animated = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        live = true
        post(liveTick)
    }

    override fun onDetachedFromWindow() {
        live = false
        animator?.cancel()
        removeCallbacks(liveTick)
        super.onDetachedFromWindow()
    }

    private val liveTick = object : Runnable {
        override fun run() {
            if (!live) return
            // Subtle breathing animation makes the lab feel alive without altering data.
            invalidate()
            postDelayed(this, 900L)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * .37f
        val stroke = size * .075f
        track.style = Paint.Style.STROKE
        track.strokeWidth = stroke
        track.strokeCap = Paint.Cap.ROUND
        track.color = if (ThemeManager.isDark(context)) 0x334D6075 else 0x224C5966
        arc.style = Paint.Style.STROKE
        arc.strokeWidth = stroke
        arc.strokeCap = Paint.Cap.ROUND
        arc.color = ThemeManager.accent(context)
        bounds.set(cx-radius, cy-radius, cx+radius, cy+radius)
        canvas.drawArc(bounds, -90f, 360f, false, track)
        canvas.drawArc(bounds, -90f, 360f * (animated / 100f), false, arc)

        valuePaint.color = ThemeManager.text(context)
        valuePaint.textSize = size * .19f
        canvas.drawText("${target}%", cx, cy + valuePaint.textSize * .34f, valuePaint)
        labelPaint.color = ThemeManager.muted(context)
        labelPaint.textSize = size * .085f
        canvas.drawText(label.take(18), cx, cy + valuePaint.textSize * .34f + labelPaint.textSize * 1.55f, labelPaint)

    }
}
