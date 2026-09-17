package com.localqbank.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** Small decorative header bird. It is purely visual and never intercepts header actions. */
class RovexHeaderBirdView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val body=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.FILL}
    private val wing=Path()
    private var started=0L
    private val cycle=9200L

    override fun onAttachedToWindow(){super.onAttachedToWindow();started=SystemClock.uptimeMillis();postInvalidateOnAnimation()}

    override fun onDraw(c:Canvas){
        val w=width.toFloat(); val h=height.toFloat(); if(w<=0f||h<=0f)return
        val t=((SystemClock.uptimeMillis()-started)%cycle).toFloat()/cycle
        val dark=ThemeManager.isDark(context)
        val bird=if(dark) Color.rgb(236,244,255) else Color.rgb(44,58,68)
        val accent=if(dark) Color.rgb(120,211,255) else Color.rgb(34,116,145)
        body.color=bird
        val x:Float; val y:Float; var fallen=false
        when {
            t<.46f->{
                val u=t/.46f
                x=w*(.08f+.78f*u)
                y=h*(.22f+.28f*sin(u*6.283f))
            }
            t<.64f->{x=w*.55f;y=h*.27f}
            t<.72f->{x=w*(.55f+.25f*((t-.64f)/.08f));y=h*(.27f+.08f*((t-.64f)/.08f))}
            t<.90f->{
                val u=(t-.72f)/.18f
                x=w*.80f; y=h*(.35f+1.15f*u*u); fallen=true
            }
            else->{x=w*.08f;y=h*.24f}
        }
        if(fallen){
            body.color=Color.argb((255*(1f-((t-.72f)/.18f))).toInt().coerceIn(0,255),bird shr 16 and 255,bird shr 8 and 255,bird and 255)
            c.save();c.rotate(75f,x,y);drawBird(c,x,y,body,accent);c.restore()
        } else drawBird(c,x,y,body,accent)
        if(t in .64f.. .69f){
            val u=(t-.64f)/.05f
            body.color=Color.argb((220*(1f-u)).toInt(),if(dark)220 else 50,if(dark)225 else 55,if(dark)235 else 60)
            c.drawCircle(w*.70f,h*.33f,dp(2.2f),body)
            body.color=accent
            c.drawCircle(w*.66f,h*.31f,dp(1.3f),body)
        }
        postInvalidateOnAnimation()
    }

    private fun drawBird(c:Canvas,x:Float,y:Float,p:Paint,accent:Int){
        val s=dp(1f)
        c.drawOval(x-9*s,y-5*s,x+8*s,y+6*s,p)
        c.drawCircle(x+8*s,y-3*s,5*s,p)
        wing.reset();wing.moveTo(x-2*s,y);wing.cubicTo(x-13*s,y-12*s,x-15*s,y-2*s,x-5*s,y+4*s);wing.close();c.drawPath(wing,p)
        p.color=accent
        val beak=Path();beak.moveTo(x+13*s,y-3*s);beak.lineTo(x+20*s,y);beak.lineTo(x+13*s,y+2*s);beak.close();c.drawPath(beak,p)
        c.drawCircle(x+9*s,y-4*s,1.1f*s,p)
        p.color=if(ThemeManager.isDark(context))Color.rgb(236,244,255) else Color.rgb(44,58,68)
        c.drawLine(x-10*s,y+6*s,x-13*s,y+9*s,p);c.drawLine(x-5*s,y+6*s,x-7*s,y+9*s,p)
    }
    private fun dp(v:Float)=v*resources.displayMetrics.density
}
