#!/usr/bin/env python3
from pathlib import Path
import re,sys

OLD='versionName = "8.3.632"'; OLDC='versionCode = 718'
NEW='versionName = "8.3.633"'; NEWC='versionCode = 719'

def one(root,n):
    x=list(root.rglob(n))
    if len(x)!=1: raise SystemExit(f"v8.3.633 expected one {n}, found {len(x)}")
    return x[0]
def need(s,old,new,label):
    if old not in s: raise SystemExit("v8.3.633 anchor missing: "+label)
    return s.replace(old,new,1)

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_633_theme_bookmark_glass.py <project>")
    root=Path(sys.argv[1]); gpath=root/"app/build.gradle.kts"; g=gpath.read_text()
    if OLD not in g or OLDC not in g: raise SystemExit("v8.3.633 requires 8.3.632/718")

    # Theme stage 1: Pastel Prism only. Other themes are intentionally untouched.
    p=one(root,"RovexPremiumPalette.kt"); s=p.read_text()
    old='''    private fun pastel(d: Boolean) = if (!d) roles(
        Color.rgb(244,247,255),Color.rgb(255,255,255),Color.rgb(239,243,255),Color.rgb(255,255,255),
        Color.rgb(78,94,226),Color.WHITE,Color.rgb(226,231,255),Color.rgb(43,54,129),
        Color.rgb(110,91,205),Color.rgb(202,83,154),Color.rgb(28,35,72),Color.rgb(91,99,132),
        Color.rgb(177,183,211),Color.rgb(118,128,171),Color.rgb(25,139,99),Color.WHITE,Color.rgb(220,248,237),Color.rgb(0,75,49),
        Color.rgb(193,55,93),Color.WHITE,Color.rgb(255,226,236),Color.rgb(113,22,51),
        Color.rgb(164,104,0),Color.WHITE,Color.rgb(255,240,205),Color.rgb(88,55,0),
        Color.rgb(65,110,191),Color.rgb(226,238,255),Color.rgb(230,234,255),
        Color.rgb(250,251,255),Color.rgb(220,225,255),Color.rgb(48,57,143)
    ) else clinical(true)'''
    new='''    /** Pastel Prism dark profile; this stage deliberately does not modify other themes. */
    private fun pastel(d: Boolean) = if (!d) roles(
        Color.rgb(244,247,255),Color.rgb(255,255,255),Color.rgb(239,243,255),Color.rgb(255,255,255),
        Color.rgb(78,94,226),Color.WHITE,Color.rgb(226,231,255),Color.rgb(43,54,129),
        Color.rgb(110,91,205),Color.rgb(202,83,154),Color.rgb(28,35,72),Color.rgb(91,99,132),
        Color.rgb(177,183,211),Color.rgb(118,128,171),Color.rgb(25,139,99),Color.WHITE,Color.rgb(220,248,237),Color.rgb(0,75,49),
        Color.rgb(193,55,93),Color.WHITE,Color.rgb(255,226,236),Color.rgb(113,22,51),
        Color.rgb(164,104,0),Color.WHITE,Color.rgb(255,240,205),Color.rgb(88,55,0),
        Color.rgb(65,110,191),Color.rgb(226,238,255),Color.rgb(230,234,255),
        Color.rgb(250,251,255),Color.rgb(220,225,255),Color.rgb(48,57,143)
    ) else roles(
        Color.rgb(14,13,28),Color.rgb(24,22,45),Color.rgb(34,31,61),Color.rgb(43,38,75),
        Color.rgb(169,158,255),Color.rgb(25,20,60),Color.rgb(54,47,104),Color.rgb(232,226,255),
        Color.rgb(113,225,255),Color.rgb(255,145,208),Color.rgb(248,245,255),Color.rgb(197,190,222),
        Color.rgb(91,84,124),Color.rgb(132,122,170),Color.rgb(91,235,181),Color.rgb(0,45,30),Color.rgb(12,72,56),Color.rgb(173,255,225),
        Color.rgb(255,112,157),Color.rgb(73,8,30),Color.rgb(91,22,46),Color.rgb(255,211,223),
        Color.rgb(255,204,91),Color.rgb(69,43,0),Color.rgb(82,60,13),Color.rgb(255,231,160), 
        Color.rgb(111,215,255),Color.rgb(10,55,81),Color.rgb(55,47,103),
        Color.rgb(17,15,34),Color.rgb(55,48,101),Color.rgb(238,233,255)
    )'''
    s=need(s,old,new,"Pastel Prism palette"); p.write_text(s)

    # Root cause: ConcurrentHashMap rejects null; clearing a bookmark wrote null into it.
    p=one(root,"QuizViewModel.kt"); s=p.read_text()
    s=need(s,'private val pendingBookmarks = java.util.concurrent.ConcurrentHashMap<String, String?>()',
             'private val pendingBookmarks = QuizBookmarkOverlay()',"nullable pending bookmark map")
    old='''    fun bookmark(stableKey: String): String? = pendingBookmarks[stableKey] ?: foregroundProgress.record(stableKey)?.bookmark
    fun setBookmark(stableKey: String, value: String?) {
        pendingBookmarks[stableKey] = value
        bookmarks.set(stableKey, value)
        PerformanceManager.submit {
            // Keep the optimistic overlay until the durable write is reflected in the snapshot.
            foregroundProgress = PerformanceManager.progress(appContext)
            pendingBookmarks.remove(stableKey, value)
        }
    }'''
    new='''    fun bookmark(stableKey: String): String? =
        if (pendingBookmarks.contains(stableKey)) pendingBookmarks.value(stableKey)
        else foregroundProgress.record(stableKey)?.bookmark

    fun setBookmark(stableKey: String, value: String?) {
        pendingBookmarks.put(stableKey, value)
        bookmarks.set(stableKey, value)
        PerformanceManager.submit {
            foregroundProgress = PerformanceManager.progress(appContext)
            pendingBookmarks.removeIfEquals(stableKey, value)
        }
    }'''
    s=need(s,old,new,"bookmark methods"); p.write_text(s)
    pkg=p.parent
    (pkg/"QuizBookmarkOverlay.kt").write_text('''package com.localqbank.library

import java.util.concurrent.ConcurrentHashMap

/** Explicit null/presence-safe optimistic overlay for asynchronous bookmark writes. */
internal class QuizBookmarkOverlay {
    private companion object { const val CLEAR="\\u0000ROVEX_BOOKMARK_CLEAR" }
    private val values=ConcurrentHashMap<String,String>()
    fun put(key:String,value:String?){ values[key]=value ?: CLEAR }
    fun contains(key:String)=values.containsKey(key)
    fun value(key:String):String?=values[key]?.let{if(it==CLEAR)null else it}
    fun removeIfEquals(key:String,value:String?){ values.remove(key,value ?: CLEAR) }
}
''')
    tdir=root/"app/src/test/java/com/localqbank/library"; tdir.mkdir(parents=True,exist_ok=True)
    (tdir/"QuizBookmarkOverlayTest.kt").write_text('''package com.localqbank.library

import org.junit.Assert.*
import org.junit.Test

class QuizBookmarkOverlayTest {
    @Test fun clearUsesPresenceSafeSentinel() {
        val o=QuizBookmarkOverlay()
        o.put("q","important"); assertEquals("important",o.value("q"))
        o.put("q",null); assertTrue(o.contains("q")); assertNull(o.value("q"))
        o.removeIfEquals("q",null); assertFalse(o.contains("q"))
    }
}
''')

    # Glass root cause: old fill alpha was 218-238 and there were only fill+border layers.
    p=one(root,"RovexVisualColors.kt"); s=p.read_text()
    old='''    fun glassFill(context: Context, prominent: Boolean = false): Int {
        val base = if (prominent) p(context).surfaceElevated else p(context).surface
        val alpha = if (ThemeManager.isDark(context)) if (prominent) 232 else 218 else if (prominent) 238 else 224
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    fun glassHighlight(context: Context): Int =
        Color.argb(if (ThemeManager.isDark(context)) 45 else 105, 255, 255, 255)'''
    new='''    fun glassBase(context: Context, prominent: Boolean = false): Int {
        val base=if(prominent)p(context).surfaceElevated else p(context).surface
        val alpha=if(ThemeManager.isDark(context)) if(prominent)150 else 126 else if(prominent)156 else 132
        return Color.argb(alpha,Color.red(base),Color.green(base),Color.blue(base))
    }
    fun glassTint(context: Context, prominent: Boolean = false): Int {
        val a=p(context).primary
        val alpha=if(ThemeManager.isDark(context)) if(prominent)34 else 24 else if(prominent)26 else 18
        return Color.argb(alpha,Color.red(a),Color.green(a),Color.blue(a))
    }
    fun glassHighlight(context: Context): Int =
        Color.argb(if(ThemeManager.isDark(context))72 else 108,255,255,255)
    fun glassSheen(context: Context): Int =
        Color.argb(if(ThemeManager.isDark(context))24 else 42,255,255,255)
    fun glassShadow(context: Context): Int =
        Color.argb(if(ThemeManager.isDark(context))46 else 22,0,0,0)'''
    s=need(s,old,new,"glass opacity/tokens"); p.write_text(s)

    p=one(root,"RovexVisualSurfaceStyle.kt"); s=p.read_text()
    start=s.index("object RovexVisualSurfaceStyle {")
    body='''object RovexVisualSurfaceStyle {
    fun glass(context: Context, radiusDp: Float, prominent: Boolean = false): LayerDrawable {
        val d=context.resources.displayMetrics.density
        val r=radiusDp.coerceIn(8f,28f)*d
        val base=RovexVisualColors.glassBase(context,prominent)
        val tint=RovexVisualColors.glassTint(context,prominent)
        val fill=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,base,Color.argb((Color.alpha(base)*.88f).toInt(),Color.red(base),Color.green(base),Color.blue(base)))).apply{cornerRadius=r}
        val tintLayer=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(tint,Color.TRANSPARENT)).apply{cornerRadius=r}
        val sheen=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(RovexVisualColors.glassSheen(context),Color.TRANSPARENT,RovexVisualColors.glassShadow(context))).apply{cornerRadius=r}
        val border=GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=r;setStroke((d).toInt().coerceAtLeast(1),RovexVisualColors.glassHighlight(context))}
        return LayerDrawable(arrayOf(fill,tintLayer,sheen,border))
    }
    fun apply(view:View,context:Context,radiusDp:Float,prominent:Boolean=false,touch:Boolean=true){
        view.background=glass(context,radiusDp,prominent)
        if(touch&&(view.isClickable||view.hasOnClickListeners())) RovexTouchFeedback.bind(view)
    }
}
'''
    s=s[:start]+body; p.write_text(s)

    # Daily Progress: luminous/sparkling points instead of flat dots.
    p=one(root,"Last28StudyDaysGraphView.kt"); s=p.read_text()
    old='''            dot.color = if (!hasData) android.graphics.Color.argb(45,
                android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent))
            else if (accuracy >= 75f) android.graphics.Color.rgb(62, 210, 165)
            else if (accuracy >= 50f) android.graphics.Color.rgb(255, 193, 73)
            else android.graphics.Color.rgb(255, 102, 137)
            c.drawCircle(x, y, if (hasData) 4.3f * d else 2.4f * d, dot)'''
    new='''            val pointColor=if(accuracy>=75f) android.graphics.Color.rgb(62,210,165)
                else if(accuracy>=50f) android.graphics.Color.rgb(255,193,73)
                else android.graphics.Color.rgb(255,102,137)
            if(!hasData){
                dot.color=android.graphics.Color.argb(45,android.graphics.Color.red(accent),android.graphics.Color.green(accent),android.graphics.Color.blue(accent))
                c.drawCircle(x,y,2.4f*d,dot)
            } else {
                dot.color=android.graphics.Color.argb(30,android.graphics.Color.red(pointColor),android.graphics.Color.green(pointColor),android.graphics.Color.blue(pointColor))
                c.drawCircle(x,y,9f*d,dot)
                dot.color=android.graphics.Color.argb(65,android.graphics.Color.red(pointColor),android.graphics.Color.green(pointColor),android.graphics.Color.blue(pointColor))
                c.drawCircle(x,y,6f*d,dot)
                dot.color=pointColor; c.drawCircle(x,y,3.9f*d,dot)
                dot.color=android.graphics.Color.WHITE; c.drawCircle(x,y,1.35f*d,dot)
                val sparkle=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.argb(180,255,255,255);strokeWidth=1f*d;strokeCap=Paint.Cap.ROUND}
                val ray=5.8f*d
                c.drawLine(x-ray,y,x+ray,y,sparkle); c.drawLine(x,y-ray,x,y+ray,sparkle)
            }'''
    s=need(s,old,new,"sparkling accuracy dots"); p.write_text(s)

    gpath.write_text(g.replace(OLD,NEW,1).replace(OLDC,NEWC,1))
    print("V8.3.633_APPLIED")
if __name__=="__main__": main()
