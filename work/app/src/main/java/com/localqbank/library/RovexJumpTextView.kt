package com.localqbank.library

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Dr.Frankenstein wordmark: a slow break-dance-style travelling hand-wave through adjacent letters. */
class RovexJumpTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TextView(context, attrs) {
    private var phase=0f
    private var animator:ValueAnimator?=null
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(AnimationPolicy.enabled(context))start()}
    override fun onDetachedFromWindow(){animator?.cancel();animator=null;paint.shader=null;super.onDetachedFromWindow()}
    private fun start(){
        animator?.cancel();animator=ValueAnimator.ofFloat(0f,1f).apply{
            duration=3200L
            repeatCount=ValueAnimator.INFINITE
            addUpdateListener{phase=it.animatedValue as Float;invalidate()}
            start()
        }
    }
    override fun onDraw(canvas:Canvas){
        val value=text?.toString().orEmpty();if(value.isEmpty())return super.onDraw(canvas)
        val p=Paint(paint);val total=p.measureText(value);val available=(width-paddingLeft-paddingRight).coerceAtLeast(0).toFloat()
        val gravityHorizontal=gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val xStart=when(gravityHorizontal){Gravity.CENTER_HORIZONTAL->paddingLeft+(available-total).coerceAtLeast(0f)/2f;Gravity.RIGHT,Gravity.END->paddingLeft+(available-total).coerceAtLeast(0f);else->paddingLeft.toFloat()}
        val baseline=paddingTop+((height-paddingTop-paddingBottom)-(p.ascent()+p.descent()))/2f
        if(RovexColorFlowTextView.flowEnabled(context)){
            val travel=maxOf(total,width.toFloat())*1.6f;val start=-travel+phase*travel
            p.shader=LinearGradient(start,0f,start+travel,0f,intArrayOf(RovexColorFlowTextView.colorOne(context),RovexColorFlowTextView.mix(RovexColorFlowTextView.colorOne(context),RovexColorFlowTextView.colorTwo(context),.5f),RovexColorFlowTextView.colorTwo(context),RovexColorFlowTextView.colorOne(context)),null,Shader.TileMode.MIRROR)
        }else{p.shader=null;p.color=ThemeManager.text(context)}

        // The active letter travels left -> right. At its centre it rises; its immediate
        // neighbours dip, creating a continuous hand-wave rather than isolated hopping.
        val cursor=phase*(value.length-1).coerceAtLeast(1).toFloat()
        var x=xStart
        value.forEachIndexed{i,ch->
            val d=abs(i-cursor)
            val envelope=when{
                d<=1f -> 1f
                d<2.25f -> (1f-(d-1f)/1.25f)
                else -> 0f
            }
            val wave=when{
                d<=1f -> cos(d*Math.PI/2.0).toFloat()
                else -> 0f
            }
            val jump=if(d<=1f) (-dp(3.8f)*wave + dp(2.7f)*(1f-wave)) else 0f
            // Small smoothing on the next-nearest character keeps the movement organic.
            val tail=if(d>1f && d<2.25f) dp(1.0f)*envelope*sin((2.25f-d)/1.25f*Math.PI).toFloat() else 0f
            canvas.drawText(ch.toString(),x,baseline+jump+tail,p)
            x+=p.measureText(ch.toString())
        }
    }
    private fun dp(v:Float)=v*resources.displayMetrics.density
}
