package com.localqbank.library

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.TextView

/** Configurable two-colour flowing text with a theme-safe solid fallback when flow is disabled. */
class RovexColorFlowTextView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):TextView(context,attrs){
    private var animator:ValueAnimator?=null
    private var phase=0f
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(AnimationPolicy.enabled(context))startFlow()else{paint.shader=null;setTextColor(ThemeManager.text(context))}}
    override fun onDetachedFromWindow(){animator?.cancel();animator=null;paint.shader=null;super.onDetachedFromWindow()}
    private fun startFlow(){
        animator?.cancel();animator=ValueAnimator.ofFloat(0f,1f).apply{duration=3000L;repeatCount=ValueAnimator.INFINITE;addUpdateListener{phase=it.animatedValue as Float;applyFlow();invalidate()};start()}
    }
    private fun applyFlow(){
        if(width<=0||text.isNullOrBlank())return
        if(!flowEnabled(context)){
            paint.shader=null
            // Critical: XML colours are often light-theme values; use the active theme text colour.
            val safeColor = ThemeManager.text(context)
            if (currentTextColor != safeColor) setTextColor(safeColor)
            return
        }
        val c1=colorOne(context);val c2=colorTwo(context);val travel=width.toFloat()*1.6f;val start=-travel+phase*travel
        paint.shader=LinearGradient(start,0f,start+travel,0f,intArrayOf(c1,mix(c1,c2,.5f),c2,c1),null,Shader.TileMode.MIRROR)
    }
    override fun onDraw(canvas:android.graphics.Canvas){applyFlow();super.onDraw(canvas)}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);applyFlow()}
    companion object{
        private const val PREFS="ui";private const val FLOW="flow_text_enabled";private const val C1="flow_text_color_1";private const val C2="flow_text_color_2"
        fun flowEnabled(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(FLOW,true)
        fun colorOne(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(C1,Color.rgb(255,59,48))
        fun colorTwo(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(C2,Color.rgb(255,212,59))
        fun mix(a:Int,b:Int,t:Float)=Color.rgb((Color.red(a)+(Color.red(b)-Color.red(a))*t).toInt(),(Color.green(a)+(Color.green(b)-Color.green(a))*t).toInt(),(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t).toInt())
    }
}
