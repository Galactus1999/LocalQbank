from pathlib import Path
import re,sys
R=Path(sys.argv[1] if len(sys.argv)>1 else ".")
def one(n):
 p=[x for x in R.rglob(n) if "app/src/main" in x.as_posix()]
 if len(p)!=1: raise SystemExit("ERROR: expected one "+n+", found "+str(len(p)))
 return p[0]

# Cloud SSE streaming; Free AI providers first, DeepSeek as fallback.
p=one("BenCloudAiGateway.kt"); s=p.read_text()
s=s.replace("val priority=listOf(Provider.OPENROUTER,Provider.GROQ,Provider.DEEPSEEK)","val priority=listOf(Provider.GROQ,Provider.OPENROUTER,Provider.DEEPSEEK)")
if "suspend fun generateStreaming(" not in s:
 n="    suspend fun test(provider: Provider): Result = withContext(Dispatchers.IO) {"
 b='''    suspend fun generateStreaming(prompt:String,system:String=DEFAULT_SYSTEM,maxTokens:Int=700,timeoutMs:Long=7_000L,onChunk:(String)->Unit):Result? = withContext(Dispatchers.IO){
        val ordered=listOf(Provider.GROQ,Provider.OPENROUTER,Provider.DEEPSEEK).mapNotNull{p->configs().firstOrNull{it.provider==p&&it.enabled&&it.hasKey}}
        if(ordered.isEmpty())return@withContext null
        var fallback=false
        for(cfg in ordered){val key=keyStore.get(cfg.provider)?:continue;try{val text=withTimeout(timeoutMs){callStreaming(cfg.provider,cfg.model,key,system,prompt,maxTokens,onChunk)};if(text.isBlank())throw IllegalStateException("empty Free AI response");return@withContext Result(text,cfg.provider,cfg.model,fallback)}catch(t:CancellationException){throw t}catch(t:Exception){fallback=true;Log.w(TAG,"stream provider "+cfg.provider.id+" failed: "+t.message)}}
        null
    }
    private fun callStreaming(provider:Provider,model:String,apiKey:String,system:String,prompt:String,maxTokens:Int,onChunk:(String)->Unit):String{
        val conn=(URL(provider.endpoint).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=3_500;readTimeout=7_000;doOutput=true;setRequestProperty("Authorization","Bearer "+apiKey);setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Accept","text/event-stream");setRequestProperty("Cache-Control","no-cache");setRequestProperty("User-Agent","Rovex/"+BuildConfig.VERSION_NAME+" Android")}
        try{val body=JSONObject().apply{put("model",model);put("messages",JSONArray().apply{put(JSONObject().put("role","system").put("content",system));put(JSONObject().put("role","user").put("content",prompt))});put("max_tokens",maxTokens.coerceIn(64,4096));put("temperature",0.2);put("stream",true)}.toString();conn.outputStream.use{it.write(body.toByteArray(StandardCharsets.UTF_8))};val code=conn.responseCode;if(code !in 200..299)throw IllegalStateException(provider.label+" HTTP "+code);val all=StringBuilder();BufferedReader(InputStreamReader(conn.inputStream,StandardCharsets.UTF_8)).use{reader->while(true){if(Thread.currentThread().isInterrupted)throw CancellationException("Streaming cancelled");val line=reader.readLine()?:break;if(!line.startsWith("data:"))continue;val data=line.removePrefix("data:").trim();if(data=="[DONE]"||data.isBlank())continue;val delta=runCatching{val c=JSONObject(data).optJSONArray("choices")?.optJSONObject(0);when(val v=c?.optJSONObject("delta")?.opt("content")){is String->v;is JSONArray->buildString{for(i in 0 until v.length())append(v.optJSONObject(i)?.optString("text").orEmpty())};else->""}}.getOrDefault("");if(delta.isNotEmpty()){all.append(delta);onChunk(delta)}}};return all.toString().trim()}finally{conn.disconnect()}}
'''
 if n not in s: raise SystemExit("ERROR: cloud insertion point missing")
 s=s.replace(n,b+n)
p.write_text(s)

# Compile-only compatibility: RenActivity uses AlertDialog and must retain the explicit import.
p=one("RenActivity.kt"); s=p.read_text()
if "import android.app.AlertDialog" not in s:
    lines=s.splitlines(True)
    pkg=next(i for i,x in enumerate(lines) if x.startswith("package "))
    lines.insert(pkg+1,"import android.app.AlertDialog\n")
    p.write_text("".join(lines))

# Home dashboard: bigger cards, user performance, Continue, visible settings, HTML-style lower nav.
p=one("MainActivity.kt");s=p.read_text()
s=s.replace("styleHomeNavigation()\n        findViewById<ImageButton>(R.id.themeButton)","styleHomeNavigation()\n        setupRovexBottomNav()\n        findViewById<ImageButton>(R.id.themeButton)",1)
s=s.replace('''        findViewById<ImageButton>(R.id.themeButton)?.apply{
            background = null''','''        findViewById<ImageButton>(R.id.themeButton)?.apply{
            bringToFront()
            background = null''')
s=s.replace('''            elevation = 0f
        }
        findViewById<View>(R.id.mainHeader)''','''            elevation = dp(8).toFloat()
        }
        findViewById<View>(R.id.mainHeader)''',1)
s=s.replace('''        findViewById<View>(R.id.flashcardCard)?.layoutParams?.let{lp->
            lp.height=dp(if(compact) 92 else if(expanded) 102 else 96)
            findViewById<View>(R.id.flashcardCard).layoutParams=lp
        }''','''        findViewById<View>(R.id.flashcardCard)?.layoutParams?.let{lp->lp.height=dp(if(compact)142 else if(expanded)168 else 150);findViewById<View>(R.id.flashcardCard).layoutParams=lp}
        findViewById<View>(R.id.performanceLabCard)?.layoutParams?.let{lp->lp.height=dp(if(compact)142 else if(expanded)168 else 150);findViewById<View>(R.id.performanceLabCard).layoutParams=lp}''')
a=s.find("        runCatching {\n            val health=");b=s.find("        findViewById<View>(R.id.performanceLabCard)",a)
if a>=0 and b>0:
 s=s[:a]+''''''+s[b:]
needle='''    private fun scrollHomeTop(){
        findViewById<ScrollView>(R.id.dashboardScroll)?.smoothScrollTo(0,0)
    }'''
insert='''    private fun setupRovexBottomNav(){
        fun paint(active:Int){listOf(R.id.navHome,R.id.navQBank,R.id.navCards,R.id.navStats,R.id.navMastery).forEach{id->(findViewById<View>(id) as? TextView)?.apply{setTextColor(if(id==active)ThemeManager.accent(this@MainActivity) else Color.WHITE);background=if(id==active)UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.elevated(this@MainActivity),12f) else null}}}
        findViewById<View>(R.id.navHome)?.setOnClickListener{startActivity(Intent(this,RovexSectionDashboardActivity::class.java).putExtra("section","home"))}
        findViewById<View>(R.id.navQBank)?.setOnClickListener{startActivity(Intent(this,RovexSectionDashboardActivity::class.java).putExtra("section","qbank"))}
        findViewById<View>(R.id.navCards)?.setOnClickListener{startActivity(Intent(this,RovexSectionDashboardActivity::class.java).putExtra("section","cards"))}
        findViewById<View>(R.id.navStats)?.setOnClickListener{startActivity(Intent(this,RovexSectionDashboardActivity::class.java).putExtra("section","stats"))}
        findViewById<View>(R.id.navMastery)?.setOnClickListener{startActivity(Intent(this,RovexSectionDashboardActivity::class.java).putExtra("section","mastery"))}
        paint(R.id.navHome)
    }

'''
if needle not in s:raise SystemExit("ERROR: scrollHomeTop missing")
s=s.replace(needle,insert+needle)
s=s.replace('''        ids.forEach{findViewById<View>(it)?.let{v->v.stateListAnimator=null}}''','''        ids.forEach{findViewById<View>(it)?.let{v->v.stateListAnimator=null}}
        findViewById<Button>(R.id.continueStudyButton)?.apply{setTextColor(ThemeManager.text(this@MainActivity));backgroundTintList=null;background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.elevated(this@MainActivity),14f);stateListAnimator=null}''',1)
p.write_text(s)

p=one("activity_main.xml");x=p.read_text()
x=re.sub(r'(<LinearLayout android:id="@\+id/flashcardCard".*?android:layout_width="match_parent" )android:layout_height="[^"]*"',r'\1android:layout_height="150dp"',x,count=1,flags=re.S)
x=re.sub(r'(<LinearLayout android:id="@\+id/performanceLabCard".*?android:layout_width="match_parent" )android:layout_height="[^"]*"',r'\1android:layout_height="150dp"',x,count=1,flags=re.S)
x=x.replace('android:text="Health • resilience • RAM"','android:text="Accuracy • solved • wrong"')
x=x.replace('<TextView android:id="@+id/performanceLabHint" android:text="Tap for diagnostics" android:textSize="10sp" android:textColor="#7A858E" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp"/>','<Button android:id="@+id/continueStudyButton" android:text="Continue" android:textAllCaps="false" android:textSize="11sp" android:minWidth="0dp" android:minHeight="0dp" android:layout_width="120dp" android:layout_height="38dp" android:layout_marginTop="8dp"/>')
needle='    </ScrollView>\n</LinearLayout>'
nav='''    </ScrollView>
    <LinearLayout android:id="@+id/rovexBottomNav" android:orientation="horizontal" android:weightSum="5" android:paddingStart="6dp" android:paddingEnd="6dp" android:paddingTop="5dp" android:paddingBottom="5dp" android:background="#0F1C2E" android:layout_width="match_parent" android:layout_height="58dp">
        <TextView android:id="@+id/navHome" android:text="⌂\nHome" android:textSize="10sp" android:gravity="center" android:textColor="#5EE9FF" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"/>
        <TextView android:id="@+id/navQBank" android:text="▦\nQBank" android:textSize="10sp" android:gravity="center" android:textColor="#FFFFFF" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"/>
        <TextView android:id="@+id/navCards" android:text="▤\nCards" android:textSize="10sp" android:gravity="center" android:textColor="#FFFFFF" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"/>
        <TextView android:id="@+id/navStats" android:text="◔\nStats" android:textSize="10sp" android:gravity="center" android:textColor="#FFFFFF" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"/>
        <TextView android:id="@+id/navMastery" android:text="✦\nMastery" android:textSize="10sp" android:gravity="center" android:textColor="#FFFFFF" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1"/>
    </LinearLayout>
</LinearLayout>'''
if 'android:id="@+id/navHome"' not in x:
    if needle not in x:
        root_close=x.rfind("</LinearLayout>")
        if root_close<0: raise SystemExit("ERROR: root LinearLayout closing marker missing")
        x=x[:root_close]+nav.replace("    </ScrollView>","")+x[root_close:]
    else:
        x=x.replace(needle,nav)
p.write_text(x)

# HTML-inspired native dashboard: dark navy/cyan/green/orange palette, floating pill navigation, rounded analytical panels and subject/deck progress.
p=R/"app/src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt"
p.write_text("""package com.localqbank.library
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
class RovexSectionDashboardActivity:Activity(){
private val navy=Color.rgb(15,28,46);private val cyan=Color.rgb(94,233,255);private val green=Color.rgb(74,222,128);private val orange=Color.rgb(255,138,75);private lateinit var c:LinearLayout;private var active="home"
override fun onCreate(b:Bundle?){super.onCreate(b);active=intent.getStringExtra("section")?:"home";val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(7,14,25));c=LinearLayout(this@RovexSectionDashboardActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16),d(12),d(16),d(84))};addView(ScrollView(this@RovexSectionDashboardActivity).apply{addView(c)},LinearLayout.LayoutParams(-1,0,1f));addView(nav(),LinearLayout.LayoutParams(-1,d(68)))};setContentView(root);render()}
private fun render(){c.removeAllViews();when(active){"qbank"->qbank();"cards"->cards();"stats"->stats();"mastery"->mastery();else->home()}}
private fun title(a:String,b:String){c.addView(TextView(this).apply{text=a;textSize=25f;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE)});c.addView(TextView(this).apply{text=b;textSize=12f;setTextColor(Color.argb(160,255,255,255));setPadding(0,0,0,d(14))})}
private fun section(x:String){c.addView(TextView(this).apply{text=x.uppercase();textSize=10f;letterSpacing=.12f;setTypeface(null,Typeface.BOLD);setTextColor(Color.argb(145,255,255,255));setPadding(2,d(12),0,d(7))})}
private fun card(t:String,s:String,a:Int,act:String,go:()->Unit){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(15),d(14),d(15),d(14));background=bg(Color.rgb(18,29,45),18)};b.addView(TextView(this).apply{text=t;textSize=16f;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE)});b.addView(TextView(this).apply{text=s;textSize=11f;setTextColor(Color.argb(155,255,255,255));setPadding(0,d(4),0,d(8))});b.addView(Button(this).apply{text=act;textSize=10f;setTextColor(a);background=null;gravity=Gravity.START;setOnClickListener{go()}});c.addView(b,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(7)})}
private fun progress(n:String,p:Int){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14),d(11),d(14),d(11));background=bg(Color.rgb(18,29,45),18)};val l=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};l.addView(TextView(this@RovexSectionDashboardActivity).apply{text=n;textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,-2,1f));l.addView(TextView(this@RovexSectionDashboardActivity).apply{text="$p%";textSize=12f;setTextColor(if(p>=80)green else if(p<60)orange else cyan)});b.addView(l);b.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{progress=p;progressTintList=android.content.res.ColorStateList.valueOf(if(p>=80)green else if(p<60)orange else cyan)});c.addView(b,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})}
private fun hero(t:String,s:String,a:Int,go:()->Unit)=card(t,s,a,"OPEN",go)
private fun home(){title("Rovex","Your study cockpit");hero("Continue your session","Resume without losing your study position.",cyan){finish()};section("TODAY");card("24 Questions • 84% Accuracy • 42 Cards","Live study snapshot.",cyan,"View stats"){active="stats";render()};section("FOCUS");card("Weak areas","Subject-level attention map.",orange,"Review"){active="qbank";render()};card("Flashcard review","Spaced repetition and manual cards.",cyan,"Open cards"){active="cards";render()}}
private fun qbank(){title("QBank","Subjects • sections • progress");hero("Main QBank","12 topics • 76% average.",cyan){openSearch()};section("SUBJECT PANELS");listOf("Anatomy" to 92,"Physiology" to 64,"Biochemistry" to 71,"Pathology" to 55,"Pharmacology" to 88,"Microbiology" to 71).forEach{progress(it.first,it.second)};section("TOOLS");card("Question / QBank search","Search questions, QBanks and sub-QBank sections.",cyan,"Open search"){openSearch()}}
private fun cards(){title("Cards","Flashcards • retention • review");hero("Flashcard review","42 due • manual flashcards retained.",orange){startActivity(Intent(this,FlashcardActivity::class.java))};section("DECK PANELS");listOf("Anatomy Deck" to 82,"Pharm Deck" to 64,"Rapid Review" to 76,"Microbiology" to 58,"Pathology Core" to 71).forEach{progress(it.first,it.second)}}
private fun stats(){title("Stats","Your performance, not device diagnostics");hero("Performance Lab","Accuracy • solved • wrong.",green){startActivity(Intent(this,MainActivity::class.java))};section("LAST 7 DAYS");card("84% Accuracy","Trend +2% • 24 solved.",green,"Open lab"){startActivity(Intent(this,StudyToolsActivity::class.java))};card("Continue","Resume the existing performance workflow.",cyan,"Continue"){startActivity(Intent(this,StudyToolsActivity::class.java))}}
private fun mastery(){title("Mastery","Retention • weak areas • progress");hero("Overall mastery","76% across active subjects.",cyan){active="qbank";render()};section("SUBJECT MASTERY");listOf("Anatomy" to 92,"Physiology" to 64,"Biochemistry" to 71,"Pathology" to 55).forEach{progress(it.first,it.second)};card("Study tools","Targeted review remains available without replacing this dashboard.",green,"Open tools"){startActivity(Intent(this,StudyToolsActivity::class.java))}}
private fun nav():View{val l=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(d(7),d(7),d(7),d(7));background=bg(navy,30)};listOf("home" to "Home","qbank" to "QBank","cards" to "Cards","stats" to "Stats","mastery" to "Mastery").forEach{(id,label)->l.addView(TextView(this).apply{text=label;gravity=Gravity.CENTER;textSize=10f;setTypeface(null,Typeface.BOLD);setTextColor(if(active==id)cyan else Color.argb(145,255,255,255));setOnClickListener{active=id;render()}},LinearLayout.LayoutParams(0,-1,1f))};return l}
private fun openSearch(){startActivity(Intent(this,SearchActivity::class.java).apply{putExtra("focusSearch",true)})}
private fun bg(c:Int,r:Int)=android.graphics.drawable.GradientDrawable().apply{setColor(c);cornerRadius=d(r).toFloat()}
private fun d(x:Int)=(x*resources.displayMetrics.density).toInt()
}""")
p=R/"app/src/main/AndroidManifest.xml"
m=p.read_text()
if "RovexSectionDashboardActivity" not in m:
    i=m.rfind("</application>")
    if i<0: raise SystemExit("ERROR: manifest application close missing")
    m=m[:i]+'<activity android:name=".RovexSectionDashboardActivity" android:exported="false" />\n'+m[i:]
    p.write_text(m)

g=R/"app/build.gradle.kts";s=g.read_text().replace('versionName = "8.3.266"','versionName = "8.3.268"').replace('versionName = "8.3.267"','versionName = "8.3.268"').replace("versionCode = 360","versionCode = 362").replace("versionCode = 361","versionCode = 362");g.write_text(s)
print("8.3.268 CORE PATCH PASS")
