from pathlib import Path
import re,sys,subprocess,shutil,zipfile,hashlib

zips=sorted(Path(".").glob("*.zip"))
if len(zips)!=1: raise SystemExit(f"expected one source zip, found {len(zips)}")
old=zips[0]
work=Path("work"); shutil.rmtree(work,ignore_errors=True); work.mkdir()
with zipfile.ZipFile(old) as z: z.extractall(work)
roots=list(work.rglob("settings.gradle.kts"))
if not roots: raise SystemExit("settings.gradle.kts not found")
p=roots[0].parent
ren=p/"app/src/main/java/com/localqbank/library/RenActivity.kt"
main=p/"app/src/main/java/com/localqbank/library/MainActivity.kt"
xml=p/"app/src/main/res/layout/activity_main.xml"
perf=p/"app/src/main/java/com/localqbank/library/RovexPerformanceLabView.kt"
gw=p/"app/src/main/java/com/localqbank/library/BenCloudAiGateway.kt"

s=ren.read_text()
hs=s.index("        val header = LinearLayout(this)"); he=s.index("        root.addView(header)",hs)+len("        root.addView(header)")
header='''        val header = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text="‹"; textSize=30f; gravity=Gravity.CENTER; setTextColor(ThemeManager.text(this@RenActivity)); setOnClickListener{finish()} }, LinearLayout.LayoutParams(dp(40),dp(42)))
        header.addView(Space(this), LinearLayout.LayoutParams(0,1,1f))
        header.addView(TextView(this).apply { text="FREE AI"; textSize=10f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(ThemeManager.text(this@RenActivity)); background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),10f); setPadding(dp(9),0,dp(9),0); setOnClickListener{showFreeAiMenu()} },LinearLayout.LayoutParams(dp(78),dp(42)).apply{setMargins(0,0,dp(5),0)})
        header.addView(TextView(this).apply { text="MODEL LAB"; textSize=10f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(ThemeManager.text(this@RenActivity)); background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),10f); setPadding(dp(9),0,dp(9),0); setOnClickListener{startActivity(Intent(this@RenActivity,BenModelLabActivity::class.java))} },LinearLayout.LayoutParams(dp(94),dp(42)))
        root.addView(header)'''
s=s[:hs]+header+s[he:]
s=re.sub(r'\s*contextCard = buildFrankensteinContextCard\(\)\n\s*root\.addView\(contextCard,[^\n]+\)','',s,count=1)
s=re.sub(r'\s*root\.addView\(liveStatus, LinearLayout\.LayoutParams\(-1,dp\(28\)\)\)','\n        liveStatus.visibility=View.GONE',s,count=1)
fs=s.index("        val core = TextView(this)"); fe=s.index("        root.addView(row)",fs)+len("        root.addView(row)")
footer='''        fun action(label:String, click:()->Unit)=TextView(this).apply{text=label;textSize=11.5f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@RenActivity));background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),11f);setOnClickListener{click()}}
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        row.addView(action("Dr.F"){respond(input,answer,modelPill)},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(0,0,dp(5),0)})
        row.addView(action("Dr.F + AI"){runCombinedCore(input,answer,modelPill)},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(0,0,dp(5),0)})
        row.addView(action("G.search"){startActivity(Intent(this@RenActivity,GoogleSearchActivity::class.java))},LinearLayout.LayoutParams(0,dp(38),1f))
        root.addView(row)'''
s=s[:fs]+footer+s[fe:]
s=s.replace('LinearLayout.LayoutParams(-1,dp(68)).apply{setMargins(0,dp(2),0,dp(6))}','LinearLayout.LayoutParams(-1,dp(62)).apply{setMargins(0,dp(2),0,dp(5))}',1)
clean='''    private fun cleanAiAnswer(raw:String):String{
        var x=raw.replace(Regex("(?is)<style[^>]*>.*?</style>"),"").replace(Regex("(?is)<script[^>]*>.*?</script>"),"")
        x=Regex("(?s)(?:body|h[1-6]|p|blockquote|code,pre|pre|table|th,td)\\s*\\{[^}]*\\}").replace(x,"")
        x=x.replace(Regex("(?m)^\\s*(?:font-family|font-size|line-height|margin|padding|color|background|border(?:-[a-z]+)?)\\s*:[^\\n}]+[;}]?\\s*$"),"")
        return x.replace(Regex("\\n{3,}"),"\\n\\n").trim()
    }

'''
if 'private fun cleanAiAnswer' not in s: s=s.replace('    private fun currentPromptForContext(): String',clean+'    private fun currentPromptForContext(): String',1)
s=s.replace('answer.text=rovexMarkdownToSpanned(insight.body)','answer.text=rovexMarkdownToSpanned(cleanAiAnswer(insight.body))')
s=s.replace('answer.text=rovexMarkdownToSpanned(result.text)','answer.text=rovexMarkdownToSpanned(cleanAiAnswer(result.text))')
s=s.replace('maxTokens=1600)','maxTokens=900,timeoutMs=9_000L)',1)
ren.write_text(s)

perf.write_text('''package com.localqbank.library
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
class RovexPerformanceLabView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):View(context,attrs){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG);private var accuracy=0;private var solved=0;private var wrong=0
 fun setUserStats(a:Int,s:Int,w:Int){accuracy=a.coerceIn(0,100);solved=s.coerceAtLeast(0);wrong=w.coerceAtLeast(0);invalidate()}
 fun setScores(a:Int,s:Int,w:Int){}
 override fun onDraw(c:Canvas){val d=resources.displayMetrics.density;val w=width.toFloat();val h=height.toFloat();if(w<=0||h<=0)return;p.color=ThemeManager.panel(context);c.drawRoundRect(0f,0f,w,h,16*d,16*d,p);p.color=ThemeManager.text(context);p.textSize=10.5f*d;p.isFakeBoldText=true;c.drawText("YOUR PERFORMANCE",10*d,17*d,p);p.isFakeBoldText=false;p.color=ThemeManager.muted(context);p.textSize=8*d;c.drawText("$solved solved • $wrong wrong",10*d,30*d,p);val cx=28*d;val cy=57*d;val r=20*d;p.style=Paint.Style.STROKE;p.strokeWidth=5*d;p.strokeCap=Paint.Cap.ROUND;p.color=if(ThemeManager.isDark(context))0xFF27343D.toInt() else 0xFFE0E8EB.toInt();c.drawCircle(cx,cy,r,p);p.color=ThemeManager.accent(context);c.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,accuracy*3.6f,false,p);p.style=Paint.Style.FILL;p.textAlign=Paint.Align.CENTER;p.color=ThemeManager.text(context);p.textSize=11*d;p.isFakeBoldText=true;c.drawText("$accuracy%",cx,cy+4*d,p);p.isFakeBoldText=false;p.textSize=7*d;p.color=ThemeManager.muted(context);c.drawText("ACCURACY",cx,cy+30*d,p);p.textAlign=Paint.Align.LEFT;p.textSize=8*d;c.drawText("Accuracy",50*d,51*d,p);p.color=ThemeManager.accent(context);c.drawRoundRect(50*d,56*d,w-10*d,62*d,3*d,3*d,p);p.color=if(ThemeManager.isDark(context))0xFF26343C.toInt() else 0xFFE2EAED.toInt();c.drawRoundRect(50*d,69*d,w-10*d,75*d,3*d,3*d,p);p.color=0xFFD65F5F.toInt();val frac=wrong.toFloat()/kotlin.math.max(1,solved);c.drawRoundRect(50*d,69*d,50*d+(w-60*d)*frac.coerceIn(0f,1f),75*d,3*d,3*d,p);p.color=ThemeManager.muted(context);p.textSize=7.5f*d;c.drawText("Wrong",50*d,84*d,p)}
}''')

ms=main.read_text()
if 'refreshUserPerformanceLab()' not in ms:
 m=re.search(r'\bsetContentView\([^\n]+\)',ms);assert m
 ms=ms[:m.end()]+'\n        refreshUserPerformanceLab()'+ms[m.end():]
 method='''

 private fun refreshUserPerformanceLab(){PerformanceManager.submit{val refs=PerformanceManager.lightRefs(applicationContext);val progress=PerformanceManager.progress(applicationContext);var solved=0;var correct=0;var wrong=0;refs.forEach{r->when(progress.record(r.stableKey)?.status){"correct"->{solved++;correct++};"wrong"->{solved++;wrong++}}};val acc=if(solved==0)0 else correct*100/solved;runOnUiThread{findViewById<RovexPerformanceLabView>(R.id.performanceLabViz)?.setUserStats(acc,solved,wrong)}}}
'''
 i=ms.rfind('\n}');ms=ms[:i]+method+ms[i:];main.write_text(ms)

x=xml.read_text().replace('android:layout_height="104dp"','android:layout_height="76dp"',1)
x=re.sub(r'\s*<com\.localqbank\.library\.RovexHeaderTitanicView[^>]*/>','''\n        <com.localqbank.library.RovexHeaderCosmicView android:id="@+id/headerCosmic" android:layout_width="96dp" android:layout_height="68dp" android:layout_gravity="end|center_vertical" android:clickable="false" android:focusable="false" android:importantForAccessibility="no" />''',x,count=1)
x=x.replace('android:layout_width="118dp" android:layout_height="96dp"','android:layout_width="104dp" android:layout_height="78dp"',1).replace('android:layout_width="158dp" android:layout_height="128dp"','android:layout_width="126dp" android:layout_height="86dp"',1)
x=re.sub(r'android:text="Search[^"]*"','android:text="Search QBanks • sections • questions"',x,count=1);xml.write_text(x)

(p/"app/src/main/java/com/localqbank/library/RovexHeaderCosmicView.kt").write_text('''package com.localqbank.library
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
class RovexHeaderCosmicView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):View(context,attrs){private val p=Paint(Paint.ANTI_ALIAS_FLAG);private var t=0f;override fun onDraw(c:Canvas){val w=width.toFloat();val h=height.toFloat();if(w<=0||h<=0)return;p.color=if(ThemeManager.isDark(context))0xFF080A16.toInt() else 0xFFF2F5F8.toInt();c.drawRect(0f,0f,w,h,p);val ex=w*.70f;val ey=h*.56f;val er=minOf(w,h)*.30f;p.shader=RadialGradient(ex-er*.35f,ey-er*.4f,er,intArrayOf(0xFF64B7FF.toInt(),0xFF176AA5.toInt(),0xFF0B3156.toInt()),null,Shader.TileMode.CLAMP);c.drawCircle(ex,ey,er,p);p.shader=null;p.color=0xFF3F9D62.toInt();c.drawOval(ex-er*.45f,ey-er*.15f,ex+er*.05f,ey+er*.28f,p);c.drawOval(ex+er*.02f,ey-er*.55f,ex+er*.45f,ey-er*.05f,p);t+=.04f;val sx=w*.34f;val sy=h*(.35f+.04f*sin(t));p.color=ThemeManager.text(context);c.drawOval(sx-16,sy-3,sx+12,sy+5,p);p.color=ThemeManager.accent(context);c.drawCircle(sx-4,sy+1,2.2f,p);postInvalidateOnAnimation()}}
''')
(p/"app/src/main/java/com/localqbank/library/RovexHeaderTitanicView.kt").unlink(missing_ok=True)

settings=p/"app/src/main/java/com/localqbank/library/SettingsScreen.kt";st=settings.read_text()
if 'BenPdfLibraryActivity' not in st:
 needle='startActivity(Intent(activity, BenPdfImportActivity::class.java))';pos=st.find(needle);assert pos>=0;e=st.find('\n',pos);st=st[:e]+'\n                menu.add("Imported PDFs", "Open and manage PDFs already imported for Ben") { activity.startActivity(Intent(activity, BenPdfLibraryActivity::class.java)) }'+st[e:];settings.write_text(st)
(p/"app/src/main/java/com/localqbank/library/BenPdfLibraryActivity.kt").write_text('''package com.localqbank.library
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.widget.*
import java.io.File
class BenPdfLibraryActivity:Activity(){override fun onCreate(b:Bundle?){super.onCreate(b);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,16)};val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};bar.addView(TextView(this).apply{text="‹";textSize=30f;setOnClickListener{finish()}},LinearLayout.LayoutParams(42,48));bar.addView(TextView(this).apply{text="Imported PDFs";textSize=20f;setTypeface(null,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,48,1f));root.addView(bar);val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root);refresh(list)};private fun refresh(list:LinearLayout){list.removeAllViews();val d=File(filesDir,"ben_pdf_corpus");val fs=d.listFiles{f->f.extension.equals("pdf",true)}?.sortedByDescending{it.lastModified()}.orEmpty();if(fs.isEmpty()){list.addView(TextView(this).apply{text="No PDFs imported yet.";textSize=15f;setPadding(8,24,8,24)});return};fs.forEach{pdf->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};row.addView(TextView(this).apply{text=pdf.name.substringAfter("_").removeSuffix(".pdf").replace("_"," ");textSize=14f},LinearLayout.LayoutParams(0,58,1f));row.addView(TextView(this).apply{text="OPEN";gravity=Gravity.CENTER;setOnClickListener{open(pdf)}},LinearLayout.LayoutParams(68,44));row.addView(TextView(this).apply{text="DELETE";gravity=Gravity.CENTER;setOnClickListener{pdf.delete();File(pdf.parentFile,pdf.nameWithoutExtension+".txt").delete();refresh(list)}},LinearLayout.LayoutParams(72,44));list.addView(row)}};private fun open(file:File){val d=android.app.Dialog(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)};val image=ImageView(this).apply{adjustViewBounds=true};var page=0;fun render(){runCatching{ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{pfd->PdfRenderer(pfd).use{r->r.openPage(page).use{pg->val bm=Bitmap.createBitmap(pg.width,pg.height,Bitmap.Config.ARGB_8888);pg.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image.setImageBitmap(bm)}}}}};val controls=LinearLayout(this).apply{gravity=Gravity.CENTER};controls.addView(TextView(this).apply{text="‹";textSize=26f;setPadding(24,4,24,4);setOnClickListener{if(page>0){page--;render()}}});controls.addView(TextView(this).apply{text="›";textSize=26f;setPadding(24,4,24,4);setOnClickListener{val n=runCatching{ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{PdfRenderer(it).pageCount}}.getOrDefault(page+1);if(page+1<n){page++;render()}}});box.addView(image,LinearLayout.LayoutParams(-1,0,1f));box.addView(controls);d.setContentView(box);d.show();d.window?.setLayout(-1,-1);render()}}''')

mmf=p/"app/src/main/AndroidManifest.xml";mm=mmf.read_text()
if 'BenPdfLibraryActivity' not in mm:mm=mm.replace('</application>','<activity android:name=".BenPdfLibraryActivity" />\n</application>',1)
mmf.write_text(mm)

grad=p/"app/build.gradle.kts";g=grad.read_text();m=re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"',g);c=re.search(r'versionCode\s*=\s*([0-9]+)',g);assert m and c and m.group(1)=="8.3.264" and int(c.group(1))==358;g=g[:m.start(1)]+"8.3.265"+g[m.end(1):];c2=re.search(r'versionCode\s*=\s*([0-9]+)',g);g=g[:c2.start(1)]+"359"+g[c2.end(1):];grad.write_text(g)

gs=gw.read_text().replace('timeoutMs: Long = 35_000L','timeoutMs: Long = 9_000L').replace('maxTokens: Int = 1200','maxTokens: Int = 900').replace('val ordered = configs().filter { it.enabled && it.hasKey }','val priority=listOf(Provider.OPENROUTER,Provider.GROQ,Provider.DEEPSEEK)\n        val ordered=priority.mapNotNull{p->configs().firstOrNull{it.provider==p&&it.enabled&&it.hasKey}}').replace('connectTimeout = 12_000','connectTimeout = 5_000').replace('readTimeout = 30_000','readTimeout = 8_000');gw.write_text(gs)

print("OK")
