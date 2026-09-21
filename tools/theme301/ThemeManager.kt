package com.localqbank.library

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/** Rovex visual language: Light, Pastel, Mint, Sunset, Lavender and pitch-black AMOLED. */
object ThemeManager {
    const val LIGHT="light"; const val PASTEL="pastel"; const val MINT="mint"; const val SUNSET="sunset"; const val LAVENDER="lavender"; const val AMOLED="amoled"
    const val DARK="legacy_dark"; const val SEPIA="legacy_sepia"; const val OBSIDIAN_NIGHT="legacy_obsidian"; const val MIDNIGHT="legacy_midnight"; const val COSMOS="legacy_cosmos"; const val AVATAR="legacy_avatar"
    private const val PREF="ui"; private const val KEY="theme"
    private fun normalize(raw:String?):String=when(raw){PASTEL,MINT,SUNSET,LAVENDER,AMOLED,LIGHT->raw; "amoled_dark",DARK,OBSIDIAN_NIGHT,MIDNIGHT,COSMOS,AVATAR->AMOLED; SEPIA->SUNSET; else->LIGHT}
    fun get(c:Context):String{val p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);val n=normalize(p.getString(KEY,LIGHT));if(n!=p.getString(KEY,LIGHT))p.edit().putString(KEY,n).apply();return n}
    fun set(c:Context,v:String){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,normalize(v)).apply()}
    fun isDark(c:Context)=get(c)==AMOLED
    fun bg(c:Context)=when(get(c)){PASTEL->Color.rgb(241,246,255);MINT->Color.rgb(239,250,247);SUNSET->Color.rgb(255,247,239);LAVENDER->Color.rgb(246,243,255);AMOLED->Color.BLACK;else->Color.rgb(247,249,255)}
    fun panel(c:Context)=when(get(c)){PASTEL->Color.rgb(249,251,255);MINT->Color.rgb(248,255,252);SUNSET->Color.rgb(255,251,246);LAVENDER->Color.rgb(252,250,255);AMOLED->Color.rgb(4,7,12);else->Color.rgb(252,253,255)}
    fun elevated(c:Context)=when(get(c)){PASTEL,MINT,SUNSET,LAVENDER->Color.WHITE;AMOLED->Color.rgb(9,13,22);else->Color.WHITE}
    fun text(c:Context)=if(isDark(c))Color.rgb(241,247,255) else Color.rgb(18,32,72)
    fun muted(c:Context)=if(isDark(c))Color.rgb(153,177,208) else Color.rgb(77,94,125)
    fun accent(c:Context)=when(get(c)){MINT->Color.rgb(0,190,165);SUNSET->Color.rgb(255,113,50);LAVENDER->Color.rgb(112,72,255);AMOLED->Color.rgb(54,189,255);PASTEL->Color.rgb(74,91,255);else->Color.rgb(45,91,230)}
    fun aiText(c:Context)=if(isDark(c))Color.rgb(99,221,255) else Color.rgb(62,55,174)
    fun questionText(c:Context)=text(c); fun explanationText(c:Context)=text(c)
    fun optionBg(c:Context)=if(isDark(c))Color.rgb(7,13,23) else Color.rgb(246,249,255); fun optionText(c:Context)=text(c)
    fun correctBg(c:Context)=if(isDark(c))Color.rgb(5,62,48) else Color.rgb(225,250,239); fun correctText(c:Context)=if(isDark(c))Color.rgb(86,255,189) else Color.rgb(0,119,78)
    fun wrongBg(c:Context)=if(isDark(c))Color.rgb(65,14,30) else Color.rgb(255,232,238); fun wrongText(c:Context)=if(isDark(c))Color.rgb(255,110,156) else Color.rgb(172,36,78)
    fun explanationBg(c:Context)=if(isDark(c))Color.rgb(7,12,20) else Color.rgb(241,247,255); fun explanationTitle(c:Context)=if(isDark(c))Color.rgb(255,181,58) else Color.rgb(79,67,190)
    fun dialogBg(c:Context)=if(isDark(c))Color.rgb(7,10,16) else Color.WHITE; fun dialogText(c:Context)=text(c); fun dialogMuted(c:Context)=muted(c)
    fun peacockFill(c:Context)=when(get(c)){MINT->Color.rgb(211,250,242);SUNSET->Color.rgb(255,229,211);LAVENDER->Color.rgb(234,226,255);AMOLED->Color.rgb(8,40,64);PASTEL->Color.rgb(226,235,255);else->Color.rgb(224,238,255)}
    fun peacockText(c:Context)=when(get(c)){MINT->Color.rgb(0,117,100);SUNSET->Color.rgb(171,64,15);LAVENDER->Color.rgb(82,45,176);AMOLED->Color.rgb(107,224,255);else->Color.rgb(35,67,155)}
    fun statsFill(c:Context)=if(isDark(c))Color.rgb(43,20,54) else Color.rgb(247,239,255); fun statsText(c:Context)=if(isDark(c))Color.rgb(255,145,225) else Color.rgb(113,49,145); fun resultBg(c:Context)=correctBg(c)
    fun bookmarkFill(c:Context,type:String):Int=if(isDark(c))when(type.lowercase()){"important"->Color.rgb(69,48,5);"revise"->Color.rgb(6,45,74);"favorite","favourite"->Color.rgb(66,17,43);else->Color.rgb(20,28,40)}else when(type.lowercase()){"important"->Color.rgb(255,242,196);"revise"->Color.rgb(225,239,255);"favorite","favourite"->Color.rgb(255,229,239);else->Color.rgb(238,243,250)}
    fun bookmarkText(c:Context,type:String)=if(isDark(c))Color.rgb(255,205,87) else Color.rgb(50,67,100)
    fun pastelBlueFill(c:Context)=if(isDark(c))Color.rgb(7,38,64) else Color.rgb(222,241,255); fun pastelBlueText(c:Context)=if(isDark(c))Color.rgb(93,210,255) else Color.rgb(30,79,150)
    fun pastelPinkFill(c:Context)=if(isDark(c))Color.rgb(61,18,47) else Color.rgb(255,228,242); fun pastelPinkText(c:Context)=if(isDark(c))Color.rgb(255,127,202) else Color.rgb(139,48,111)
    fun pastelRedFill(c:Context)=if(isDark(c))Color.rgb(62,20,23) else Color.rgb(255,232,224); fun pastelRedText(c:Context)=if(isDark(c))Color.rgb(255,142,110) else Color.rgb(171,64,31)
    fun pastelYellowFill(c:Context)=if(isDark(c))Color.rgb(62,45,7) else Color.rgb(255,246,213); fun pastelYellowText(c:Context)=if(isDark(c))Color.rgb(255,209,84) else Color.rgb(130,91,0)
    fun pastelLavenderFill(c:Context)=if(isDark(c))Color.rgb(34,22,72) else Color.rgb(238,232,255); fun pastelLavenderText(c:Context)=if(isDark(c))Color.rgb(184,154,255) else Color.rgb(82,55,151)
    fun pastelAccentFill(c:Context,index:Int)=when(index%4){0->pastelBlueFill(c);1->pastelPinkFill(c);2->pastelYellowFill(c);else->pastelLavenderFill(c)}
    fun pastelAccentText(c:Context,index:Int)=when(index%4){0->pastelBlueText(c);1->pastelPinkText(c);2->pastelYellowText(c);else->pastelLavenderText(c)}
    fun transparentSectionDrawable(c:Context):Drawable=GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke((c.resources.displayMetrics.density).toInt().coerceAtLeast(1),Color.argb(if(isDark(c))110 else 65,Color.red(accent(c)),Color.green(accent(c)),Color.blue(accent(c))));cornerRadius=22f*c.resources.displayMetrics.density}
    fun backgroundDrawable(c:Context):Drawable=ThemeAtmosphereDrawable(c)
}