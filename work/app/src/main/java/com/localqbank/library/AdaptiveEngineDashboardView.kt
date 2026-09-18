package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Presentation-only Adaptive Engine dashboard. No policy, persistence, or engine ownership.
 * Uses compact KPI cards, a live health bar matrix and a short rolling trend line so the
 * long Adaptive Engine settings page reads as a control-room dashboard rather than a log.
 */
class AdaptiveEngineDashboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private var adaptive: AdaptiveEngineManager.State? = null
    private var telemetry: BenNeuralTelemetry.Snapshot = BenNeuralTelemetry.Snapshot()
    private var ramMb = 0
    private var thermal = "—"
    private val healthHistory = ArrayDeque<Int>()
    private val confidenceHistory = ArrayDeque<Int>()

    fun update(state: AdaptiveEngineManager.State, telemetry: BenNeuralTelemetry.Snapshot, ramText: String, thermal: String) {
        adaptive = state
        this.telemetry = telemetry
        ramMb = ramText.toIntOrNull() ?: 0
        this.thermal = thermal
        push(healthHistory, state.health)
        push(confidenceHistory, telemetry.lastConfidence)
        invalidate()
    }

    private fun push(history: ArrayDeque<Int>, value: Int) {
        history.addLast(value.coerceIn(0, 100))
        while (history.size > 18) history.removeFirst()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (330f * density).toInt().coerceAtLeast(suggestedMinimumHeight))
    }

    override fun onDraw(canvas: Canvas) {
        val state = adaptive ?: return
        val dark = ThemeManager.isDark(context)
        val bg = if (ThemeManager.get(context) == ThemeManager.AMOLED) android.graphics.Color.BLACK else ThemeManager.elevated(context)
        val fg = ThemeManager.text(context)
        val muted = ThemeManager.muted(context)
        val accent = ThemeManager.accent(context)
        val panel = if (dark) android.graphics.Color.rgb(20, 28, 37) else android.graphics.Color.rgb(248, 249, 249)
        val grid = if (dark) android.graphics.Color.rgb(55, 68, 82) else android.graphics.Color.rgb(220, 226, 230)
        fill.color = bg
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 20f * density, 20f * density, fill)

        val pad = 14f * density
        text.textAlign = Paint.Align.LEFT
        text.color = fg
        text.textSize = 13f * density
        canvas.drawText("ADAPTIVE CONTROL ROOM", pad, 20f * density, text)
        text.color = muted
        text.textSize = 8.5f * density
        canvas.drawText("Live policy, Ben runtime and device guardrails", pad, 34f * density, text)

        val cardTop = 45f * density
        val gap = 7f * density
        val cardW = (width - pad * 2 - gap * 3) / 4f
        val cards = listOf(
            Triple("${state.health}%", "HEALTH", state.health),
            Triple("${telemetry.lastConfidence}%", "CONFIDENCE", telemetry.lastConfidence),
            Triple("${telemetry.lastElapsedMs}ms", "LAST LATENCY", (100 - min(100, telemetry.lastElapsedMs.toInt() / 20))),
            Triple("${telemetry.lastEvidenceCount}", "EVIDENCE", min(100, telemetry.lastEvidenceCount * 10))
        )
        cards.forEachIndexed { i, c ->
            val l = pad + i * (cardW + gap)
            fill.color = panel
            canvas.drawRoundRect(RectF(l, cardTop, l + cardW, cardTop + 48f * density), 11f * density, 11f * density, fill)
            text.color = if (c.third >= 70) accent else fg
            text.textSize = 15f * density
            canvas.drawText(c.first, l + 8f * density, cardTop + 20f * density, text)
            text.color = muted
            text.textSize = 7f * density
            canvas.drawText(c.second, l + 8f * density, cardTop + 36f * density, text)
        }

        val chartTop = 105f * density
        val chartBottom = 206f * density
        val split = width * .53f
        drawBars(canvas, pad, chartTop, split - pad, chartBottom, grid, accent, fg, muted, state)
        drawTrend(canvas, split + 8f * density, chartTop, width - pad, chartBottom, grid, accent, fg, muted)

        val infoTop = 218f * density
        val rows = listOf(
            "Model" to telemetry.activeModel.ifBlank { "Deterministic Ben" },
            "Stage" to telemetry.stage.name.replace('_', ' '),
            "Device" to state.systemModel.replaceFirstChar { it.uppercase() },
            "Resources" to "RAM ${ramMb}MB  •  thermal $thermal",
            "Policy" to if (state.safeMode) "Protective mode" else "Normal adaptive operation"
        )
        text.textSize = 8f * density
        rows.forEachIndexed { i, pair ->
            val y = infoTop + i * 19f * density
            stroke.color = grid
            canvas.drawLine(pad, y + 7f * density, width - pad, y + 7f * density, stroke)
            text.color = muted
            canvas.drawText(pair.first.uppercase(), pad, y, text)
            text.color = fg
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(pair.second.take(54), width - pad, y, text)
            text.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawBars(canvas: Canvas, l: Float, top: Float, r: Float, bottom: Float, grid: Int, accent: Int, fg: Int, muted: Int, state: AdaptiveEngineManager.State) {
        text.color = fg; text.textSize = 8f * density; text.textAlign = Paint.Align.LEFT
        canvas.drawText("ENGINE SIGNALS", l, top - 5f * density, text)
        val vals = intArrayOf(state.learningScore, state.confidence, state.health, telemetry.lastConfidence, telemetry.lastEvidenceCount * 10)
        val labels = arrayOf("LEARN", "CONF", "HEALTH", "BEN", "EVID")
        val w = (r - l - 4f * density * 4) / 5f
        vals.forEachIndexed { i, v ->
            val x = l + i * (w + 4f * density)
            stroke.color = grid
            canvas.drawRoundRect(RectF(x, top + 7f * density, x + w, bottom), 6f * density, 6f * density, stroke)
            val h = (bottom - top - 7f * density) * (v.coerceIn(0, 100) / 100f)
            fill.color = if (v >= 70) accent else grid
            canvas.drawRoundRect(RectF(x + 2f * density, bottom - h, x + w - 2f * density, bottom - 2f * density), 5f * density, 5f * density, fill)
            text.color = muted; text.textSize = 6.5f * density; text.textAlign = Paint.Align.CENTER
            canvas.drawText(labels[i], x + w / 2f, bottom + 11f * density, text)
        }
        text.textAlign = Paint.Align.LEFT
    }

    private fun drawTrend(canvas: Canvas, l: Float, top: Float, r: Float, bottom: Float, grid: Int, accent: Int, fg: Int, muted: Int) {
        text.color = fg; text.textSize = 8f * density
        canvas.drawText("HEALTH / CONFIDENCE TREND", l, top - 5f * density, text)
        stroke.color = grid
        canvas.drawRoundRect(RectF(l, top + 7f * density, r, bottom), 7f * density, 7f * density, stroke)
        val count = max(1, max(healthHistory.size, confidenceHistory.size))
        fun drawSeries(values: ArrayDeque<Int>, offset: Float) {
            if (values.isEmpty()) return
            val path = android.graphics.Path()
            values.forEachIndexed { i, v ->
                val x = l + 6f * density + (r - l - 12f * density) * (i.toFloat() / max(1, count - 1))
                val y = bottom - 5f * density - (bottom - top - 17f * density) * (v / 100f)
                if (i == 0) path.moveTo(x, y + offset) else path.lineTo(x, y + offset)
            }
            stroke.color = if (offset == 0f) accent else fg
            stroke.strokeWidth = 2f * density
            canvas.drawPath(path, stroke)
        }
        drawSeries(healthHistory, 0f)
        drawSeries(confidenceHistory, 1.5f * density)
        text.color = muted; text.textSize = 6.5f * density
        canvas.drawText("H", l + 7f * density, bottom + 11f * density, text)
        canvas.drawText("C", l + 21f * density, bottom + 11f * density, text)
    }
}
