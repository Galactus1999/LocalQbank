package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

/** Small, information-dense trend view for Performance Lab. */
class ProgressSparklineView(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var values: List<Int> = emptyList()
    fun setValues(next: List<Int>) { values = next.map { it.coerceIn(0,100) }.takeLast(24); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return
        val left = 8f; val right = max(left + 1f, width - 8f); val top = 12f; val bottom = max(top + 1f, height - 12f)
        line.color = ThemeManager.accent(context)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = left + (right-left) * i / (values.lastIndex.coerceAtLeast(1)).toFloat()
            val y = bottom - (bottom-top) * v / 100f
            if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
            if (i == values.lastIndex) { dot.color = ThemeManager.accent(context); canvas.drawCircle(x,y,5f,dot) }
        }
        canvas.drawPath(path,line)
    }
}
