package com.localqbank.library
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
class RovexPerformanceLabView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):View(context,attrs){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var accuracy=0; private var solved=0; private var wrong=0
    fun setUserStats(a:Int,s:Int,w:Int){accuracy=a.coerceIn(0,100);solved=s.coerceAtLeast(0);wrong=w.coerceAtLeast(0);invalidate()}
    fun setScores(a:Int,s:Int,w:Int){} // legacy device-score callers are intentionally ignored.
    override fun onDraw(c:Canvas){
        val d=resources.displayMetrics.density; val w=width.toFloat(); val h=height.toFloat()
        if(w<=0||h<=0)return
        paint.style=Paint.Style.FILL; paint.color=ThemeManager.panel(context); c.drawRoundRect(0f,0f,w,h,16*d,16*d,paint)
        paint.color=ThemeManager.text(context);paint.textSize=10.5f*d;paint.isFakeBoldText=true
        c.drawText("YOUR PERFORMANCE",10*d,17*d,paint);paint.isFakeBoldText=false
        paint.color=ThemeManager.muted(context);paint.textSize=8*d;c.drawText("$solved solved • $wrong wrong",10*d,30*d,paint)
        val cx=28*d;val cy=57*d;val r=20*d;paint.style=Paint.Style.STROKE;paint.strokeWidth=5*d;paint.strokeCap=Paint.Cap.ROUND
        paint.color=if(ThemeManager.isDark(context))0xFF27343D.toInt() else 0xFFE0E8EB.toInt();c.drawCircle(cx,cy,r,paint)
        paint.color=ThemeManager.accent(context);c.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,accuracy*3.6f,false,paint)
        paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER;paint.color=ThemeManager.text(context);paint.textSize=11*d;paint.isFakeBoldText=true;c.drawText("$accuracy%",cx,cy+4*d,paint)
        paint.isFakeBoldText=false;paint.textSize=7*d;paint.color=ThemeManager.muted(context);c.drawText("ACCURACY",cx,cy+30*d,paint)
        paint.textAlign=Paint.Align.LEFT;paint.textSize=8*d;c.drawText("Accuracy",50*d,51*d,paint)
        paint.color=ThemeManager.accent(context);c.drawRoundRect(50*d,56*d,w-10*d,62*d,3*d,3*d,paint)
        paint.color=if(ThemeManager.isDark(context))0xFF26343C.toInt() else 0xFFE2EAED.toInt();c.drawRoundRect(50*d,69*d,w-10*d,75*d,3*d,3*d,paint)
        paint.color=0xFFD65F5F.toInt();c.drawRoundRect(50*d,69*d,50*d+(w-60*d)*minOf(1f,wrong.toFloat()/maxOf(1,solved)),75*d,3*d,3*d,paint)
        paint.color=ThemeManager.muted(context);paint.textSize=7.5f*d;c.drawText("Wrong",50*d,84*d,paint)
        paint.textAlign=Paint.Align.LEFT
    }
}
