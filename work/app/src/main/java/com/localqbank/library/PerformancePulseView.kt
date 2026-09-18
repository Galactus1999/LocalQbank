package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

/** Live, low-density telemetry graph. Samples actual runtime signals and keeps only the last 30. */
class PerformancePulseView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; isFakeBoldText = true }
    private val health = ArrayDeque<Int>()
    private val ram = ArrayDeque<Int>()
    private val resilience = ArrayDeque<Int>()
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            sample(); invalidate(); postDelayed(this, 1000L)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; sample(); post(tick) }
    override fun onDetachedFromWindow() { running = false; removeCallbacks(tick); super.onDetachedFromWindow() }
    private fun add(q: ArrayDeque<Int>, value: Int) { q.addLast(value.coerceIn(0,100)); while(q.size > 30) q.removeFirst() }
    private fun sample() {
        val gov = runCatching { BenAiResourceGovernor(context).snapshot(true) }.getOrNull()
        val rh = runCatching { AppManagers.resilienceHealth.snapshot().overall }.getOrDefault(0)
        val hs = runCatching { AppManagers.adaptive.state().health }.getOrDefault(0)
        val ramScore = ((gov?.availableMemoryMb ?: 0).toFloat()/2048f*100f).toInt().coerceIn(0,100)
        add(health, hs); add(ram, ramScore); add(resilience, rh)
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (health.isEmpty()) return
        val left=18f; val right=max(left+1f,width-18f); val top=28f; val bottom=max(top+1f,height-12f)
        fun draw(values:ArrayDeque<Int>, color:Int) {
            if(values.size<2)return
            val p=Path(); val list=values.toList()
            list.forEachIndexed { i,v -> val x=left+(right-left)*i/(list.lastIndex.coerceAtLeast(1)).toFloat(); val y=bottom-(bottom-top)*v/100f; if(i==0)p.moveTo(x,y) else p.lineTo(x,y) }
            paint.color=color; c.drawPath(p,paint)
        }
        draw(health, ThemeManager.accent(context)); draw(ram, ThemeManager.peacockText(context)); draw(resilience, ThemeManager.text(context))
        label.color=ThemeManager.muted(context); c.drawText("HEALTH",left,16f,label); c.drawText("RAM",left+70f,16f,label); c.drawText("RESILIENCE",left+110f,16f,label)
    }
}
