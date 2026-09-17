package com.localqbank.library

import android.content.Context
import android.graphics.Color

object ThemeManager {
    const val LIGHT="light"; const val DARK="dark"; const val SEPIA="sepia"
    const val AMOLED="amoled"; const val MIDNIGHT="midnight"; const val COSMOS="cosmos"; const val AVATAR="avatar"
    fun get(c:Context)=c.getSharedPreferences("ui",Context.MODE_PRIVATE).getString("theme",LIGHT)?:LIGHT
    fun set(c:Context,v:String){c.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().putString("theme",v).apply()}
    fun isDark(c:Context)=get(c) in setOf(DARK,AMOLED,MIDNIGHT,COSMOS,AVATAR)
    fun bg(c:Context)=when(get(c)){AMOLED->Color.BLACK;COSMOS->Color.BLACK;AVATAR->Color.rgb(3,7,25);MIDNIGHT->Color.rgb(7,14,28);DARK->Color.rgb(9,13,18);SEPIA->Color.rgb(246,239,218);else->Color.rgb(247,249,246)}
    fun panel(c:Context)=when(get(c)){AMOLED->Color.BLACK;COSMOS->Color.rgb(5,8,20);AVATAR->Color.rgb(6,22,38);MIDNIGHT->Color.rgb(13,24,43);DARK->Color.rgb(17,23,30);SEPIA->Color.rgb(251,245,227);else->Color.rgb(251,253,250)}
    fun elevated(c:Context)=when(get(c)){AMOLED->Color.BLACK;COSMOS->Color.rgb(10,14,30);AVATAR->Color.rgb(9,34,51);MIDNIGHT->Color.rgb(19,33,55);DARK->Color.rgb(24,32,42);SEPIA->Color.rgb(255,248,231);else->Color.WHITE}
    fun text(c:Context)=when(get(c)){AMOLED->Color.rgb(245,245,245);COSMOS->Color.rgb(239,241,255);AVATAR->Color.rgb(231,249,255);MIDNIGHT->Color.rgb(235,242,255);DARK->Color.rgb(235,240,247);SEPIA->Color.rgb(70,57,39);else->Color.rgb(21,38,59)}
    fun muted(c:Context)=when(get(c)){AMOLED->Color.rgb(194,192,177);COSMOS->Color.rgb(166,181,221);AVATAR->Color.rgb(146,205,222);MIDNIGHT->Color.rgb(169,190,219);DARK->Color.rgb(176,188,202);SEPIA->Color.rgb(111,94,69);else->Color.rgb(80,94,110)}
    fun accent(c:Context)=when(get(c)){AMOLED->Color.rgb(255,201,104);COSMOS->Color.rgb(184,150,255);AVATAR->Color.rgb(77,226,221);MIDNIGHT->Color.rgb(126,181,255);DARK->Color.rgb(157,194,255);SEPIA->Color.rgb(91,74,45);else->Color.rgb(24,91,130)}
    fun optionBg(c:Context)=when(get(c)){AMOLED->Color.BLACK;COSMOS->Color.rgb(11,16,36);AVATAR->Color.rgb(8,39,52);MIDNIGHT->Color.rgb(20,37,61);DARK->Color.rgb(27,37,49);SEPIA->Color.rgb(250,243,223);else->Color.rgb(232,242,251)}
    fun optionText(c:Context)=when(get(c)){AMOLED->Color.rgb(249,246,233);COSMOS->Color.rgb(239,235,255);AVATAR->Color.rgb(224,255,252);MIDNIGHT->Color.rgb(231,240,255);DARK->Color.rgb(232,239,248);SEPIA->Color.rgb(68,54,36);else->Color.rgb(18,39,61)}
    fun correctBg(c:Context)=when(get(c)){AMOLED->Color.rgb(14,45,27);COSMOS->Color.rgb(12,42,38);AVATAR->Color.rgb(9,52,47);MIDNIGHT->Color.rgb(17,55,52);DARK->Color.rgb(28,52,76);SEPIA->Color.rgb(224,239,220);else->Color.rgb(220,244,228)}
    fun correctText(c:Context)=when(get(c)){AMOLED->Color.rgb(130,235,160);COSMOS->Color.rgb(118,238,207);AVATAR->Color.rgb(110,242,223);MIDNIGHT->Color.rgb(137,230,214);DARK->Color.rgb(178,218,255);SEPIA->Color.rgb(35,91,54);else->Color.rgb(17,91,54)}
    fun wrongBg(c:Context)=when(get(c)){AMOLED->Color.rgb(55,17,24);COSMOS->Color.rgb(58,17,45);AVATAR->Color.rgb(58,19,39);MIDNIGHT->Color.rgb(62,24,38);DARK->Color.rgb(67,29,38);SEPIA->Color.rgb(247,225,225);else->Color.rgb(252,226,230)}
    fun wrongText(c:Context)=when(get(c)){AMOLED->Color.rgb(255,145,165);COSMOS->Color.rgb(255,153,218);AVATAR->Color.rgb(255,159,199);MIDNIGHT->Color.rgb(255,158,185);DARK->Color.rgb(255,190,201);SEPIA->Color.rgb(120,38,52);else->Color.rgb(137,36,52)}
    fun explanationBg(c:Context)=when(get(c)){AMOLED->Color.BLACK;COSMOS->Color.rgb(7,10,25);AVATAR->Color.rgb(5,25,36);MIDNIGHT->Color.rgb(16,29,48);DARK->Color.rgb(24,33,44);SEPIA->Color.rgb(237,225,199);else->Color.rgb(232,244,252)}
    fun explanationTitle(c:Context)=when(get(c)){AMOLED->Color.rgb(126,205,255);COSMOS->Color.rgb(191,163,255);AVATAR->Color.rgb(105,236,228);MIDNIGHT->Color.rgb(144,201,255);DARK->Color.rgb(164,207,255);SEPIA->Color.rgb(76,62,42);else->Color.rgb(35,91,125)}
    fun dialogBg(c:Context)=if(isDark(c)) elevated(c) else Color.WHITE
    fun dialogText(c:Context)=text(c)
    fun dialogMuted(c:Context)=muted(c)
    fun peacockFill(c:Context)=when(get(c)){AMOLED->Color.rgb(10,62,67);COSMOS->Color.rgb(10,54,70);AVATAR->Color.rgb(8,66,66);MIDNIGHT->Color.rgb(18,78,88);DARK->Color.rgb(18,91,94);SEPIA->Color.rgb(211,235,226);else->Color.rgb(216,242,236)}
    fun peacockText(c:Context)=when(get(c)){AMOLED->Color.rgb(127,235,224);COSMOS->Color.rgb(143,226,255);AVATAR->Color.rgb(111,245,226);MIDNIGHT->Color.rgb(135,232,224);DARK->Color.rgb(125,225,216);SEPIA->Color.rgb(25,91,79);else->Color.rgb(15,104,91)}
    fun statsFill(c:Context)=when(get(c)){AMOLED->Color.rgb(66,28,36);COSMOS->Color.rgb(55,28,68);AVATAR->Color.rgb(54,27,52);MIDNIGHT->Color.rgb(72,31,44);DARK->Color.rgb(78,35,43);SEPIA->Color.rgb(249,220,220);else->Color.rgb(252,225,228)}
    fun statsText(c:Context)=when(get(c)){AMOLED->Color.rgb(255,177,190);COSMOS->Color.rgb(239,185,255);AVATAR->Color.rgb(255,183,218);MIDNIGHT->Color.rgb(255,183,198);DARK->Color.rgb(255,190,201);SEPIA->Color.rgb(126,43,55);else->Color.rgb(139,44,58)}
    fun resultBg(c:Context)=when(get(c)){AMOLED->Color.rgb(14,45,27);COSMOS->Color.rgb(12,42,38);AVATAR->Color.rgb(9,52,47);MIDNIGHT->Color.rgb(17,55,52);DARK->Color.rgb(28,52,76);SEPIA->Color.rgb(237,225,199);else->Color.rgb(238,246,251)}
    fun bookmarkFill(c:Context,type:String):Int=if(isDark(c))when(type.lowercase()){"important"->Color.rgb(61,48,12);"revise"->Color.rgb(18,48,78);"doubt"->Color.rgb(42,47,55);"favorite","favourite"->Color.rgb(70,27,39);else->Color.rgb(35,43,52)} else when(type.lowercase()){"important"->Color.rgb(255,238,184);"revise"->Color.rgb(216,235,255);"doubt"->Color.rgb(232,237,241);"favorite","favourite"->Color.rgb(255,221,229);else->Color.rgb(232,239,244)}
    fun bookmarkText(c:Context,type:String):Int=if(isDark(c))when(type.lowercase()){"important"->Color.rgb(255,219,105);"revise"->Color.rgb(151,211,255);"doubt"->Color.rgb(210,218,228);"favorite","favourite"->Color.rgb(255,171,190);else->Color.rgb(225,233,242)} else when(type.lowercase()){"important"->Color.rgb(105,72,0);"revise"->Color.rgb(18,78,135);"doubt"->Color.rgb(52,64,78);"favorite","favourite"->Color.rgb(139,38,65);else->Color.rgb(35,52,70)}
    // Shared pastel semantic palette. Light themes use airy collection-card fills; dark themes
    // retain the same hue families at safe luminance/contrast instead of raw light colours.
    fun pastelBlueFill(c:Context)=if(isDark(c))Color.rgb(24,52,78) else Color.rgb(224,242,255)
    fun pastelBlueText(c:Context)=if(isDark(c))Color.rgb(170,215,255) else Color.rgb(22,83,125)
    fun pastelPinkFill(c:Context)=if(isDark(c))Color.rgb(70,35,51) else Color.rgb(255,231,239)
    fun pastelPinkText(c:Context)=if(isDark(c))Color.rgb(255,177,204) else Color.rgb(133,45,75)
    fun pastelRedFill(c:Context)=if(isDark(c))Color.rgb(72,30,38) else Color.rgb(255,229,229)
    fun pastelRedText(c:Context)=if(isDark(c))Color.rgb(255,178,190) else Color.rgb(139,42,52)
    fun pastelYellowFill(c:Context)=if(isDark(c))Color.rgb(67,56,22) else Color.rgb(255,245,205)
    fun pastelYellowText(c:Context)=if(isDark(c))Color.rgb(255,224,120) else Color.rgb(111,80,0)
    fun pastelLavenderFill(c:Context)=if(isDark(c))Color.rgb(52,40,73) else Color.rgb(241,233,255)
    fun pastelLavenderText(c:Context)=if(isDark(c))Color.rgb(208,184,255) else Color.rgb(86,59,130)
    fun pastelAccentFill(c:Context,index:Int)=when(index%4){0->pastelBlueFill(c);1->pastelPinkFill(c);2->pastelYellowFill(c);else->pastelLavenderFill(c)}
    fun pastelAccentText(c:Context,index:Int)=when(index%4){0->pastelBlueText(c);1->pastelPinkText(c);2->pastelYellowText(c);else->pastelLavenderText(c)}
    fun backgroundDrawable(c:Context): android.graphics.drawable.Drawable = ThemeAtmosphereDrawable(c)
}
