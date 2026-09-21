from pathlib import Path
import re
R=Path(__file__).resolve().parent
B=R/'app/src/main/java/com/localqbank/library'
def F(x): return R/x
def rw(p,fn):
 q=F(p); q.write_text(fn(q.read_text()))
# version
rw('app/build.gradle.kts',lambda s:s.replace('versionCode = 390','versionCode = 391',1).replace('versionName = "8.3.296"','versionName = "8.3.297"',1))
# rename retired theme id in Kotlin
for q in B.rglob('*.kt'): q.write_text(q.read_text().replace('AMOLED_DARK','OBSIDIAN_NIGHT'))
# ThemeManager migration + palette
q=B/'ThemeManager.kt'; s=q.read_text()
s=s.replace('const val OBSIDIAN_NIGHT="amoled_dark"','const val OBSIDIAN_NIGHT="obsidian_night"')
s=re.sub(r'fun get\(c:Context\):String \{.*?\n    \}', '''fun get(c:Context):String {
        val raw=c.getSharedPreferences("ui",Context.MODE_PRIVATE).getString("theme",LIGHT)?:LIGHT
        return when(raw){
            "amoled","amoled_dark" -> { c.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().putString("theme",OBSIDIAN_NIGHT).apply(); OBSIDIAN_NIGHT }
            else -> raw
        }
    }''',s,count=1,flags=re.S)
for a,b in {
'OBSIDIAN_NIGHT->Color.BLACK':'OBSIDIAN_NIGHT->Color.rgb(8,10,14)',
'OBSIDIAN_NIGHT->Color.rgb(20,17,13)':'OBSIDIAN_NIGHT->Color.rgb(22,28,38)',
'OBSIDIAN_NIGHT->Color.rgb(250,250,210)':'OBSIDIAN_NIGHT->Color.rgb(255,246,220)',
'OBSIDIAN_NIGHT->Color.rgb(247,243,234)':'OBSIDIAN_NIGHT->Color.rgb(243,246,250)',
'OBSIDIAN_NIGHT->Color.rgb(156,146,132)':'OBSIDIAN_NIGHT->Color.rgb(169,180,194)',
'OBSIDIAN_NIGHT->Color.rgb(255,178,56)':'OBSIDIAN_NIGHT->Color.rgb(127,231,226)',
'OBSIDIAN_NIGHT->Color.rgb(46,14,20)':'OBSIDIAN_NIGHT->Color.rgb(53,20,29)',
'OBSIDIAN_NIGHT->Color.rgb(255,143,160)':'OBSIDIAN_NIGHT->Color.rgb(255,143,164)',
'OBSIDIAN_NIGHT->Color.rgb(255,208,138)':'OBSIDIAN_NIGHT->Color.rgb(255,204,128)',
'OBSIDIAN_NIGHT->Color.rgb(12,42,24)':'OBSIDIAN_NIGHT->Color.rgb(13,48,34)',
'OBSIDIAN_NIGHT->Color.rgb(124,239,160)':'OBSIDIAN_NIGHT->Color.rgb(123,238,168)',
'OBSIDIAN_NIGHT->Color.rgb(6,42,46)':'OBSIDIAN_NIGHT->Color.rgb(10,49,51)',
'OBSIDIAN_NIGHT->Color.rgb(111,224,216)':'OBSIDIAN_NIGHT->Color.rgb(127,231,226)',
'OBSIDIAN_NIGHT->Color.rgb(48,11,28)':'OBSIDIAN_NIGHT->Color.rgb(55,27,45)',
'OBSIDIAN_NIGHT->Color.rgb(255,156,194)':'OBSIDIAN_NIGHT->Color.rgb(255,170,205)',
}.items(): s=s.replace(a,b)
s=s.replace('// AMOLED Dark question-reading palette: LightGoldenrodYellow (#FAFAD2) is the primary reading color.\n    // Ben + AI uses a restrained luminous cyan to separate machine/context surfaces from the core answer text.','// Obsidian Night semantic reading palette: warm ivory medical text, cyan Ben/AI.')
q.write_text(s)
# old theme labels / black branches
for name in ['SettingsScreen.kt','QuizActivity.kt','MainActivity.kt']:
 q=B/name; q.write_text(q.read_text().replace('AMOLED Dark','Obsidian Night'))
q=B/'SettingsScreen.kt'; s=q.read_text().replace('True-black OLED reading with restrained near-black surfaces','Near-black OLED reading with warm ivory text and cyan guidance').replace('AMOLED-indigo bioluminescent Pandora-inspired theme','Indigo bioluminescent Pandora-inspired theme').replace('ThemeManager.OBSIDIAN_NIGHT->Color.rgb(0,0,0)','ThemeManager.OBSIDIAN_NIGHT->Color.rgb(14,18,25)').replace('if(ThemeManager.get(activity)==ThemeManager.OBSIDIAN_NIGHT)Color.BLACK','if(ThemeManager.get(activity)==ThemeManager.OBSIDIAN_NIGHT)ThemeManager.panel(activity)'); q.write_text(s)
for name in ['CollectionActivity.kt','TestListActivity.kt','SourceAdapter.kt','RovexPerformanceLabView.kt','RovexFlashcardSummaryView.kt','GoogleSearchActivity.kt','AdaptiveEngineDashboardView.kt','MainActivity.kt','QuizActivity.kt']:
 q=B/name; s=q.read_text()
 s=s.replace('if(ThemeManager.get(parent.context)==ThemeManager.OBSIDIAN_NIGHT) Color.BLACK','if(ThemeManager.get(parent.context)==ThemeManager.OBSIDIAN_NIGHT) ThemeManager.panel(parent.context)').replace('if(ThemeManager.get(v.context)==ThemeManager.OBSIDIAN_NIGHT) Color.BLACK','if(ThemeManager.get(v.context)==ThemeManager.OBSIDIAN_NIGHT) ThemeManager.panel(v.context)').replace('ThemeManager.get(ctx)==ThemeManager.OBSIDIAN_NIGHT -> if(inProgress) Color.rgb(49,43,20) else Color.BLACK','ThemeManager.get(ctx)==ThemeManager.OBSIDIAN_NIGHT -> if(inProgress) Color.rgb(49,43,20) else ThemeManager.panel(ctx)').replace('if(ThemeManager.get(ctx)==ThemeManager.OBSIDIAN_NIGHT)Color.BLACK','if(ThemeManager.get(ctx)==ThemeManager.OBSIDIAN_NIGHT)ThemeManager.panel(ctx)').replace('if(ThemeManager.get(ctx)==ThemeManager.OBSIDIAN_NIGHT) Color.BLACK','if(ThemeManager.get(ctx)==ThemeManager.OBSIDIAN_NIGHT) ThemeManager.panel(ctx)').replace('if (ThemeManager.get(context) == ThemeManager.OBSIDIAN_NIGHT) android.graphics.Color.BLACK','if (ThemeManager.get(context) == ThemeManager.OBSIDIAN_NIGHT) ThemeManager.elevated(context)').replace('if (ThemeManager.get(context) == ThemeManager.OBSIDIAN_NIGHT) Color.rgb(24,27,34)','if (ThemeManager.get(context) == ThemeManager.OBSIDIAN_NIGHT) ThemeManager.panel(context)').replace('if(ThemeManager.get(this@GoogleSearchActivity)==ThemeManager.OBSIDIAN_NIGHT)Color.BLACK','if(ThemeManager.get(this@GoogleSearchActivity)==ThemeManager.OBSIDIAN_NIGHT)ThemeManager.bg(this@GoogleSearchActivity)').replace('if(ThemeManager.get(this@MainActivity)==ThemeManager.OBSIDIAN_NIGHT) Color.BLACK','if(ThemeManager.get(this@MainActivity)==ThemeManager.OBSIDIAN_NIGHT) ThemeManager.panel(this@MainActivity)').replace('if(ThemeManager.get(this@MainActivity)==ThemeManager.OBSIDIAN_NIGHT)Color.BLACK','if(ThemeManager.get(this@MainActivity)==ThemeManager.OBSIDIAN_NIGHT)ThemeManager.panel(this@MainActivity)').replace('if (!answered) (if (ThemeManager.get(this@QuizActivity) == ThemeManager.OBSIDIAN_NIGHT) Color.BLACK else','if (!answered) (if (ThemeManager.get(this@QuizActivity) == ThemeManager.OBSIDIAN_NIGHT) ThemeManager.panel(this@QuizActivity) else')
 q.write_text(s)
# atmosphere
q=B/'ThemeAtmosphereDrawable.kt'; s=q.read_text().replace('ThemeManager.OBSIDIAN_NIGHT -> drawAmoledDark(canvas, w, h)','ThemeManager.OBSIDIAN_NIGHT -> drawObsidianNight(canvas, w, h)'); a=s.find('    private fun drawAmoledDark(')
if a>=0:
 e=s.find('\n    private fun drawCosmos',a); s=s[:a]+'''    private fun drawObsidianNight(canvas: Canvas,w:Float,h:Float){
        paint.shader=LinearGradient(0f,0f,w,h,Color.rgb(8,10,14),Color.rgb(13,18,25),Shader.TileMode.CLAMP);canvas.drawRect(0f,0f,w,h,paint)
        paint.shader=RadialGradient(w*.78f,h*.16f,minOf(w,h)*.52f,intArrayOf(Color.argb(42,127,231,226),Color.argb(15,84,137,151),Color.TRANSPARENT),floatArrayOf(0f,.46f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(w*.78f,h*.16f,minOf(w,h)*.52f,paint);drawStars(canvas,w,h,Color.argb(110,215,230,238))
        realisticPlanet(canvas,w*.86f,h*.14f,minOf(w,h)*.065f,Color.rgb(112,224,216),Color.rgb(15,42,45),true)
        realisticPlanet(canvas,w*.13f,h*.82f,minOf(w,h)*.03f,Color.rgb(255,194,112),Color.rgb(42,27,14),false)
    }
'''+s[e:]
s=s.replace('AMOLED','OLED'); q.write_text(s)
# quiz helper naming
q=B/'QuizActivity.kt'; s=q.read_text().replace('enforceAmoledTextVisibility','enforceThemeTextVisibility').replace('// Radical invariant: imported HTML owns formatting only, never semantic text colour.','// Rendering invariant: imported HTML owns formatting only, never semantic text colour.'); q.write_text(s)
# splash wording
q=B/'RovexSplashView.kt'; q.write_text(q.read_text().replace('AMOLED-black','OLED-black').replace('True AMOLED black','Near-black OLED field').replace('AMOLED field','OLED field'))
# touch-aware chat WebView
(B/'BenChatWebView.kt').write_text('''package com.localqbank.library
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import kotlin.math.abs
class BenChatWebView @JvmOverloads constructor(context: Context,attrs: AttributeSet?=null,defStyleAttr:Int=0):WebView(context,attrs,defStyleAttr){
 private var downX=0f; private var downY=0f; private var horizontalGesture=false
 override fun onTouchEvent(e:MotionEvent):Boolean{ when(e.actionMasked){ MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;horizontalGesture=false;parent?.requestDisallowInterceptTouchEvent(false)}; MotionEvent.ACTION_MOVE->{val dx=e.x-downX;val dy=e.y-downY;if(!horizontalGesture&&abs(dx)>12f&&abs(dx)>abs(dy)*1.15f)horizontalGesture=true;if(horizontalGesture)parent?.requestDisallowInterceptTouchEvent(true)}; MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(horizontalGesture)parent?.requestDisallowInterceptTouchEvent(false);horizontalGesture=false} }; return super.onTouchEvent(e)}
 override fun onDetachedFromWindow(){parent?.requestDisallowInterceptTouchEvent(false);super.onDetachedFromWindow()}
}
''')
# RenActivity WebView + CSS
q=B/'RenActivity.kt'; s=q.read_text().replace('val answer=WebView(this).apply {','val answer=BenChatWebView(this).apply {')
a=s.find('<style>html,body{background:$bg!important'); e=s.find('</style>',a)
css='''<style>html,body{background:$bg!important;color:$text!important}body *{color:inherit}body{font-family:sans-serif;font-size:16px;line-height:1.58;margin:0;padding:4px 2px 20px;overflow-x:hidden;overflow-wrap:anywhere}.turn{box-sizing:border-box;padding:12px 14px;margin:8px 0;border-radius:18px;border:1px solid ${if(dark)"#2B3948" else "#D8DEE3"};background:${if(dark)"#121821" else "#F7F8F9"}}.turn.user{background:${if(dark)"#152530" else "#EEF6FA"}}.role{font-size:10px;font-weight:800;letter-spacing:.1em;color:$accent;margin-bottom:7px}p{margin:7px 0;max-width:100%;white-space:normal}li,dd{margin:3px 0}h1{font-size:23px;line-height:1.22;margin:13px 0 8px}h2{font-size:20px;line-height:1.25;margin:12px 0 7px}h3{font-size:18px;line-height:1.3;margin:11px 0 6px}h4,h5,h6{line-height:1.32;margin:9px 0 5px}blockquote{margin:9px 0;padding:8px 11px;border-left:4px solid $accent;background:${if(dark)"#17272D" else "#FFF7DF"};border-radius:7px}pre{box-sizing:border-box;max-width:100%;padding:10px;overflow:auto;background:${if(dark)"#0D131A" else "#EEF1F3"};border-radius:9px;white-space:pre-wrap;overflow-wrap:anywhere}code{background:${if(dark)"#1B2530" else "#E9EDF0"};padding:1px 4px;border-radius:4px;overflow-wrap:anywhere}.table-wrap{box-sizing:border-box;width:100%;max-width:100%;overflow-x:auto;overflow-y:hidden;margin:11px 0;padding-bottom:2px;overscroll-behavior-x:contain;-webkit-overflow-scrolling:touch;scrollbar-gutter:stable}.table-wrap table{border-collapse:collapse;border-spacing:0;width:max-content;min-width:100%;max-width:none;table-layout:auto;margin:0;background:${if(dark)"#101821" else "#FFFFFF"}}.table-wrap th,.table-wrap td{box-sizing:border-box;border:1px solid ${if(dark)"#385064" else "#CBD4DA"};padding:8px 9px;text-align:left;vertical-align:top;min-width:96px;max-width:280px;white-space:normal;overflow-wrap:anywhere;word-break:normal;line-height:1.45}.table-wrap th{background:${if(dark)"#1D3342" else "#EAF1F5"};color:${if(dark)"#B9F4EF" else "#155C79"};font-weight:750;white-space:normal}.table-wrap td:first-child,.table-wrap th:first-child{min-width:120px}.table-wrap td p,.table-wrap th p{margin:0}.table-wrap td strong,.table-wrap th strong{font-weight:750}.table-wrap::-webkit-scrollbar{height:7px}.table-wrap::-webkit-scrollbar-thumb{background:${if(dark)"#5A7284" else "#9AA9B3"};border-radius:8px}img{display:block;max-width:100%;height:auto;max-height:720px;object-fit:contain;border-radius:10px;margin:10px auto;background:#0B1014}a{color:${if(dark)"#8EDBFF" else "#126B9A"}!important}strong,b{color:${if(dark)"#FFF0C7" else "#17212B"}!important}.empty{padding:28px 12px;text-align:center;color:$muted}.empty span{font-size:12px}</style>'''
if a<0 or e<0: raise SystemExit('Ren CSS marker missing')
s=s[:a]+css+s[e+8:]; q.write_text(s)
# shared markdown CSS
q=B/'RovexMarkdown.kt'; s=q.read_text(); s=s.replace('table{display:table;max-width:100%;width:100%;overflow-wrap:anywhere}th,td{word-break:break-word}','table{overflow-wrap:anywhere}')
old='table{width:100%;border-collapse:collapse;margin:12px 0;background:#13212B;border-radius:10px;overflow:hidden}th,td{border:1px solid #334A5A;padding:8px;text-align:left;vertical-align:top}th{background:#1C3445;color:#A9DCFF}mark'
new='.table-wrap{width:100%;max-width:100%;overflow-x:auto;overflow-y:hidden;margin:12px 0;padding-bottom:2px;overscroll-behavior-x:contain;-webkit-overflow-scrolling:touch;scrollbar-gutter:stable}table{border-collapse:collapse;margin:0;background:#13212B;width:max-content;min-width:100%;max-width:none;table-layout:auto}th,td{box-sizing:border-box;border:1px solid #334A5A;padding:8px 9px;text-align:left;vertical-align:top;min-width:96px;max-width:280px;overflow-wrap:anywhere;word-break:normal}th{background:#1C3445;color:#B9F4EF;white-space:normal}mark'
if old not in s: raise SystemExit('Markdown CSS marker missing')
s=s.replace(old,new,1); q.write_text(s)
F('UPGRADE_V8.3.297.md').write_text('# v8.3.297 repair\n\nRetired AMOLED Dark; added Obsidian Night, touch-safe Frankenstein table scrolling, adaptive table sizing and responsive Markdown presentation.\n')
print('PATCH_297_APPLIED')
