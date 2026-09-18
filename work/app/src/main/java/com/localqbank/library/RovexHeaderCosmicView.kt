package com.localqbank.library
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
class RovexHeaderCosmicView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):View(context,attrs){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private var t=0f
    override fun onDraw(c:Canvas){
        val w=width.toFloat();val h=height.toFloat();if(w<=0||h<=0)return
        paint.style=Paint.Style.FILL;paint.color=if(ThemeManager.isDark(context))0xFF080A16.toInt() else 0xFFF2F5F8.toInt();c.drawRect(0f,0f,w,h,paint)
        val ex=w*.70f;val ey=h*.56f;val er=minOf(w,h)*.30f
        paint.shader=RadialGradient(ex-er*.35f,ey-er*.4f,er,intArrayOf(0xFF64B7FF.toInt(),0xFF176AA5.toInt(),0xFF0B3156.toInt()),null,Shader.TileMode.CLAMP);c.drawCircle(ex,ey,er,paint);paint.shader=null
        paint.color=0xFF3F9D62.toInt();c.drawOval(ex-er*.45f,ey-er*.15f,ex+er*.05f,ey+er*.28f,paint);c.drawOval(ex+er*.02f,ey-er*.55f,ex+er*.45f,ey-er*.05f,paint)
        t+=.04f;val sx=w*.34f;val sy=h*(.35f+.04f*sin(t));paint.color=ThemeManager.text(context)
        c.drawOval(sx-16,sy-3,sx+12,sy+5,paint);paint.color=ThemeManager.accent(context);c.drawCircle(sx-4,sy+1,2.2f,paint)
        postInvalidateOnAnimation()
    }
}
