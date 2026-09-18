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
 s=s[:a]+'''        refreshUserPerformanceLab()
        findViewById<TextView>(R.id.performanceLabSummary)?.text="Accuracy • solved • wrong"
'''+s[b:]
needle='''    private fun scrollHomeTop(){
        findViewById<ScrollView>(R.id.dashboardScroll)?.smoothScrollTo(0,0)
    }'''
insert='''    private fun setupRovexBottomNav(){
        fun paint(active:Int){listOf(R.id.navHome,R.id.navQBank,R.id.navCards,R.id.navStats,R.id.navMastery).forEach{id->(findViewById<View>(id) as? TextView)?.apply{setTextColor(if(id==active)ThemeManager.accent(this@MainActivity) else Color.WHITE);background=if(id==active)UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.elevated(this@MainActivity),12f) else null}}}
        findViewById<View>(R.id.navHome)?.setOnClickListener{paint(R.id.navHome);scrollHomeTop()}
        findViewById<View>(R.id.navQBank)?.setOnClickListener{paint(R.id.navQBank);openSearch()}
        findViewById<View>(R.id.navCards)?.setOnClickListener{paint(R.id.navCards);startActivity(Intent(this,FlashcardActivity::class.java))}
        findViewById<View>(R.id.navStats)?.setOnClickListener{paint(R.id.navStats);PerformanceManager.submit{val refs=PerformanceManager.refs(applicationContext);runOnUiThread{if(!isFinishing&&!isDestroyed)showInteractiveAnalytics(refs,7)}}}
        findViewById<View>(R.id.navMastery)?.setOnClickListener{paint(R.id.navMastery);startActivity(Intent(this,StudyToolsActivity::class.java))}
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
if needle not in x:raise SystemExit("ERROR: XML closing marker missing")
x=x.replace(needle,nav);p.write_text(x)

g=R/"app/build.gradle.kts";s=g.read_text().replace('versionName = "8.3.266"','versionName = "8.3.268"').replace('versionName = "8.3.267"','versionName = "8.3.268"').replace("versionCode = 360","versionCode = 362").replace("versionCode = 361","versionCode = 362");g.write_text(s)
print("8.3.268 CORE PATCH PASS")
