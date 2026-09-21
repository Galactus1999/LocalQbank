package com.localqbank.library

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** App-wide visual pass: rounded 3D surfaces, semantic typography and adaptive accents. */
object RovexThemeEngine{
    private fun dp(v:Int,x:View)=(v*x.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun surface(x:View,top:Int,bottom:Int,stroke:Int,r:Int=20)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(top,bottom)).apply{cornerRadius=dp(r,x).toFloat();setStroke(dp(1,x),stroke)}
    fun apply(a:Activity){val root=a.window.decorView.findViewById<ViewGroup>(android.R.id.content)?:return;root.background=ThemeManager.backgroundDrawable(a);walk(root,a,0)}
    private fun walk(v:View,a:Activity,depth:Int){if(v.tag=="rovexThemeSkip")return;if((v.tag as? String)?.startsWith("quiz:")==true)return;when(v){is Button->button(v,a);is ImageButton->imageButton(v,a);is EditText->edit(v,a);is ProgressBar->{v.progressTintList=ColorStateList.valueOf(ThemeManager.accent(a))};is TextView->text(v,a);is ViewGroup->if(depth==0)v.background=ThemeManager.backgroundDrawable(a)};if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i),a,depth+1)}
    private fun text(v:TextView,a:Activity){val id=runCatching{v.resources.getResourceEntryName(v.id).lowercase()}.getOrDefault("");val color=when{ id.contains("ai")||id.contains("ben")||id.contains("franken")->ThemeManager.aiText(a);id.contains("hint")||id.contains("sub")||id.contains("muted")->ThemeManager.muted(a);else->ThemeManager.text(a)};v.setTextColor(color);if(v.textSize>=16f)v.typeface=Typeface.create(v.typeface,Typeface.BOLD)}
    private fun button(v:Button,a:Activity){val ac=ThemeManager.accent(a);v.background=surface(v,ThemeManager.peacockFill(a),if(ThemeManager.isDark(a))Color.rgb(8,18,32) else Color.WHITE,Color.argb(130,Color.red(ac),Color.green(ac),Color.blue(ac)),18);v.setTextColor(ThemeManager.peacockText(a));v.stateListAnimator=null;v.elevation=dp(6,v).toFloat();v.minHeight=dp(46,v);v.isAllCaps=false;v.setPadding(dp(15,v),dp(8,v),dp(15,v),dp(8,v));v.letterSpacing=.015f}
    private fun imageButton(v:ImageButton,a:Activity){val ac=ThemeManager.accent(a);v.background=surface(v,ThemeManager.elevated(a),ThemeManager.peacockFill(a),Color.argb(120,Color.red(ac),Color.green(ac),Color.blue(ac)),18);v.elevation=dp(5,v).toFloat()}
    private fun edit(v:EditText,a:Activity){val ac=ThemeManager.accent(a);v.background=surface(v,ThemeManager.elevated(a),ThemeManager.panel(a),Color.argb(80,Color.red(ac),Color.green(ac),Color.blue(ac)),18);v.setTextColor(ThemeManager.text(a));v.setHintTextColor(ThemeManager.muted(a));v.setPadding(dp(15,v),v.paddingTop,dp(15,v),v.paddingBottom);v.elevation=dp(2,v).toFloat()}
}