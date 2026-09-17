package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Theme-adaptive Rovex wordmark with deterministic bundled cursive handwriting reveal. */
class RovexHeaderWordmarkView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{textAlign=Paint.Align.CENTER;isSubpixelText=true}
    private var reveal=0f
    private var writingAnimator:ValueAnimator?=null
    private var attached=false
    private val cursive:Typeface by lazy{runCatching{Typeface.createFromAsset(context.assets,"rovex_lobster_two.otf")}.getOrElse{Typeface.create("cursive",Typeface.NORMAL)}}
    init{contentDescription="Rovex";setWillNotDraw(false)}
    fun refreshTheme(){invalidate()}
    override fun onAttachedToWindow(){super.onAttachedToWindow();attached=true;if(!AnimationPolicy.enabled(context)){reveal=1f;invalidate();return};writingAnimator?.cancel();writingAnimator=ValueAnimator.ofFloat(0f,1f).apply{duration=1050L;interpolator=DecelerateInterpolator();addUpdateListener{reveal=it.animatedValue as Float;invalidate()};start()}}
    override fun onDetachedFromWindow(){attached=false;writingAnimator?.cancel();writingAnimator=null;super.onDetachedFromWindow()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);if(width<=0||height<=0)return
        val cx=width*.5f;val cy=height*.53f;val size=(height*.72f).coerceAtLeast(22f)
        textPaint.textSize=size;textPaint.typeface=cursive
        val accent=ThemeManager.accent(context);val themeText=ThemeManager.text(context)
        val colors=if(ThemeManager.isDark(context))intArrayOf(accent,themeText,accent) else intArrayOf(accent,Color.rgb(75,116,145),accent)
        textPaint.shader=LinearGradient(0f,0f,width.toFloat(),0f,colors,null,Shader.TileMode.CLAMP)
        textPaint.setShadowLayer(7f,0f,0f,Color.argb(75,Color.red(accent),Color.green(accent),Color.blue(accent)))
        val baseline=cy-(textPaint.ascent()+textPaint.descent())*.5f;val text="Rovex";val tw=textPaint.measureText(text);val left=cx-tw*.56f
        canvas.save();canvas.clipRect(left,0f,left+tw*1.12f*reveal,height.toFloat());canvas.drawText(text,cx,baseline,textPaint);canvas.restore()
        textPaint.clearShadowLayer();textPaint.shader=null
    }
}
