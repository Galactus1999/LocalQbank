from pathlib import Path
ROOT=Path(__file__).resolve().parent
B=ROOT/'app/src/main/java/com/localqbank/library'
def rw(rel,fn):
 p=ROOT/rel; s=p.read_text(); p.write_text(fn(s))

rw('app/src/main/java/com/localqbank/library/QuizRichTextRenderer.kt',lambda s:s.replace('''    private fun sanitizeImportedColors(raw: String): String {
        var out = raw''','''    private fun sanitizeImportedColors(raw: String): String {
        // Normalize provider/importer literal <br> markup before Android HTML parsing.
        var out = raw
            .replace(Regex("(?i)&lt;\\\\s*br\\\\s*/?\\\\s*&gt;"), "\\n")
            .replace(Regex("(?i)<\\\\s*br\\\\s*/?\\\\s*>"), "\\n")'''))
rw('app/src/main/java/com/localqbank/library/RovexMarkdown.kt',lambda s:s.replace('''    var s = raw.replace("\\r\\n", "\\n").replace("\\r", "\\n")''','''    var s = raw.replace("\\r\\n", "\\n").replace("\\r", "\\n")
        .replace(Regex("(?i)&lt;\\\\s*br\\\\s*/?\\\\s*&gt;"), "\\n")
        .replace(Regex("(?i)<\\\\s*br\\\\s*/?\\\\s*>"), "\\n")'''))
rw('app/src/main/java/com/localqbank/library/QuizActivity.kt',lambda s:s.replace('''        val outer = HorizontalScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, dp(4), 0, dp(16))
        }''','''        val outer = RovexHorizontalScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isHorizontalScrollBarEnabled = true
            isVerticalScrollBarEnabled = false
            setPadding(0, dp(4), 0, dp(16))
        }''').replace('''private class LockedScrollView(context: android.content.Context) : ScrollView(context) {''','''private class RovexHorizontalScrollView(context: android.content.Context) : HorizontalScrollView(context) {
    private var downX=0f; private var downY=0f; private var horizontal=false
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when(ev.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=ev.x;downY=ev.y;horizontal=false;parent?.requestDisallowInterceptTouchEvent(false)}
            MotionEvent.ACTION_MOVE->{val dx=ev.x-downX;val dy=ev.y-downY;if(!horizontal&&kotlin.math.abs(dx)>8f*resources.displayMetrics.density&&kotlin.math.abs(dx)>kotlin.math.abs(dy)*1.15f)horizontal=true;if(horizontal)parent?.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{parent?.requestDisallowInterceptTouchEvent(false);horizontal=false}
        }; return super.onInterceptTouchEvent(ev)
    }
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if(ev.actionMasked==MotionEvent.ACTION_MOVE&&horizontal)parent?.requestDisallowInterceptTouchEvent(true)
        if(ev.actionMasked==MotionEvent.ACTION_UP||ev.actionMasked==MotionEvent.ACTION_CANCEL){parent?.requestDisallowInterceptTouchEvent(false);horizontal=false}
        return super.onTouchEvent(ev)
    }
}

private class LockedScrollView(context: android.content.Context) : ScrollView(context) {'''))
rw('app/src/main/java/com/localqbank/library/ThemeAtmosphereDrawable.kt',lambda s:s.replace('''            else -> {
                paint.shader = null
                paint.color = ThemeManager.bg(context)
                canvas.drawRect(0f, 0f, w, h, paint)
            }''','''            ThemeManager.LIGHT -> drawAuroraLight(canvas,w,h,false)
            ThemeManager.SEPIA -> drawAuroraLight(canvas,w,h,true)
            else -> { paint.shader=null;paint.color=ThemeManager.bg(context);canvas.drawRect(0f,0f,w,h,paint) }''').replace('''    private fun drawMidnight(canvas: Canvas,w:Float,h:Float){''','''    private fun drawAuroraLight(canvas:Canvas,w:Float,h:Float,sepia:Boolean){
        val a=if(sepia)Color.rgb(250,244,229) else Color.rgb(246,250,255);val b=if(sepia)Color.rgb(255,249,236) else Color.rgb(250,247,255)
        paint.shader=LinearGradient(0f,0f,w,h,a,b,Shader.TileMode.CLAMP);canvas.drawRect(0f,0f,w,h,paint)
        val blobs=if(sepia)intArrayOf(Color.argb(38,244,190,124),Color.argb(30,236,166,211),Color.argb(26,128,196,184)) else intArrayOf(Color.argb(42,110,198,255),Color.argb(34,190,140,255),Color.argb(28,90,220,200))
        arrayOf(floatArrayOf(w*.10f,h*.12f),floatArrayOf(w*.88f,h*.18f),floatArrayOf(w*.22f,h*.88f)).forEachIndexed{i,p->{paint.shader=RadialGradient(p[0],p[1],minOf(w,h)*.48f,intArrayOf(blobs[i],Color.TRANSPARENT),floatArrayOf(0f,1f),Shader.TileMode.CLAMP);canvas.drawCircle(p[0],p[1],minOf(w,h)*.48f,paint)}}
    }

    private fun drawMidnight(canvas: Canvas,w:Float,h:Float){'''))
rw('app/src/main/java/com/localqbank/library/RovexModernUi.kt',lambda s:s.replace('root.setBackgroundColor(ThemeManager.bg(activity))','root.background=ThemeManager.backgroundDrawable(activity)',2))
rw('app/src/main/java/com/localqbank/library/MainActivity.kt',lambda s:s.replace('''        findViewById<View>(R.id.flashcardCard)?.background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.elevated(this@MainActivity),20f)
        findViewById<View>(R.id.performanceLabCard)?.background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.panel(this@MainActivity),20f)''','''        findViewById<View>(R.id.flashcardCard)?.background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.pastelBlueFill(this@MainActivity),22f)
        findViewById<View>(R.id.performanceLabCard)?.background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.pastelLavenderFill(this@MainActivity),22f)
        findViewById<View>(R.id.renCard)?.background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.pastelAccentFill(this@MainActivity,1),22f)'''))
rw('app/src/main/java/com/localqbank/library/BenQuestionAiContextDialog.kt',lambda s:s.replace('val web = WebView(activity).apply {','val web = BenChatWebView(activity).apply {').replace('''            setBackgroundColor(Color.TRANSPARENT)
        }''','''            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled=true
            isHorizontalScrollBarEnabled=true
            overScrollMode=android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            scrollBarStyle=android.view.View.SCROLLBARS_INSIDE_OVERLAY
        }''',1).replace('''            background = UiDrawableUtils.roundedDrawable(activity, ThemeManager.dialogBg(activity), 26f)''','''            background = ThemeManager.backgroundDrawable(activity)''').replace('''            setTextColor(ThemeManager.text(activity)); background = rounded(activity, ThemeManager.elevated(activity), 13f); setOnClickListener { click() }''','''            setTextColor(ThemeManager.text(activity)); background = rounded(activity, ThemeManager.pastelAccentFill(activity, 0), 18f); elevation=dp(activity,2).toFloat(); setPadding(dp(activity,5),0,dp(activity,5),0); setOnClickListener { click() }'''))
rw('app/build.gradle.kts',lambda s:s.replace('versionCode = 393','versionCode = 394',1).replace('versionName = "8.3.299"','versionName = "8.3.300"',1))
