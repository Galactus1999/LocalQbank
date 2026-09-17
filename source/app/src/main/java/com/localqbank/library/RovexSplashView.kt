package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Launcher-only flowing particle field with AMOLED-black space, stars and a cool cursive Rovex reveal. */
class RovexSplashView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    companion object { const val DURATION_MS = 2600L }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isSubpixelText = true }
    private val path = Path()
    private var startTime = 0L
    private var running = false
    private var completionSent = false
    private var onFinished: (() -> Unit)? = null
    private val cursive: Typeface by lazy {
        runCatching { Typeface.createFromAsset(context.assets, "rovex_lobster_two.otf") }
            .getOrElse { Typeface.create("cursive", Typeface.NORMAL) }
    }
    private val ink = intArrayOf(
        Color.rgb(40,198,255), Color.rgb(91,112,255), Color.rgb(174,78,255),
        Color.rgb(255,75,158), Color.rgb(55,236,190), Color.rgb(98,220,255)
    )

    fun setOnFinished(listener: () -> Unit) { onFinished = listener }
    fun start() { if (running || completionSent) return; startTime=SystemClock.uptimeMillis(); running=true; invalidate() }
    fun skipToEnd() { if(completionSent)return; running=false; completionSent=true; onFinished?.invoke() }
    override fun onAttachedToWindow(){super.onAttachedToWindow();start()}
    override fun onDetachedFromWindow(){running=false;onFinished=null;super.onDetachedFromWindow()}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat(); val h=height.toFloat(); if(w<=0f||h<=0f)return
        val cx=w*.5f; val cy=h*.5f; val maxR=hypot(w.toDouble(),h.toDouble()).toFloat()*.74f
        val elapsed=if(running)(SystemClock.uptimeMillis()-startTime).coerceAtLeast(0L) else DURATION_MS
        val t=(elapsed.toFloat()/DURATION_MS).coerceIn(0f,1f)
        val ease=1f-(1f-t)*(1f-t)

        // True AMOLED black. The centre has no coloured/golden wash.
        canvas.drawColor(Color.BLACK)

        // Sparse stars: deterministic positions + gentle twinkle, never competing with the waves.
        for(i in 0 until 105){
            val sx=((i*37)%101)/100f
            val sy=((i*61+17)%101)/100f
            val twinkle=(.55f+.45f*sin(t*6.2f+i*1.71f)).coerceIn(.08f,1f)
            val alpha=(35f+125f*twinkle*(.65f+.35f*(1f-ease))).toInt().coerceIn(18,170)
            starPaint.color=Color.argb(alpha,220,232,255)
            val r=.45f+(i%4)*.28f
            canvas.drawCircle(sx*w,sy*h,r,starPaint)
        }

        for(i in 0 until 8){
            val phase=((ease*1.05f+i*.11f)%1f)
            val r=maxR*(.025f+phase*1.02f)
            wavePaint.strokeWidth=1.25f+(1f-phase)*4.8f
            wavePaint.color=Color.argb(((1f-phase)*70f).toInt(),2,13,24)
            canvas.drawCircle(cx,cy,r,wavePaint)
        }

        for(ribbon in 0 until 5){
            val colour=ink[(ribbon+1)%ink.size]
            path.reset()
            val baseAngle=-.55f+ribbon*.70f
            val points=70
            for(p in 0 until points){
                val u=p.toFloat()/(points-1)
                val radius=maxR*(.035f+u*(.98f*ease))
                val angle=baseAngle+.48f*sin(u*7f+ease*5.2f+ribbon)
                val x=(cx+cos(angle)*radius).toFloat()
                val y=(cy+sin(angle)*radius*(.70f+ribbon*.035f)).toFloat()
                if(p==0)path.moveTo(x,y) else path.lineTo(x,y)
            }
            wavePaint.color=Color.argb(120,Color.red(colour),Color.green(colour),Color.blue(colour))
            wavePaint.strokeWidth=1.7f+1.5f*(1f-ease)
            canvas.drawPath(path,wavePaint)
            for(p in 0 until 58){
                val u=p/57f
                val radius=maxR*(.04f+u*(.98f*ease))
                val angle=baseAngle+.48f*sin(u*7f+ease*5.2f+ribbon)
                val x=(cx+cos(angle)*radius).toFloat(); val y=(cy+sin(angle)*radius*(.70f+ribbon*.035f)).toFloat()
                val a=((1f-u*.45f)*175f).toInt().coerceIn(20,175)
                particlePaint.color=Color.argb(a,Color.red(colour),Color.green(colour),Color.blue(colour))
                canvas.drawCircle(x,y,1.1f+3.9f*u*ease,particlePaint)
            }
        }
        for(i in 0 until 80){
            val phase=((ease*(.70f+(i%7)*.035f)+i*.031f)%1f)
            val angle=i*2.399963f+.25f*sin(ease*4f+i)
            val radius=maxR*(.08f+phase*.90f)
            val x=(cx+cos(angle)*radius).toFloat(); val y=(cy+sin(angle)*radius).toFloat()
            val alpha=((1f-phase)*155f).toInt().coerceIn(0,155)
            val c=ink[i%ink.size]
            particlePaint.color=Color.argb(alpha,Color.red(c),Color.green(c),Color.blue(c))
            canvas.drawCircle(x,y,1.1f+(i%4)*.7f,particlePaint)
        }

        drawWordmark(canvas,cx,cy,minOf(w,h),t)
        if(running){
            if(elapsed>=DURATION_MS){running=false;if(!completionSent){completionSent=true;post{onFinished?.invoke()}}}
            else postInvalidateOnAnimation()
        }
    }

    private fun drawWordmark(canvas:Canvas,cx:Float,cy:Float,minDim:Float,t:Float){
        val size=(minDim*.155f).coerceIn(54f,118f)
        textPaint.textSize=size; textPaint.typeface=cursive
        // The wordmark now flows through the same multicolour spectrum as the waves,
        // avoiding the old fixed blue tint while remaining legible on the AMOLED field.
        val shift=(t*1.7f)%1f
        val colours=ink.copyOf()
        val stops=FloatArray(colours.size){i->(i.toFloat()/(colours.size-1)+shift)%1f}
        val order=colours.indices.sortedBy{stops[it]}
        val orderedColors=order.map{colours[it]}.toIntArray()
        val orderedStops=FloatArray(order.size){it->it.toFloat()/(order.size-1)}
        textPaint.shader=LinearGradient(cx-size*2f,cy,cx+size*2f,cy,orderedColors,orderedStops,Shader.TileMode.MIRROR)
        textPaint.setShadowLayer(7f,0f,0f,Color.argb(65,90,210,255))
        val baseline=cy-(textPaint.ascent()+textPaint.descent())*.5f
        val text="Rovex"; val tw=textPaint.measureText(text); val reveal=(t/.78f).coerceIn(0f,1f)
        canvas.save(); canvas.clipRect(cx-tw*.62f,0f,cx-tw*.62f+tw*1.24f*reveal,height.toFloat())
        canvas.drawText(text,cx,baseline,textPaint); canvas.restore()
        textPaint.clearShadowLayer();textPaint.shader=null
    }
}
