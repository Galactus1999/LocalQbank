package com.localqbank.library

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.*
import kotlin.math.roundToInt
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity: AppCompatActivity(){
    private val mainViewModel: MainViewModel by viewModels { (application as LocalQBankApplication).appContainer.mainViewModelFactory() }
    private var isVisible = false
    private var fullyDrawnReported = false
    private val stateListener:()->Unit={if(!isFinishing && !isDestroyed && isVisible)runOnUiThread{mainViewModel.refresh();refreshOpenAnalyticsDialog()}}
    private fun dp(value:Int):Int = (value * resources.displayMetrics.density).roundToInt()
    private var analyticsDialog: Dialog? = null
    private var analyticsPeriod: Int = 0
    private var libraryAdapter: SourceAdapter? = null
    private val uiPreferences by lazy { MainUiPreferences(this) }
    private val htmlImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            startActivity(Intent(this, HtmlImportActivity::class.java).putParcelableArrayListExtra("uris", ArrayList(uris)))
        }
    }
    override fun onCreate(b:Bundle?){super.onCreate(b);AppManagers.initialize(applicationContext)
        SystemUi.immersive(this)
        setContentView(R.layout.activity_main);applyResponsiveHomeLayout(); AppState.register(stateListener)
        refreshUserPerformanceLab()
        installHomeSwipeToFlashcards()
        TransitionCoordinator.install(this)
        styleHeaderControls()
        styleHomeNavigation()
        findViewById<ImageButton>(R.id.themeButton)?.setOnClickListener{startActivity(Intent(this,SettingsActivity::class.java))};findViewById<View>(R.id.homeSearchCard)?.setOnClickListener{openSearch()};findViewById<View>(R.id.renCard)?.setOnClickListener{startActivity(Intent(this,RenActivity::class.java))}
        renderImmediateShell()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.uiState.collect { state -> applyMainUiState(state) }
            }
        }
        mainViewModel.refreshSourcesFast()
        mainViewModel.refresh()
        window.decorView.postDelayed({showRecoveryPrompt()},500)}
    override fun onStart(){ super.onStart(); isVisible = true }
    override fun onResume(){super.onResume(); mainViewModel.prepareForDisplay(); renderImmediateShell(); mainViewModel.refreshSourcesFast(); mainViewModel.refresh()}
    override fun onPause(){ super.onPause() }
    override fun onStop(){ isVisible = false; mainViewModel.flushBackup(); super.onStop() }
    private fun installHomeSwipeToFlashcards(){
        val scroll=findViewById<ScrollView>(R.id.dashboardScroll) ?: return
        var downX=0f
        var downY=0f
        var downTime=0L
        scroll.setOnTouchListener { _,event ->
            when(event.actionMasked){
                MotionEvent.ACTION_DOWN -> {
                    downX=event.x; downY=event.y; downTime=android.os.SystemClock.uptimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val dx=event.x-downX
                    val dy=event.y-downY
                    val elapsed=(android.os.SystemClock.uptimeMillis()-downTime).coerceAtLeast(1L)
                    val velocityX=kotlin.math.abs(dx)/(elapsed/1000f)
                    if(dx < -dp(90).toFloat() && kotlin.math.abs(dx) > kotlin.math.abs(dy)*1.25f && velocityX>350f){
                        startActivity(Intent(this@MainActivity,FlashcardActivity::class.java))
                    }
                }
            }
            false
        }
    }

    private fun showRecoveryPrompt(){
        if (isFinishing || isDestroyed || !ResilienceManager.shouldOfferRecovery(this)) return
        val recovery = ResilienceManager.recovery(this) ?: return
        AlertDialog.Builder(this)
            .setTitle("Resume protected study session?")
            .setMessage("Rovex saved question ${recovery.position + 1} before the previous interruption. Your QBank data was preserved.")
            .setNegativeButton("Later") { _, _ -> ResilienceManager.dismissRecoveryOffer(this) }
            .setPositiveButton("Resume") { _, _ ->
                ResilienceManager.dismissRecoveryOffer(this)
                val intent = Intent(this, QuizActivity::class.java).apply {
                    putExtra("testId", recovery.testId)
                    putExtra("position", recovery.position)
                    putExtra("sessionLabel", recovery.session)
                    if (recovery.stableKey.isNotBlank()) putExtra("questionId", recovery.stableKey.substringAfterLast(':').toLongOrNull() ?: -1L)
                }
                startActivity(intent)
            }.show()
    }

    private fun refreshOpenAnalyticsDialog(){
        val dialog=analyticsDialog
        if(dialog!=null && dialog.isShowing && !isFinishing && !isDestroyed){
            PerformanceManager.submit {
                val refs = PerformanceManager.refs(applicationContext)
                runOnUiThread { if (!isFinishing && !isDestroyed) runCatching { showInteractiveAnalytics(refs, analyticsPeriod) } }
            }
        }
    }
    private fun styleHeaderControls(){
        styleHomeNavigation()
        findViewById<ImageButton>(R.id.themeButton)?.apply{
            background = null
            backgroundTintList = null
            setImageResource(R.drawable.ic_settings)
            imageTintList = android.content.res.ColorStateList.valueOf(ThemeManager.text(this@MainActivity))
            contentDescription = "Settings and app menu"
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(8),dp(8),dp(8),dp(8))
            minimumWidth = 0
            minimumHeight = 0
            elevation = 0f
        }
        findViewById<View>(R.id.mainHeader)?.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        findViewById<View>(R.id.renCard)?.background = UiDrawableUtils.roundedDrawable(this, ThemeManager.explanationBg(this), 16f)
        findViewById<View>(R.id.renCard)?.elevation = dp(1).toFloat()
        findViewById<RovexHeaderWordmarkView>(R.id.appLogoText)?.refreshTheme()
        findViewById<View>(R.id.searchChase)?.bringToFront()
        // Add QBank now lives beside the "Dashboard" heading on the light body background,
        // so it needs a solid dark pill instead of the white-on-header treatment it used to have.
        findViewById<Button>(R.id.addButton)?.apply{
            backgroundTintList=null
            background=GradientDrawable().apply{setColor(if(ThemeManager.isDark(this@MainActivity)) Color.rgb(48,105,145) else Color.rgb(35,92,123));cornerRadius=20f*resources.displayMetrics.density}
            setTextColor(Color.WHITE); minHeight=dp(40); isAllCaps=false
            setPadding(dp(16),dp(2),dp(16),dp(2))
            elevation=dp(2).toFloat()
        }
    }

    private fun openSearch(){
        startActivity(Intent(this,SearchActivity::class.java).putExtra("focusSearch",true))
    }

    private fun scrollHomeTop(){
        findViewById<ScrollView>(R.id.dashboardScroll)?.smoothScrollTo(0,0)
    }

    /** Paint the usable home shell immediately; expensive QBank analytics arrive later. */
    private fun renderImmediateShell(){
        if (isFinishing || isDestroyed) return
        styleHeaderControls()
        styleHomeNavigation()
        findViewById<TextView>(R.id.stats)?.text="Loading your QBank library in the background…"
        findViewById<View>(R.id.dashboardRoot)?.background=ThemeManager.backgroundDrawable(this)
    }

    /**
     * The Home QBank list has its own fast visibility path. A secondary dashboard metric must
     * never be able to suppress the primary library UI after an import.
     */
    private fun applyMainUiState(state: MainUiState) {
        if (isFinishing || isDestroyed) return
        state.dashboard?.let { model ->
            runCatching { renderUi(model.refs, model.sources, model.analytics, model.bookmarks, model.sourceProgress) }
                .onFailure { Toast.makeText(this, "Dashboard refresh failed. Your data is safe.", Toast.LENGTH_SHORT).show() }
        }
        publishQBankSources(state.sources, state.sourceProgress, state.resumeBySource)
    }

    private fun publishQBankSources(sources: List<Source>, sourceProgress: Map<Long, ProgressSummary> = emptyMap(), resumeBySource: Map<Long, MainRepository.ResumeTarget> = mainViewModel.uiState.value.resumeBySource) {
        if (isFinishing || isDestroyed) return
        val list = findViewById<RecyclerView>(R.id.libraryList) ?: return
        AppManagers.runtime.configureRecyclerView(list, fixedSize = false)
        // Main QBank library is inside the dashboard ScrollView, so it still needs its own
        // concrete layout manager. Without this, RecyclerView can hold items but renders none.
        if (list.layoutManager == null) list.layoutManager = LinearLayoutManager(this)
        if (libraryAdapter == null) {
            libraryAdapter = SourceAdapter(sources, resumeBySource, sourceProgress, { source ->
                AppManagers.qbankLoading.warmSource(source.id)
                startActivity(Intent(this, TestListActivity::class.java).putExtra("sourceId", source.id).putExtra("sourceName", source.fileName))
            }, { source -> showEditSourceDialog(source) }, { sourceId ->
                AlertDialog.Builder(this).setTitle("Delete QBank?").setMessage("Remove this QBank and all its imported questions from Rovex?").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _, _ ->
                    mainViewModel.deleteSource(sourceId)
                    Toast.makeText(this,"QBank deleted",Toast.LENGTH_SHORT).show()
                }.show()
            })
            list.adapter = libraryAdapter
        } else {
            libraryAdapter?.updateItems(sources, sourceProgress, resumeBySource)
        }
        findViewById<TextView>(R.id.stats)?.text = if (sources.isEmpty()) "No QBanks added yet" else "${sources.size} QBank${if (sources.size == 1) "" else "s"}"
        reportInitialUsableState()
    }

    /** Report TTFD after the Home shell and QBank library have been published. */
    private fun reportInitialUsableState() {
        if (fullyDrawnReported || isFinishing || isDestroyed) return
        window.decorView.post {
            if (fullyDrawnReported || isFinishing || isDestroyed) return@post
            android.os.Trace.beginSection("Rovex.MainActivity.firstUsable")
            try {
                fullyDrawnReported = true
                reportFullyDrawn()
            } finally {
                android.os.Trace.endSection()
            }
        }
    }

    private fun renderUi(refs: List<QuestionRef>, sources: List<Source>, snapshot: AnalyticsSnapshot, bookmarks: Map<String, Int>, sourceProgress: Map<Long, ProgressSummary>) {
        android.os.Trace.beginSection("Rovex.MainActivity.renderUi")
        try {
        findViewById<View>(R.id.dashboardRoot)?.background=ThemeManager.backgroundDrawable(this@MainActivity)
        val total=snapshot.total; val solved=snapshot.correct; val wrong=snapshot.wrong
        val attempted=snapshot.attempted; val accuracy=snapshot.accuracy; val due=snapshot.due
        findViewById<TextView>(R.id.todaySolvedCount)?.apply {
            val value = snapshot.todaySolved.toString()
            text = value
            isSingleLine = true
            ellipsize = null
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = when { value.length >= 9 -> 14f; value.length >= 7 -> 17f; value.length >= 5 -> 21f; value.length >= 4 -> 25f; else -> 30f }
            scaleX = 1f
            minimumWidth = 0
        }

        findViewById<TextView>(R.id.studyMenuSub)?.apply{text="Revision • PYQ • tests • weak areas";visibility=View.VISIBLE}
        findViewById<TextView>(R.id.studyMenuTitle)?.text="Study Workbench"
        findViewById<TextView>(R.id.studyMenuTitle)?.textSize=20f
        findViewById<View>(R.id.todaySolvedCard)?.setOnClickListener{startTodaySolved(refs)}
        findViewById<View>(R.id.studyMenuCard)?.setOnClickListener{startActivity(Intent(this,StudyToolsActivity::class.java))}
        findViewById<View>(R.id.flashcardCard)?.setOnClickListener{startActivity(Intent(this@MainActivity,FlashcardActivity::class.java))}
        refreshHomeFlashcardSummary()
        runCatching {
            val health=AppManagers.adaptive.state().health
            val resilience=AppManagers.resilienceHealth.snapshot().overall
            findViewById<TextView>(R.id.performanceLabSummary)?.text = "Health ${health}%  •  Resilience ${resilience}%  •  RAM guarded"
            val ram=BenAiResourceGovernor(this@MainActivity).snapshot(foreground=true).availableMemoryMb
            val ramScore=(ram.toFloat()/2048f*100f).toInt().coerceIn(0,100)
            findViewById<RovexPerformanceLabView>(R.id.performanceLabViz)?.setScores(health,resilience,ramScore)
        }
        findViewById<View>(R.id.performanceLabCard)?.setOnClickListener{showInteractiveAnalytics(refs,7)}
        applyDashboardTheme()
        setCollectionButton(R.id.importantButton,"★ Important",bookmarks["important"] ?: 0,ThemeManager.bookmarkFill(this@MainActivity,"important"),ThemeManager.bookmarkText(this@MainActivity,"important"))
        setCollectionButton(R.id.reviseButton,"↻ Revise",bookmarks["revise"] ?: 0,ThemeManager.bookmarkFill(this@MainActivity,"revise"),ThemeManager.bookmarkText(this@MainActivity,"revise"))
        setCollectionButton(R.id.doubtButton,"❔ Doubt",bookmarks["doubt"] ?: 0,ThemeManager.bookmarkFill(this@MainActivity,"doubt"),ThemeManager.bookmarkText(this@MainActivity,"doubt"))
        setCollectionButton(R.id.favoriteButton,"♥ Favourite",bookmarks["favorite"] ?: 0,ThemeManager.bookmarkFill(this@MainActivity,"favorite"),ThemeManager.bookmarkText(this@MainActivity,"favorite"))
        styleDashboardCards()
        applyDashboardAccessibility()
        AdaptiveTypographyManager.apply(findViewById(R.id.dashboardRoot))
        findViewById<Button>(R.id.addButton).setOnClickListener{openImporter()}
        setupCollection(R.id.importantButton,"bookmark","important"); setupCollection(R.id.reviseButton,"bookmark","revise"); setupCollection(R.id.doubtButton,"bookmark","doubt"); setupCollection(R.id.favoriteButton,"bookmark","favorite")
        publishQBankSources(sources, sourceProgress)
        val list=findViewById<RecyclerView>(R.id.libraryList)
        list.setOnTouchListener { v, event ->
            when(event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        } finally {
            android.os.Trace.endSection()
        }
    }


    private fun continueStudy(refs: List<QuestionRef>, sources: List<Source>) {
        runCatching {
            val session = AppManagers.sessionStore.current()
            if (session != null && session.testId.isNotBlank() && !session.id.contains("collection", true)) {
                val test = mainViewModel.testById(session.testId)
                if (test != null && test.count > 0) {
                    startActivity(Intent(this, QuizActivity::class.java).apply {
                        putExtra("testId", test.id)
                        putExtra("title", test.title)
                        putExtra("position", session.position.coerceIn(0, test.count - 1))
                        putExtra("sessionLabel", test.title)
                    })
                    return
                }
            }
            for (source in sources) {
                val target = mainViewModel.resumeTarget(source)
                val testId = target?.testId.orEmpty()
                val test = testId.takeIf { it.isNotBlank() }?.let { mainViewModel.testById(it) }
                if (test != null && test.count > 0) {
                    startActivity(Intent(this, QuizActivity::class.java).apply {
                        putExtra("testId", test.id)
                        putExtra("title", test.title)
                        putExtra("position", target?.position?.coerceIn(0, test.count - 1) ?: 0)
                        putExtra("sessionLabel", test.title)
                    })
                    return
                }
            }
            val first = refs.firstOrNull()
            if (first != null) {
                startActivity(Intent(this, QuizActivity::class.java).apply {
                    putExtra("testId", first.testId)
                    putExtra("title", first.testTitle)
                    putExtra("position", first.position)
                    putExtra("sessionLabel", first.testTitle)
                })
            } else Toast.makeText(this, "Import a QBank to continue studying", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(this, "Could not resume this study session. Your progress is safe.", Toast.LENGTH_SHORT).show() }
    }

    private fun launchQuickCollection(ids:LongArray,label:String){
        if(ids.isEmpty()){Toast.makeText(this,"No questions available",Toast.LENGTH_SHORT).show();return}
        startActivity(Intent(this,QuizActivity::class.java).putExtra("collectionMode",true).putExtra("sessionIds",ids.take(100).joinToString(",")).putExtra("sessionLabel",label).putExtra("practiceMode",true).putExtra("title",label))
    }

    private fun wrongIdsFor(refs:List<QuestionRef>):LongArray = mainViewModel.wrongIds(refs)



    private fun startTodaySolved(refs:List<QuestionRef>){
        if (isFinishing || isDestroyed) return
        runCatching {
            val ids = mainViewModel.todaySolvedIds(refs).toList()
            if(ids.isEmpty()){ Toast.makeText(this,"No questions solved today",Toast.LENGTH_SHORT).show(); return }
            startActivity(Intent(this,QuizActivity::class.java).apply{
                putExtra("sessionIds",ids.joinToString(","))
                putExtra("collectionMode",true)
                putExtra("title","Today’s Solved")
                putExtra("sessionLabel","Today’s Solved")
                putExtra("position",0)
                putExtra("practiceMode",true)
            })
        }.onFailure { Toast.makeText(this,"Today's Solved could not be opened. Your progress is safe.",Toast.LENGTH_LONG).show() }
    }

    private fun todaysRevisionCount(refs:List<QuestionRef>):Int = mainViewModel.todayRevisionCount(refs)

    private fun startTodayRevision(refs:List<QuestionRef>){
        runCatching {
            val ids = mainViewModel.todayRevisionIds(refs).toList()
            if(ids.isEmpty()){Toast.makeText(this,"No questions are due for today's revision yet.",Toast.LENGTH_SHORT).show();return}
            startActivity(Intent(this,QuizActivity::class.java).putExtra("collectionMode",true).putExtra("sessionIds",ids.joinToString(",")).putExtra("sessionLabel","Today's Revision").putExtra("practiceMode",true).putExtra("title","Today's Revision"))
        }.onFailure { Toast.makeText(this,"Revision could not be opened. Your progress is safe.",Toast.LENGTH_SHORT).show() }
    }

    private fun currentQBankRefs(allRefs:List<QuestionRef>):List<QuestionRef> = mainViewModel.currentQBankRefs(allRefs)


    private fun showInteractiveAnalytics(allRefs:List<QuestionRef>,periodDays:Int=0){
        if (isFinishing || isDestroyed) return
        try {
        analyticsPeriod=periodDays
        val baseRefs=if(periodDays<=0) allRefs else { val since=System.currentTimeMillis()-periodDays*86_400_000L; val progress=PerformanceManager.progress(applicationContext); allRefs.filter{progress.record(it.stableKey)?.lastAttempted?.let{t->t>=since}==true} }
        val refs=currentQBankRefs(baseRefs)
        val snap=AppManagers.analytics.snapshot(refs)
        val solved=snap.correct
        val wrong=snap.wrong
        val attempted=snap.attempted
        val total=snap.total
        val accuracy=snap.accuracy
        val mastery=snap.mastery
        val due=snap.due
        val notes=snap.notes
        val bookmarks=snap.bookmarks
        val avgMs=snap.averageTimeMs
        val today=snap.todaySolved
        val dark=ThemeManager.isDark(this)
        val themeLabBg=uiPreferences.performanceLabThemeBackground()
        analyticsDialog?.dismiss()
        val dialog=Dialog(this)
        analyticsDialog=dialog
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=if(themeLabBg)ThemeManager.backgroundDrawable(this@MainActivity) else GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(ThemeManager.accent(this@MainActivity),ThemeManager.elevated(this@MainActivity),ThemeManager.bg(this@MainActivity)))}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),dp(12),dp(10),dp(8));background=GradientDrawable().apply{setColor(if(dark)Color.rgb(18,43,57)else Color.rgb(24,84,108));cornerRadius=0f}}
        top.addView(TextView(this).apply{text="PERFORMANCE LAB";textSize=15.5f;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,dp(48),1f))
        top.addView(TextView(this).apply{
            text=if(themeLabBg)"THEME" else "DATA";textSize=9.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
            background=UiDrawableUtils.roundedDrawable(this@MainActivity,Color.argb(45,255,255,255),10f);setPadding(dp(8),0,dp(8),0)
            setOnClickListener{uiPreferences.setPerformanceLabThemeBackground(!themeLabBg);dialog.dismiss();showInteractiveAnalytics(allRefs,periodDays)}
        },LinearLayout.LayoutParams(dp(58),dp(34)).apply{setMargins(0,0,dp(6),0)})
        top.addView(TextView(this).apply{text="×";textSize=28f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(42),dp(48)))
        root.addView(top)
        val periods=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(12),dp(10),dp(12),dp(2))}
        listOf(1 to "DAILY",7 to "WEEKLY",30 to "MONTHLY",0 to "ALL TIME").forEach{(days,label)->
            val selected=periodDays==days
            val chip=TextView(this).apply{text=label;textSize=10.5f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setTextColor(if(selected)Color.WHITE else ThemeManager.text(this@MainActivity));background=UiDrawableUtils.roundedDrawable(this@MainActivity,if(selected)ThemeManager.accent(this@MainActivity) else ThemeManager.elevated(this@MainActivity),14f);setPadding(dp(8),dp(7),dp(8),dp(7));setOnClickListener{dialog.dismiss();showInteractiveAnalytics(allRefs,days)}}
            periods.addView(chip,LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(dp(3),0,dp(3),0)})
        }
        root.addView(periods)
        val scroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(26))}

        val hero=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(16),dp(14),dp(16));background=GradientDrawable().apply{setColor(if(dark)Color.rgb(24,42,55)else Color.rgb(233,247,250));cornerRadius=24f}}
        hero.addView(PerformanceRing(this,accuracy,mastery,dark),LinearLayout.LayoutParams(dp(126),dp(126)))
        val heroText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(4),0)}
        heroText.addView(TextView(this).apply{text=when{attempted==0->"Ready to build your profile";accuracy>=85->"Elite accuracy zone";accuracy>=70->"Strong base — sharpen weak areas";else->"High-yield opportunity zone"};textSize=19f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@MainActivity))})
        heroText.addView(TextView(this).apply{text="${accuracy}% accuracy  •  ${mastery}% mastery";textSize=13f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@MainActivity));setPadding(0,dp(7),0,dp(5))})
        heroText.addView(TextView(this).apply{text="${today} questions in this period. ${if(due>0)"${due} questions are waiting for revision." else "No revision backlog is waiting."}";textSize=12.5f;setTextColor(ThemeManager.muted(this@MainActivity));setLineSpacing(0f,1.15f)})
        hero.addView(heroText,LinearLayout.LayoutParams(0,-2,1f));box.addView(hero)

        val section=TextView(this).apply{text="YOUR CONTROL PANEL";textSize=11.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.muted(this@MainActivity));setPadding(dp(2),dp(18),dp(2),dp(8))};box.addView(section)
        val actionRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;weightSum=3f}
        fun action(label:String,value:String,bg:Int,click:()->Unit){val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(7),dp(11),dp(7),dp(10));background=UiDrawableUtils.roundedDrawable(this@MainActivity,if(dark)Color.rgb(27,35,45)else bg,18f);setOnClickListener{click()}};c.addView(TextView(this).apply{text=value;textSize=22f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@MainActivity))});c.addView(TextView(this).apply{text=label;textSize=10.5f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@MainActivity))});actionRow.addView(c,LinearLayout.LayoutParams(0,dp(76),1f).apply{setMargins(dp(3),0,dp(3),0)})}
        action("WRONG",wrong.toString(),Color.rgb(253,232,235)){startActivity(Intent(this,CollectionActivity::class.java).putExtra("filterType","status").putExtra("filterValue","wrong"))}
        val currentRefs=currentQBankRefs(refs)
        val currentDue=todaysRevisionCount(currentRefs)
        action("DUE NOW",currentDue.toString(),Color.rgb(255,243,214)){startTodayRevision(currentRefs)}
        action("UNSOLVED",(total-attempted).coerceAtLeast(0).toString(),Color.rgb(230,241,248)){startActivity(Intent(this,CollectionActivity::class.java).putExtra("filterType","status").putExtra("filterValue","unsolved"))}
        box.addView(actionRow)

        val speed=if(avgMs>0)"${(avgMs/1000).coerceAtLeast(1)}s / question" else "—"
        val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;weightSum=3f;setPadding(0,dp(10),0,0)}
        fun metric(label:String,value:String,click:(()->Unit)?=null){val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(11),dp(10),dp(8),dp(10));background=UiDrawableUtils.roundedDrawable(this@MainActivity,if(ThemeManager.get(this@MainActivity)==ThemeManager.AMOLED) Color.BLACK else if(dark)Color.rgb(22,29,38)else Color.rgb(248,250,252),16f);if(click!=null){isClickable=true;isFocusable=true;setOnClickListener{click()}}};c.addView(TextView(this).apply{text=value;textSize=18f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@MainActivity))});c.addView(TextView(this).apply{text=label;textSize=10.5f;setTextColor(ThemeManager.muted(this@MainActivity));setPadding(0,dp(3),0,0)});stats.addView(c,LinearLayout.LayoutParams(0,dp(70),1f).apply{setMargins(dp(3),0,dp(3),0)})}
        metric("AVG TIME",speed);metric("BOOKMARKS",bookmarks.toString()){startActivity(Intent(this,CollectionActivity::class.java).putExtra("filterType","bookmark").putExtra("filterValue","all"))};metric("NOTES",notes.toString()){startActivity(Intent(this,NotesActivity::class.java))};box.addView(stats)

        box.addView(TextView(this).apply{text="QBANK FOCUS MAP";textSize=11.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.muted(this@MainActivity));setPadding(dp(2),dp(18),dp(2),dp(7))})
        val sources=mainViewModel.uiState.value.sources
        sources.take(10).forEachIndexed{idx,src->
            val r=refs.filter{it.sourceName==src.fileName}; val p=PerformanceManager.progress(applicationContext); val a=r.count{p.record(it.stableKey)?.status!=null}; val c=r.count{p.record(it.stableKey)?.status=="correct"}; val pct=if(a==0)0 else c*100/a
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(9),dp(12),dp(9));background=UiDrawableUtils.roundedDrawable(this@MainActivity,if(ThemeManager.get(this@MainActivity)==ThemeManager.AMOLED) Color.BLACK else if(dark)Color.rgb(22,29,38)else Color.rgb(248,250,247),16f)}
            val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            head.addView(TextView(this).apply{text=src.fileName;textSize=13.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@MainActivity));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f))
            head.addView(TextView(this).apply{text="$a/${r.size}  •  $pct%";textSize=11.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@MainActivity));gravity=Gravity.END})
            row.addView(head)
            val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=pct;progressTintList=android.content.res.ColorStateList.valueOf(if(pct>=80)Color.rgb(35,145,103)else if(pct>=60)Color.rgb(34,125,143)else Color.rgb(210,115,70));backgroundTintList=android.content.res.ColorStateList.valueOf(Color.argb(40,80,100,120))}
            row.addView(bar,LinearLayout.LayoutParams(-1,dp(6)).apply{setMargins(0,dp(8),0,0)})
            row.setOnClickListener{startActivity(Intent(this,TestListActivity::class.java).putExtra("sourceId",src.id).putExtra("sourceName",src.fileName))}
            box.addView(row,LinearLayout.LayoutParams(-1,dp(70)).apply{setMargins(0,dp(3),0,dp(3))})
        }
        box.addView(TextView(this).apply{text="ADAPTIVE INSIGHT\n\n${AppManagers.adaptive.insight()}\n\nStudy guidance: ${AppManagers.learning.recommendation(refs)}";textSize=12.5f;setTextColor(ThemeManager.text(this@MainActivity));setLineSpacing(0f,1.12f);setPadding(dp(14),dp(13),dp(14),dp(13));background=UiDrawableUtils.roundedDrawable(this@MainActivity,if(ThemeManager.get(this@MainActivity)==ThemeManager.AMOLED) Color.BLACK else if(dark)Color.rgb(25,32,41)else Color.rgb(255,247,225),16f)})
        box.addView(TextView(this).apply{text="Open Study Menu  →";textSize=14f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(this@MainActivity));setPadding(0,dp(16),0,dp(4));setOnClickListener{dialog.dismiss();startActivity(Intent(this@MainActivity,StudyToolsActivity::class.java))}})
        scroll.addView(box);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if (isFinishing || isDestroyed) return
        dialog.setContentView(root)
        dialog.setOnDismissListener { if (analyticsDialog === dialog) analyticsDialog = null }
        dialog.show()
        dialog.window?.setLayout(-1,(resources.displayMetrics.heightPixels*0.92f).toInt())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        } catch (t:Exception) {
            analyticsDialog = null
            runCatching { if (!isFinishing && !isDestroyed) Toast.makeText(this,"Analytics could not be opened. Please try again.",Toast.LENGTH_SHORT).show() }
        }
    }

    private fun animateAccuracyHero(view:TextView, target:Int){
        val safeTarget=target.coerceIn(0,100)
        view.animate().cancel()
        view.scaleX=0.88f; view.scaleY=0.88f; view.alpha=0.82f
        view.animate().scaleX(1.035f).scaleY(1.035f).alpha(1f).setDuration(260L).withEndAction{
            view.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
        }.start()
        val animator=android.animation.ValueAnimator.ofInt(0,safeTarget).apply{
            duration=520L
            addUpdateListener{a->view.text="${a.animatedValue as Int}%\naccuracy"}
        }
        animator.start()
    }

    private class PerformanceRing(context:Context,private val accuracy:Int,private val mastery:Int,private val dark:Boolean):View(context){
        private val density=context.resources.displayMetrics.density
        private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{style=android.graphics.Paint.Style.STROKE;strokeWidth=14f*density;strokeCap=android.graphics.Paint.Cap.ROUND}
        private val t=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textAlign=android.graphics.Paint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD}
        override fun onDraw(c:android.graphics.Canvas){super.onDraw(c);val cx=width/2f;val cy=height/2f;val r=minOf(width,height)*.34f;p.color=if(dark)Color.rgb(55,70,84)else Color.rgb(207,224,231);c.drawCircle(cx,cy,r,p);p.color=if(accuracy>=80)Color.rgb(39,150,113)else Color.rgb(35,126,151);c.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,accuracy*3.6f,false,p);t.color=if(dark)Color.WHITE else Color.rgb(19,55,70);t.textSize=25f*density;c.drawText("$accuracy%",cx,cy+8f*density,t);t.textSize=9.5f*density;t.color=if(dark)Color.LTGRAY else Color.rgb(82,105,116);c.drawText("ACCURACY",cx,cy+25f*density,t)}
    }

    private fun showQBankActions(source:Source){
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(16));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@MainActivity));cornerRadius=dp(24).toFloat()}}
        root.addView(RovexWaveTextView(this).apply{text=source.fileName;textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@MainActivity));maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
        root.addView(TextView(this).apply{text="QBank actions";textSize=11.5f;setTextColor(ThemeManager.muted(this@MainActivity));setPadding(0,dp(3),0,dp(12))})
        fun action(title:String,sub:String,fill:Int,fg:Int,click:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(10),dp(13),dp(10));background=GradientDrawable().apply{setColor(fill);cornerRadius=dp(15).toFloat()};setOnClickListener{click();dialog.dismiss()};addView(TextView(this@MainActivity).apply{text=title;textSize=14.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(fg)});addView(TextView(this@MainActivity).apply{text=sub;textSize=11f;setTextColor(if(ThemeManager.isDark(this@MainActivity))fg else ThemeManager.muted(this@MainActivity));setPadding(0,dp(3),0,0)})}
        root.addView(action("EDIT QBANK","Change display name or series number",ThemeManager.pastelBlueFill(this),ThemeManager.pastelBlueText(this)){showEditSourceDialog(source)},LinearLayout.LayoutParams(-1,dp(64)))
        root.addView(action("DELETE QBANK","Remove this QBank and its imported questions",ThemeManager.pastelRedFill(this),ThemeManager.pastelRedText(this)){confirmDeleteSource(source)},LinearLayout.LayoutParams(-1,dp(64)).apply{setMargins(0,dp(8),0,0)})
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((resources.displayMetrics.widthPixels*.90f).toInt(),-2);AdaptiveTypographyManager.apply(root)
    }

    private fun confirmDeleteSource(source:Source){
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(16));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@MainActivity));cornerRadius=dp(24).toFloat()}}
        root.addView(RovexWaveTextView(this).apply{text="Delete QBank?";textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@MainActivity))})
        root.addView(TextView(this).apply{text="Remove ${source.fileName} and all its imported questions from Rovex?";textSize=12.5f;setTextColor(ThemeManager.muted(this@MainActivity));setPadding(0,dp(6),0,dp(14))})
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val cancel=TextView(this).apply{text="CANCEL";textSize=11.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@MainActivity));background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.elevated(this@MainActivity),13f);setOnClickListener{dialog.dismiss()}}
        val del=TextView(this).apply{text="DELETE";textSize=11.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.pastelRedText(this@MainActivity));background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.pastelRedFill(this@MainActivity),13f);setOnClickListener{mainViewModel.deleteSource(source.id);Toast.makeText(this@MainActivity,"QBank deleted",Toast.LENGTH_SHORT).show();dialog.dismiss()}}
        row.addView(cancel,LinearLayout.LayoutParams(0,dp(44),1f));row.addView(del,LinearLayout.LayoutParams(0,dp(44),1f).apply{setMargins(dp(8),0,0,0)});root.addView(row)
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((resources.displayMetrics.widthPixels*.90f).toInt(),-2);AdaptiveTypographyManager.apply(root)
    }

    private fun showEditSourceDialog(source: Source) {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(4),dp(20),0)}
        val name=EditText(this).apply{setText(source.fileName);setSingleLine(true);hint="Main QBank name"}
        val series=EditText(this).apply{setText(source.seriesNumber);setSingleLine(true);hint="Series number (optional)";inputType=android.text.InputType.TYPE_CLASS_TEXT}
        box.addView(name,LinearLayout.LayoutParams(-1,dp(56)))
        box.addView(series,LinearLayout.LayoutParams(-1,dp(56)))
        val d=AlertDialog.Builder(this).setTitle("Edit main QBank").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        d.setOnShowListener{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val ok=mainViewModel.updateSourceMetadata(source.id,name.text.toString(),series.text.toString())
                if(ok){Toast.makeText(this,"QBank updated",Toast.LENGTH_SHORT).show();d.dismiss()}
                else Toast.makeText(this,"Could not save the name. Use a non-empty name.",Toast.LENGTH_SHORT).show()
            }
        }
        d.show()
    }

    private fun setCollectionButton(id:Int,label:String,count:Int,bg:Int,fg:Int){
        val b=findViewById<Button>(id)
        val text=SpannableString("$label\n$count")
        text.setSpan(RelativeSizeSpan(1.0f),0,label.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(RelativeSizeSpan(1.55f),label.length+1,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        b.text=text
        b.setTextColor(fg)
        // Android Button styles can apply a theme tint over a custom drawable. Clear it
        // so each bookmark collection keeps its own semantic color.
        b.backgroundTintList = null
        b.stateListAnimator = null
        b.elevation = 0f
        b.background=GradientDrawable().apply{setColor(bg);cornerRadius=18f*resources.displayMetrics.density;setStroke((1*resources.displayMetrics.density).toInt(),Color.argb(55,100,120,140))}
    }
    private fun applyResponsiveHomeLayout(){
        val widthDp=resources.configuration.screenWidthDp
        val compact=widthDp<360
        val expanded=widthDp>=600
        findViewById<View>(R.id.homeSearchCard)?.layoutParams?.let{ lp ->
            lp.height=dp(if(compact) 50 else if(expanded) 60 else 56)
            findViewById<View>(R.id.homeSearchCard).layoutParams=lp
        }
        listOf(R.id.todaySolvedCard,R.id.studyMenuCard).forEach{ id ->
            findViewById<View>(id)?.layoutParams?.let{ lp ->
                lp.height=dp(if(compact) 108 else if(expanded) 118 else 104)
                findViewById<View>(id).layoutParams=lp
            }
        }
        findViewById<View>(R.id.flashcardCard)?.layoutParams?.let{lp->
            lp.height=dp(if(compact) 92 else if(expanded) 102 else 96)
            findViewById<View>(R.id.flashcardCard).layoutParams=lp
        }
    }

    private fun styleHomeNavigation(){
        val dark=ThemeManager.isDark(this@MainActivity)
        val searchBg=ThemeManager.elevated(this@MainActivity)
        val border=if(dark) Color.rgb(63,78,96) else Color.rgb(211,220,226)
        findViewById<View>(R.id.homeSearchCard)?.background=GradientDrawable().apply{
            setColor(searchBg);cornerRadius=18f*resources.displayMetrics.density;setStroke(dp(1),border)
        }
        findViewById<ImageView>(R.id.homeSearchIcon)?.setColorFilter(ThemeManager.accent(this@MainActivity))
        findViewById<TextView>(R.id.homeSearchText)?.setTextColor(ThemeManager.muted(this@MainActivity))
        findViewById<TextView>(R.id.homeSearchShortcut)?.apply{
            setTextColor(ThemeManager.accent(this@MainActivity))
            background=GradientDrawable().apply{setColor(if(ThemeManager.get(this@MainActivity)==ThemeManager.AMOLED) Color.BLACK else if(dark) Color.rgb(31,44,58) else Color.rgb(239,244,247));cornerRadius=9f*resources.displayMetrics.density}
        }
    }

    private fun darken(color:Int,factor:Float):Int=Color.rgb((Color.red(color)*factor).roundToInt().coerceIn(0,255),(Color.green(color)*factor).roundToInt().coerceIn(0,255),(Color.blue(color)*factor).roundToInt().coerceIn(0,255))

    private fun applyDashboardTheme(){
        val root=findViewById<View>(R.id.dashboardRoot)
        // Rows whose colour carries meaning (accent labels, muted captions, live status)
        // must NOT be flattened to the plain body-text colour, or every theme collapses to
        // one flat tone and the dashboard hierarchy disappears. Everything not explicitly
        // listed still gets the standard body-text colour for theme-correctness.
        val accentIds = setOf(R.id.flashcardTitle, R.id.performanceLabLabel, R.id.studyMenuTitle)
        val mutedIds = setOf(R.id.flashcardProgress, R.id.flashcardDue, R.id.performanceLabSummary, R.id.performanceLabHint, R.id.flashcardHint)
        fun walk(v:View){
            if(v.id==R.id.mainHeader) return
            if(v is TextView && v !is Button && v !is RovexColorFlowTextView){
                when(v.id){
                    in accentIds -> v.setTextColor(ThemeManager.accent(this@MainActivity))
                    in mutedIds -> v.setTextColor(ThemeManager.muted(this@MainActivity))
                    else -> v.setTextColor(ThemeManager.text(this@MainActivity))
                }
            }
            if(v is ViewGroup) for(i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }
    private fun applyDashboardAccessibility(){
        findViewById<View>(R.id.todaySolvedCard)?.let { RovexAccessibility.action(it, getString(R.string.today_solved_accessibility)) }
        findViewById<View>(R.id.studyMenuCard)?.let { RovexAccessibility.action(it, getString(R.string.study_workbench_accessibility)) }
        findViewById<View>(R.id.flashcardCard)?.let { RovexAccessibility.action(it, getString(R.string.flashcards_accessibility)) }
        findViewById<View>(R.id.performanceLabCard)?.let { RovexAccessibility.action(it, getString(R.string.performance_lab_accessibility)) }
        findViewById<View>(R.id.homeSearchCard)?.let { RovexAccessibility.action(it, getString(R.string.search_questions_hint)) }
        findViewById<View>(R.id.themeButton)?.let { RovexAccessibility.action(it, getString(R.string.settings_menu_accessibility)) }
        listOf(R.id.importantButton,R.id.reviseButton,R.id.doubtButton,R.id.favoriteButton).forEach { id ->
            findViewById<View>(id)?.let { RovexAccessibility.action(it) }
        }
    }

    private fun styleDashboardCards(){
        val dark=ThemeManager.isDark(this@MainActivity)
        val theme=ThemeManager.get(this@MainActivity)
        val ids=listOf(R.id.importantButton,R.id.reviseButton,R.id.doubtButton,R.id.favoriteButton)
        ids.forEach{findViewById<View>(it)?.let{v->v.stateListAnimator=null}}
        // Section surfaces are intentionally transparent so the selected theme atmosphere
        // (including the richer planetary Cosmos/Avatar/Midnight backgrounds) remains visible.
        findViewById<View>(R.id.qbankSection)?.background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        findViewById<View>(R.id.collectionsSection)?.background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        findViewById<View>(R.id.flashcardCard)?.background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.elevated(this@MainActivity),20f)
        findViewById<View>(R.id.performanceLabCard)?.background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.panel(this@MainActivity),20f)
        // No paper/relic artwork on the dashboard: cards now use theme-native tonal surfaces.
        listOf(R.id.todaySolvedCard,R.id.studyMenuCard).forEach { id ->
            findViewById<View>(id)?.apply {
                background=ThemeManager.transparentSectionDrawable(this@MainActivity)
                foreground=android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(if(dark)48 else 30,
                        Color.red(ThemeManager.accent(this@MainActivity)),
                        Color.green(ThemeManager.accent(this@MainActivity)),
                        Color.blue(ThemeManager.accent(this@MainActivity)))), null, null)
                isClickable=true; isFocusable=true
            }
        }
    }
    private fun refreshHomeFlashcardSummary(){
        val summary=findViewById<TextView>(R.id.flashcardProgress) ?: return
        val due=findViewById<TextView>(R.id.flashcardDue) ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result=runCatching {
                SqliteFlashcardReviewRepository(applicationContext).use { repo ->
                    Triple(repo.modeCount("all"),repo.reviewCount(),repo.dueStatsAll().due)
                }
            }
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (total,reviews,dueCount) ->
                    summary.text="${total} cards  •  ${reviews} reviews"
                    due.text=if(dueCount>0) "${dueCount} due now  •  Open flashcards ›" else "Up to date  •  Open flashcards ›"
                    findViewById<RovexFlashcardSummaryView>(R.id.flashcardViz)?.setStats(total,dueCount,reviews)
                }.onFailure {
                    findViewById<RovexFlashcardSummaryView>(R.id.flashcardViz)?.setStats(0,0,0)
                    summary.text="Flashcard library ready"
                    due.text="Open flashcards ›"
                }
            }
        }
    }

    private fun showThemeMenu(anchor: View) {
        val dialog=Dialog(this)
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(16));setBackgroundColor(ThemeManager.dialogBg(this@MainActivity))}
        root.addView(TextView(this).apply{text="Appearance & Typography";textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@MainActivity));setPadding(dp(6),dp(2),dp(6),dp(4))})
        root.addView(TextView(this).apply{text="Theme and quiz font in one adaptive panel";textSize=11.5f;setTextColor(ThemeManager.muted(this@MainActivity));setPadding(dp(6),0,dp(6),dp(10))})
        fun section(title:String,subtitle:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.elevated(this@MainActivity),14f);addView(TextView(this@MainActivity).apply{text=title;textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(this@MainActivity))});addView(TextView(this@MainActivity).apply{text=subtitle;textSize=11f;setTextColor(ThemeManager.muted(this@MainActivity));setPadding(0,dp(3),0,dp(6))})}
        val themeCard=section("Theme","Choose the global appearance")
        val themes=linkedMapOf(ThemeManager.LIGHT to "Light",ThemeManager.DARK to "Dark",ThemeManager.SEPIA to "Sepia",ThemeManager.AMOLED to "AMOLED Black",ThemeManager.MIDNIGHT to "Midnight Blue",ThemeManager.COSMOS to "Cosmos • Galaxy",ThemeManager.AVATAR to "Pandora • Avatar")
        themes.forEach{(key,label)->themeCard.addView(TextView(this).apply{text=if(ThemeManager.get(this@MainActivity)==key)"✓  $label" else label;textSize=13f;gravity=Gravity.CENTER_VERTICAL;setTextColor(ThemeManager.text(this@MainActivity));background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.bg(this@MainActivity),10f);setPadding(dp(10),0,dp(10),0);setOnClickListener{ThemeManager.set(this@MainActivity,key);dialog.dismiss();recreate()}},LinearLayout.LayoutParams(-1,dp(40)).apply{setMargins(0,dp(3),0,dp(3))})}
        root.addView(themeCard,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(5))})
        val fontCard=section("Quiz font","Choose the reading style used in question solving")
        val fonts=linkedMapOf("sans-serif" to "Modern Sans","serif" to "Serif / textbook","sans-serif-condensed" to "Condensed Sans","sans-serif-light" to "Light Sans")
        val current=uiPreferences.quizFontFamily()
        fonts.forEach{(key,label)->fontCard.addView(TextView(this).apply{text=if(current==key)"✓  $label" else label;textSize=13f;gravity=Gravity.CENTER_VERTICAL;setTextColor(ThemeManager.text(this@MainActivity));background=UiDrawableUtils.roundedDrawable(this@MainActivity,ThemeManager.bg(this@MainActivity),10f);setPadding(dp(10),0,dp(10),0);setOnClickListener{uiPreferences.setQuizFontFamily(key);dialog.dismiss();Toast.makeText(this@MainActivity,"Quiz font updated",Toast.LENGTH_SHORT).show()}},LinearLayout.LayoutParams(-1,dp(40)).apply{setMargins(0,dp(3),0,dp(3))})}
        root.addView(fontCard,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(5),0,dp(4))})
        val more=TextView(this).apply{text="More settings  ›";textSize=12.5f;gravity=Gravity.CENTER_VERTICAL;setTextColor(ThemeManager.accent(this@MainActivity));setPadding(dp(12),dp(10),dp(12),dp(10));setOnClickListener{dialog.dismiss();startActivity(Intent(this@MainActivity,SettingsActivity::class.java).putExtra("section","all"))}}
        root.addView(more)
        scroll.addView(root);dialog.setContentView(scroll);dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.window?.setLayout(dp(350),ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun setupCollection(id:Int,type:String,value:String){findViewById<View>(id).setOnClickListener{startActivity(Intent(this,CollectionActivity::class.java).putExtra("filterType",type).putExtra("filterValue",value))}}
    private fun openImporter(){
        htmlImportLauncher.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml"))
    }
    override fun onDestroy(){analyticsDialog?.dismiss();analyticsDialog=null;AppState.unregister(stateListener);super.onDestroy()}

private fun refreshUserPerformanceLab(){
    PerformanceManager.submit{
        val refs=PerformanceManager.lightRefs(applicationContext);val progress=PerformanceManager.progress(applicationContext)
        var solved=0;var correct=0;var wrong=0
        refs.forEach{r->when(progress.record(r.stableKey)?.status){"correct"->{solved++;correct++};"wrong"->{solved++;wrong++}}}
        val acc=if(solved==0)0 else correct*100/solved
        runOnUiThread{findViewById<RovexPerformanceLabView>(R.id.performanceLabViz)?.setUserStats(acc,solved,wrong)}
    }
}

}

private fun Int.dp(context: android.content.Context): Int = (this * context.resources.displayMetrics.density).toInt()
