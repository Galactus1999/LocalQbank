package com.localqbank.library

import com.localqbank.library.UiActionGuard.setGuardedClick

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.app.AlertDialog
import android.app.Dialog
import android.view.MotionEvent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.*
import android.os.SystemClock
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuizActivity : AppCompatActivity() {
    private val quizViewModel: QuizViewModel by viewModels { (application as LocalQBankApplication).appContainer.quizViewModelFactory() }
    private val sessionLifecycle by lazy {
        QuizSessionLifecycleController(quizViewModel, this, ::recordCurrentTime)
    }
    // Session state is owned by QuizViewModel. Activity only reads it for rendering/events.
    private lateinit var content: LinearLayout
    private lateinit var counter: TextView
    private lateinit var result: TextView
    private lateinit var scroll: LockedScrollView
    private lateinit var bookmarkButton: TextView
    private lateinit var nextButton: TextView
        private lateinit var settingsButton: TextView
    private lateinit var sectionChip: TextView
    private lateinit var jumpButton: TextView
    private var fontScale = 1f
    private var fontFamily = "sans-serif"
    private var headerMm = 10f
    private val uiPreferences by lazy { QuizUiPreferences(this) }
    private val quizTimers = QuizTimerController(
        onExamTick = { remaining -> quizViewModel.setExamRemainingMs(remaining); updateExamTimer() },
        onExamFinish = { quizViewModel.setExamRemainingMs(0L); updateExamTimer(); submitExam(true) },
        onGuidanceTick = { remaining -> guidanceRemainingMs = remaining; updateGuidanceTimer() },
        onGuidanceFinish = { guidanceRemainingMs = 0L; updateGuidanceTimer() }
    )
    private var guidanceRemainingMs = 0L
    private val guidanceSecondsPerQuestion = 60L
    private lateinit var bankNameView: TextView
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var questionStartedAt = 0L
    private var timeRecordedForKey: String? = null
    private var questionLoadToken = 0L
    private val imageExecutor by lazy { LifecycleExecutor(java.util.concurrent.Executors.newFixedThreadPool(PerformanceManager.recommendedImageConcurrency()) { r -> Thread(r, "quiz-image") }) }
    private val prefetchExecutor = LifecycleExecutor(java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "quiz-prefetch") })
    private val spannedCache = object : java.util.LinkedHashMap<String, Spanned>(16, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Spanned>?) = size > 16 }
    // Small LRU keeps adjacent questions hot; avoids repeated SQLite joins while swiping.
    private val questionCache = object : java.util.LinkedHashMap<String, Question>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Question>?): Boolean = size > 16
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ResilienceManager.activityStarted(this, this)
        ResilienceManager.heartbeat(this)
        // Android 15+ is edge-to-edge by default for targetSdk 35. Keep the status bar visible,
        // but route all tappable quiz content through the actual safe drawing/cutout insets.
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !ThemeManager.isDark(this)

        // Session resolution touches SQLite/SharedPreferences. Do not perform that work on the
        // main thread during Activity startup; large QBanks can otherwise turn a normal launch
        // into an ANR. Rendering remains on the main thread after the bounded resolution completes.
        lifecycleScope.launch {
            try {
                val requestedTitle = extraText("title") ?: "QBank"
                val requestedTestId = extraText("testId") ?: ""
                val sessionIdsRaw = extraText("sessionIds")
                val requestedCollectionMode = intent.getBooleanExtra("collectionMode", false) || !sessionIdsRaw.isNullOrBlank()
                val exactQuestionId = extraLong("questionId")
                val requestedPosition = extraInt("position", -1)
                val requestedExamMode = intent.getBooleanExtra("examMode", false)
                val requestedExamDuration = extraInt("examDurationMinutes", 0).coerceIn(1, 240)
                val requestedSessionLabel = extraText("sessionLabel")
                val requestedPracticeMode = intent.getBooleanExtra("practiceMode", false)
                val requestedSectionLabel = extraText("sectionLabel")

                quizViewModel.configureSession(
                    title = requestedTitle, requestedTestId = requestedTestId, collectionMode = requestedCollectionMode,
                    examMode = requestedExamMode, examDurationMinutes = requestedExamDuration,
                    practiceMode = requestedPracticeMode, sessionLabel = requestedSessionLabel, sectionLabel = requestedSectionLabel
                )

                val resolution = withContext(Dispatchers.IO) {
                    quizViewModel.resolveSession(
                        title = quizViewModel.state.title, requestedTestId = quizViewModel.state.testId, requestedQuestionId = exactQuestionId,
                        requestedPosition = requestedPosition, sessionIdsRaw = sessionIdsRaw, collectionMode = quizViewModel.state.collectionMode,
                        filterType = extraText("collectionFilterType") ?: "status",
                        filterValue = extraText("collectionFilterValue") ?: "unsolved", sessionLabel = quizViewModel.state.sessionLabel
                    ).getOrElse { throw it }
                }
                quizViewModel.applyResolution(resolution)

                fontScale = uiPreferences.fontScale()
                fontFamily = uiPreferences.fontFamily()
                headerMm = uiPreferences.headerMm()
                val built = build()
                if (isFinishing || isDestroyed) return@launch
                setContentView(built)
                AdaptiveTypographyManager.apply(built)
                AdaptiveLayoutManager.install(this@QuizActivity, built, topExtraDp = 0, bottomExtraDp = 0) { m ->
                    val density=resources.displayMetrics.density
                    val compactHeight=m.heightPx/density < 620f
                    if (built.childCount >= 5) {
                        val top=built.getChildAt(0); val info=built.getChildAt(1)
                        top.layoutParams=top.layoutParams.apply{height=dp(if(compactHeight) 52 else if(m.windowClass==AdaptiveLayoutManager.WindowClass.EXPANDED) 60 else 56)}
                        info.layoutParams=info.layoutParams.apply{height=dp(if(compactHeight) 36 else 38)}
                        top.requestLayout(); info.requestLayout()
                    }
                }
                // Full question content is deliberately loaded off the UI thread.
                show()
                TransitionCoordinator.install(this@QuizActivity)
                quizViewModel.persistSessionCursor()
                StudyEventSpine.publishAsync(StudyEventSpine.Event("quiz_opened", quizViewModel.state.currentQuestion?.stableKey, "quiz"))
                if (quizViewModel.state.examMode && quizViewModel.state.examDurationMinutes > 0) startExamTimer(quizViewModel.state.examDurationMinutes)
                else if (!quizViewModel.state.examMode) startGuidanceTimer()
            } catch (t: Exception) {
                if (!isFinishing && !isDestroyed) {
                    showFatalError("Unable to open this question. The QBank data is still safe.\n\n${t.javaClass.simpleName}: ${t.message ?: "unknown error"}")
                }
            }
        }
    }

    /** Read navigation extras defensively. Older builds may have stored IDs as numeric extras. */
    private fun extraText(key: String): String? = when (val v = intent.extras?.get(key)) {
        null -> null
        is String -> v
        is CharSequence -> v.toString()
        is Number -> v.toString()
        else -> v.toString()
    }

    private fun extraLong(key: String): Long = when (val v = intent.extras?.get(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: -1L
        else -> -1L
    }

    private fun extraInt(key: String, fallback: Int): Int = when (val v = intent.extras?.get(key)) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: fallback
        else -> fallback
    }

    private fun showFatalError(message: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background=ThemeManager.backgroundDrawable(this@QuizActivity)
        }
        val titleView = TextView(this).apply {
            text = "Could not open question"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(this@QuizActivity))
            gravity = Gravity.CENTER
        }
        val detail = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(ThemeManager.muted(this@QuizActivity))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(20))
        }
        val back = Button(this).apply {
            text = "← Back"
            setOnClickListener { finish() }
        }
        root.addView(titleView, LinearLayout.LayoutParams(-1, -2))
        root.addView(detail, LinearLayout.LayoutParams(-1, -2))
        root.addView(back, LinearLayout.LayoutParams(-2, dp(52)))
        setContentView(root)
    }

    private fun startGuidanceTimer(){
        if(quizViewModel.state.examMode || quizViewModel.state.questionCount<=0) return
        guidanceRemainingMs=guidanceSecondsPerQuestion*1000L
        updateGuidanceTimer()
        quizTimers.startGuidance(guidanceRemainingMs)
    }
    private fun updateGuidanceTimer(){
        if(!::counter.isInitialized || quizViewModel.state.examMode) return
        val sec=(guidanceRemainingMs/1000).coerceAtLeast(0)
        val label=if(sec>0) String.format("%02d:%02d",sec/60,sec%60) else "Overtime"
        counter.text="Question ${quizViewModel.state.position+1} / ${quizViewModel.state.questionCount}  •  Guide $label"
    }

    private fun startExamTimer(minutes:Int){
        quizTimers.cancelExam()
        quizViewModel.setExamRemainingMs(minutes*60_000L)
        updateExamTimer()
        quizTimers.startExam(quizViewModel.state.examRemainingMs)
    }
    private fun updateExamTimer(){
        if (!::counter.isInitialized) return
        val sec=(quizViewModel.state.examRemainingMs/1000).coerceAtLeast(0)
        val text=String.format("%02d:%02d",sec/60,sec%60)
        counter.text="${quizViewModel.state.position+1}/${quizViewModel.state.questionCount}  •  $text"
    }
    private fun submitExam(auto:Boolean=false){
        quizTimers.cancelExam()
        val total=quizViewModel.state.examAnswers.size
        val correct=quizViewModel.state.examAnswers.values.count{it}
        val wrong=total-correct
        val unanswered=(quizViewModel.state.questionCount-total).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(if(auto) "Time is up" else "Exam submitted")
            .setMessage("Correct: $correct\nWrong: $wrong\nUnanswered: $unanswered\nAttempted: $total/${quizViewModel.state.questionCount}")
            .setPositiveButton("Done"){_,_->finish()}
            .setCancelable(false).show()
    }
        override fun onDestroy() {
        imageExecutor.close(); prefetchExecutor.close(); quizTimers.close()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { gestureDownX = ev.rawX; gestureDownY = ev.rawY }
            MotionEvent.ACTION_UP -> {
                val dx = ev.rawX - gestureDownX
                val dy = ev.rawY - gestureDownY
                if (kotlin.math.abs(dx) > dp(56) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.15f) {
                    if (dx < 0) {
                        if (quizViewModel.state.position < quizViewModel.state.questionCount - 1) {
                            recordCurrentTime(); quizViewModel.moveToPosition(quizViewModel.state.position + 1); show(); startGuidanceTimer()
                        } else if (!quizViewModel.state.examMode) {
                            finishStudySession()
                        } else {
                            submitExam(false)
                        }
                        return true
                    }
                    if (dx > 0 && quizViewModel.state.position > 0) { recordCurrentTime(); quizViewModel.moveToPosition(quizViewModel.state.position - 1); show(); startGuidanceTimer(); return true }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        sessionLifecycle.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        sessionLifecycle.onResume()
    }

    override fun onStop() {
        sessionLifecycle.onStop()
        quizTimers.cancelGuidance()
        quizTimers.cancelExam()
        super.onStop()
    }



    private fun recordCurrentTime() {
        val key = quizViewModel.state.currentQuestion?.stableKey
        if (questionStartedAt <= 0L || key.isNullOrBlank() || timeRecordedForKey == key) return
        quizViewModel.recordElapsedTime(key, (SystemClock.elapsedRealtime() - questionStartedAt).coerceAtLeast(0L))
        timeRecordedForKey = key
    }



    private fun build(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background=ThemeManager.backgroundDrawable(this@QuizActivity)
            clipChildren = false
        }

        // Compact, camera-safe header: no full-width colored slab and no oversized controls.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(6))
            background=ThemeManager.backgroundDrawable(this@QuizActivity)
            elevation = dp(1).toFloat()
        }
        val back = headerButton("‹", 30f) { finish() }
        back.background = rounded(ThemeManager.elevated(this@QuizActivity), 14f)
        back.setTextColor(ThemeManager.text(this@QuizActivity))
        top.addView(back, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(10), 0) })

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tv = TextView(this).apply {
            text = quizViewModel.state.title
            textSize = 16.5f
            setTypeface(Typeface.create(fontFamily, Typeface.BOLD))
            setTextColor(ThemeManager.text(this@QuizActivity))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBox.addView(tv, LinearLayout.LayoutParams(-1, dp(24)))
        val subRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bankNameView = TextView(this).apply {
            text = quizViewModel.state.bankName
            textSize = 10.5f
            setTextColor(ThemeManager.muted(this@QuizActivity))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        subRow.addView(bankNameView, LinearLayout.LayoutParams(0, dp(20), 1f))
        sectionChip = TextView(this).apply {
            textSize = 9.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.accent(this@QuizActivity))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            background = rounded(ThemeManager.explanationBg(this@QuizActivity), 10f)
        }
        subRow.addView(sectionChip, LinearLayout.LayoutParams(-2, dp(20)).apply { setMargins(dp(6), 0, 0, 0) })
        titleBox.addView(subRow, LinearLayout.LayoutParams(-1, dp(20)))
        top.addView(titleBox, LinearLayout.LayoutParams(0, dp(50), 1f))

        // Settings intentionally does not live in the QBank title header. Keeping it beside
        // the question counter makes it independent of the device notification/cutout area.
        if (quizViewModel.state.examMode) {
            val submit = headerButton("✓", 18f) { submitExam(false) }
            submit.background = rounded(ThemeManager.elevated(this@QuizActivity), 14f)
            submit.setTextColor(ThemeManager.accent(this@QuizActivity))
            submit.contentDescription="Submit exam"
            top.addView(submit, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(dp(6), 0, 0, 0) })
        }
        root.addView(top, LinearLayout.LayoutParams(-1, dp(56)))

        // Lightweight progress rail. It is visually separate from the content and does not
        // consume the large vertical band used by the previous header/info combination.
        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(3), dp(18), dp(4))
            setBackgroundColor(ThemeManager.panel(this@QuizActivity))
        }
        counter = TextView(this).apply {
            textSize = 12.5f
            setTypeface(Typeface.create(fontFamily, Typeface.BOLD))
            setTextColor(ThemeManager.muted(this@QuizActivity))
            gravity = Gravity.CENTER_VERTICAL
        }
        infoRow.addView(counter, LinearLayout.LayoutParams(0, dp(30), 1f))
        settingsButton = TextView(this).apply {
            text = "⚙"
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.accent(this@QuizActivity))
            background = rounded(ThemeManager.elevated(this@QuizActivity), 11f)
            contentDescription = "Question settings"
            setOnClickListener { showSettingsMenu(this) }
        }
        infoRow.addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(6),0,dp(6),0) })
        jumpButton = TextView(this).apply {
            textSize = 12.5f
            setTypeface(Typeface.create(fontFamily, Typeface.BOLD))
            setTextColor(ThemeManager.accent(this@QuizActivity))
            gravity = Gravity.CENTER
            background = rounded(ThemeManager.explanationBg(this@QuizActivity), 11f)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { showQuestionJumpMenu(this) }
            contentDescription = "Jump to question"
        }
        infoRow.addView(jumpButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(10), 0, 0, 0) })
        root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(38)))

        scroll = LockedScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(ThemeManager.panel(this@QuizActivity))
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(50))
            setBackgroundColor(ThemeManager.panel(this@QuizActivity))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        result = TextView(this).apply {
            setPadding(dp(18), dp(6), dp(18), dp(6))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(ThemeManager.panel(this@QuizActivity))
            visibility = View.GONE
        }
        root.addView(result, LinearLayout.LayoutParams(-1, dp(24)))

        // Floating-style study dock: compact, balanced controls with a single visual primary action.
        val actionWrap = FrameLayout(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background=ThemeManager.backgroundDrawable(this@QuizActivity)
        }
        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable().apply {
                setColor(ThemeManager.elevated(this@QuizActivity))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(48,61,75) else Color.rgb(222,227,231))
            }
            elevation = dp(4).toFloat()
        }
        val previous = TextView(this).apply {
            text = "‹"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.muted(this@QuizActivity))
            background = rounded(ThemeManager.panel(this@QuizActivity), 17f)
            contentDescription = "Previous question"
            setGuardedClick { moveQuestion(-1) }
        }
        actionBar.addView(previous, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(5), 0) })
        bookmarkButton = TextView(this).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(this@QuizActivity))
            background = rounded(ThemeManager.panel(this@QuizActivity), 17f)
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = "Bookmark question"
            setOnClickListener { showBookmarkMenu(this) }
        }
        actionBar.addView(bookmarkButton, LinearLayout.LayoutParams(0, dp(42), 1.05f).apply { setMargins(dp(2), 0, dp(5), 0) })
        nextButton = TextView(this).apply {
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(58, 139, 214) else Color.rgb(34, 111, 188))
                cornerRadius = dp(17).toFloat()
            }
            setPadding(dp(14), 0, dp(14), 0)
            contentDescription = "Next question"
            setGuardedClick { advanceQuestion() }
        }
        actionBar.addView(nextButton, LinearLayout.LayoutParams(0, dp(42), 1.35f).apply { setMargins(dp(5), 0, 0, 0) })
        actionWrap.addView(actionBar, FrameLayout.LayoutParams(-1, dp(60)))
        root.addView(actionWrap, LinearLayout.LayoutParams(-1, dp(78)))
        return root
    }

    private fun headerButton(label: String, size: Float, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(31,43,58) else Color.rgb(28,70,103), 12f)
        setOnClickListener { click() }
        isClickable = true
        isFocusable = true
    }

    private fun actionButton(label: String, color: Int, size: Float = 13f, click: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(ThemeManager.text(this@QuizActivity))
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = bookmarkPopupDrawable(color)
        elevation = dp(3).toFloat()
        setPadding(dp(8), 0, dp(8), 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun optionCard(q: Question, option: Option, answered: Boolean) {
        val correct = option.correct ||
            q.correctAnswer?.trim()?.equals(option.label.trim(), true) == true ||
            q.correctAnswer?.trim()?.equals(option.label.trim() + ".", true) == true
        val selected = answered && quizViewModel.selectedAnswer(q.stableKey)?.equals(option.label, true) == true
        val bg = when {
            !answered -> ThemeManager.optionBg(this@QuizActivity)
            correct -> ThemeManager.correctBg(this@QuizActivity)
            selected -> ThemeManager.wrongBg(this@QuizActivity)
            else -> ThemeManager.elevated(this@QuizActivity)
        }
        val fg = when {
            !answered -> ThemeManager.optionText(this@QuizActivity)
            correct -> ThemeManager.correctText(this@QuizActivity)
            selected -> ThemeManager.wrongText(this@QuizActivity)
            else -> ThemeManager.muted(this@QuizActivity)
        }
        val row = LinearLayout(this).apply {
            tag = "option:${option.label}"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = if (answered) dp(62) else dp(70)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = rounded(bg, 20f)
            isClickable = !answered
            isFocusable = !answered
            if (!answered) setOnClickListener { answer(option.label) }
        }
        val letter = TextView(this).apply {
            text = option.label
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(fg)
            background = rounded(if (!answered) (if (ThemeManager.get(this@QuizActivity) == ThemeManager.AMOLED) Color.BLACK else if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(43,60,79) else Color.rgb(207,224,240)) else bg, 50f)
        }
        row.addView(letter, LinearLayout.LayoutParams(dp(54), dp(54)).apply { setMargins(0, 0, dp(14), 0) })
        val optionBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = TextView(this).apply {
            this.text = toSpanned(option.text)
            textSize = 17.5f * fontScale
            setTypeface(Typeface.create(fontFamily, Typeface.NORMAL))
            setTextColor(fg)
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(0f, 1.16f)
        }
        optionBody.addView(text, LinearLayout.LayoutParams(-1, -2))
        extractImageUris(option.text).forEach { addImage(it, optionBody) }
        row.addView(optionBody, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(2), 0, dp(2)) })
    }

    private fun answer(label: String) {
        val q = quizViewModel.state.currentQuestion ?: return
        recordCurrentTime()
        val outcome = quizViewModel.answer(q, label, quizViewModel.state.practiceMode, quizViewModel.state.examMode)
        if (!outcome.accepted) return
        val correct = outcome.correct
        if (quizViewModel.state.examMode) {
            quizViewModel.recordExamAnswer(q.stableKey, correct)
            quizViewModel.setPracticeAnsweredKey(q.stableKey)
        } else {
            quizViewModel.setPracticeAnsweredKey(if (quizViewModel.state.practiceMode) q.stableKey else null)
        }
        // Do not rebuild the entire question hierarchy after every tap. The old show()
        // path reparsed HTML and recreated every option, which made answer marking feel laggy.
        showAnsweredInPlace(q, label, correct)
    }

    private fun showAnsweredInPlace(q: Question, label: String, correct: Boolean) {
        val dark = ThemeManager.isDark(this@QuizActivity)
        for (i in 0 until content.childCount) {
            val row = content.getChildAt(i) as? LinearLayout ?: continue
            val tag = row.tag?.toString() ?: continue
            if (!tag.startsWith("option:")) continue
            val optionLabel = tag.removePrefix("option:")
            val option = q.options.firstOrNull { it.label.equals(optionLabel, true) } ?: continue
            val isCorrect = option.correct || q.correctAnswer?.trim()?.equals(option.label.trim(), true) == true || q.correctAnswer?.trim()?.equals(option.label.trim()+".", true) == true
            val isSelected = option.label.equals(label, true)
            val bg = when { isCorrect -> ThemeManager.correctBg(this@QuizActivity); isSelected -> ThemeManager.wrongBg(this@QuizActivity); else -> ThemeManager.elevated(this@QuizActivity) }
            val fg = when { isCorrect -> ThemeManager.correctText(this@QuizActivity); isSelected -> ThemeManager.wrongText(this@QuizActivity); else -> ThemeManager.muted(this@QuizActivity) }
            row.background = rounded(bg,20f); row.isClickable=false; row.isFocusable=false
            (row.getChildAt(0) as? TextView)?.apply { setTextColor(fg); background=rounded(bg,50f) }
            (row.getChildAt(1) as? TextView)?.setTextColor(fg)
        }
        if (::bookmarkButton.isInitialized) updateBookmarkButton(q, bookmarkButton)
        // Answer status is communicated directly on the option cards. Keep the bottom
        // action bar clean: no redundant "Correct / Answer" banner above Bookmark/Next.
        result.visibility = View.GONE
        scroll.isVerticalScrollBarEnabled = true
        // Keep answer feedback instant; explanation is added on the next UI turn so the
        // selected state gets a frame before potentially expensive HTML parsing/layout.
        content.post {
            if (isFinishing || isDestroyed || quizViewModel.state.currentQuestion?.stableKey != q.stableKey) return@post
            appendAnswerDetails(q, !correct)
        }
    }

    private fun appendAnswerDetails(q: Question, wrong: Boolean) {
        // Avoid duplicate explanation if a rapid lifecycle callback already appended it.
        if (content.findViewWithTag<View>("explanation-panel:${q.stableKey}") != null) return
        if (wrong) {
            val why = TextView(this).apply {
                text = quizViewModel.mistakeType(q.stableKey)?.let { "Why I missed it: $it  •  Tap to change" } ?: "🧠 Why did I miss this?"
                textSize = 12.5f * fontScale; setTypeface(null, Typeface.BOLD); setTextColor(ThemeManager.accent(this@QuizActivity)); setPadding(dp(4),dp(10),dp(4),dp(4)); setOnClickListener{showMistakeTypeDialog(q)}
            }
            content.addView(why,LinearLayout.LayoutParams(-1,-2)); why.tag="explanation-why:${q.stableKey}"
        }
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(4));background=rounded(ThemeManager.explanationBg(this@QuizActivity),16f);tag="explanation-panel:${q.stableKey}"}
        panel.addView(TextView(this).apply{text="EXPLANATION";textSize=12.5f*fontScale;setTypeface(Typeface.create(fontFamily,Typeface.BOLD));setTextColor(ThemeManager.explanationTitle(this@QuizActivity));setLetterSpacing(.06f)},LinearLayout.LayoutParams(-1,dp(28)))
        content.addView(panel,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(10),0,dp(2))})
        addHtmlText(q.explanation ?: "<p>No explanation available.</p>",17f*fontScale,false,ThemeManager.text(this@QuizActivity),q.id)
        q.explanationImages.forEach{addImage(it)}
        val flashcardCta = TextView(this).apply {
            text = "MAKE FLASHCARD  •  ANSWER ONLY"
            textSize = 11.5f * fontScale
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.accent(this@QuizActivity))
            background = GradientDrawable().apply {
                setColor(ThemeManager.elevated(this@QuizActivity))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), ThemeManager.accent(this@QuizActivity))
            }
            setPadding(dp(12), 0, dp(12), 0)
            contentDescription = "Make flashcard from this question"
            setOnClickListener {
                quizViewModel.createFlashcard(q.id, "Manual • ${if (wrong) "Wrong" else "Solved"}") { r ->
                    Toast.makeText(this@QuizActivity, if (r.created > 0) "Flashcard created • answer-only reveal" else "Flashcard already exists", Toast.LENGTH_SHORT).show()
                }
            }
        }
        content.addView(flashcardCta, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(12), 0, dp(18)) })
    }

    private fun show() {
        content.removeAllViews()
        if (quizViewModel.state.questionCount == 0) {
            counter.text = "No questions imported"
            addHtmlText("<h2>No questions</h2><p>Import a QBank HTML file first.</p>", 20f)
            result.visibility = View.GONE
            scroll.locked = true
            return
        }
        val key = if (quizViewModel.state.collectionMode) "id:${quizViewModel.state.collectionQuestionIds.getOrNull(quizViewModel.state.position) ?: -1L}" else "${quizViewModel.state.testId}:${quizViewModel.state.position}"
        val q = synchronized(questionCache) { questionCache[key] }
        if (q == null) {
            counter.text = "Loading question ${quizViewModel.state.position + 1} / ${quizViewModel.state.questionCount}…"
            result.visibility = View.GONE
            questionLoadToken++
            val token = questionLoadToken
            prefetchExecutor.execute {
                val started = SystemClock.elapsedRealtime()
                val loaded = runCatching {
                    if (quizViewModel.state.collectionMode) quizViewModel.state.collectionQuestionIds.getOrNull(quizViewModel.state.position)?.let { quizViewModel.questionById(it) }
                    else quizViewModel.questionAt(quizViewModel.state.testId, quizViewModel.state.position)
                }.getOrNull()
                val latency = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
                PerformanceManager.reportLatency(latency)
                if (AppManagers.isReady()) AppManagers.adaptive.onEvent("question_opened", latency)
                runOnUiThread {
                    if (isFinishing || isDestroyed || token != questionLoadToken) return@runOnUiThread
                    if (loaded != null) {
                        synchronized(questionCache) { questionCache[key] = loaded }
                        show()
                    } else {
                        counter.text = "Question unavailable"
                    }
                }
            }
            return
        }
        recordCurrentTime()
        quizViewModel.setCurrentQuestion(q)
        questionStartedAt = SystemClock.elapsedRealtime()
        timeRecordedForKey = null
        quizViewModel.persistPosition()
        val status = quizViewModel.progressStatus(q.stableKey)
        val answered = if (quizViewModel.state.examMode) quizViewModel.state.examAnswers.containsKey(q.stableKey) else if (quizViewModel.state.practiceMode) quizViewModel.state.practiceAnsweredKey == q.stableKey else status != null
        if(quizViewModel.state.examMode) updateExamTimer() else updateGuidanceTimer()
        jumpButton.text = (quizViewModel.state.position + 1).toString()
        // The question area must remain scrollable even before answering, especially for image-based questions.
        // Explanations are only added after an answer, so there is nothing to reveal prematurely.
        scroll.locked = false
        scroll.isVerticalScrollBarEnabled = answered
        updateSectionChip(q, status)

        updateBookmarkButton(q, bookmarkButton)
        updateNextButton()

        addHtmlText(q.text, 20f * fontScale, true)
        q.questionImages.forEach { addImage(it) }
        val geminiContext = TextView(this).apply {
            text = "✦  GEMINI + BEN • EXAM CONTEXT"
            textSize = 11.5f * fontScale
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.accent(this@QuizActivity))
            background = rounded(ThemeManager.explanationBg(this@QuizActivity), 14f)
            contentDescription = "Get Gemini and Ben exam context for this question"
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setOnClickListener { showGeminiQuestionContext(q) }
        }
        content.addView(geminiContext, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(10), 0, dp(8)) })
        q.options.forEach { optionCard(q, it, answered) }

        if (answered && !quizViewModel.state.examMode) {
            // Do not show a redundant answer-status banner; the highlighted options
            // already communicate correct/wrong state.
            result.visibility = View.GONE
            if (status == "wrong") {
                val why = TextView(this).apply {
                    text = quizViewModel.mistakeType(q.stableKey)?.let { "Why I missed it: $it  •  Tap to change" } ?: "🧠 Why did I miss this?"
                    textSize = 12.5f * fontScale
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ThemeManager.accent(this@QuizActivity))
                    setPadding(dp(4), dp(10), dp(4), dp(4))
                    setOnClickListener { showMistakeTypeDialog(q) }
                }
                content.addView(why, LinearLayout.LayoutParams(-1, -2))
            }
            val explanationPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(4))
                background = rounded(ThemeManager.explanationBg(this@QuizActivity), 16f)
            }
            val explanationTitle = TextView(this).apply {
                text = "EXPLANATION"
                textSize = 12.5f * fontScale
                setTypeface(Typeface.create(fontFamily, Typeface.BOLD))
                setTextColor(ThemeManager.explanationTitle(this@QuizActivity))
                setLetterSpacing(0.06f)
            }
            explanationPanel.addView(explanationTitle, LinearLayout.LayoutParams(-1, dp(28)))
            content.addView(explanationPanel, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(10), 0, dp(2)) })
            addHtmlText(q.explanation ?: "<p>No explanation available.</p>", 17f * fontScale, false, ThemeManager.text(this@QuizActivity), q.id)
            q.explanationImages.forEach { addImage(it) }
        } else {
            result.visibility = View.GONE
        }
        AdaptiveTypographyManager.apply(content)
        scroll.post { scroll.scrollTo(0, 0) }
        prefetchNextQuestion()
    }

    private fun prefetchNextQuestion() {
        // Adaptive controller may safely choose a rolling window of 1..3 questions.
        val depth = if (AppManagers.isReady()) PerformanceManager.effectivePrefetchDepth() else 2
        val targets = (1..depth).map { quizViewModel.state.position + it }.filter { it < quizViewModel.state.questionCount }
        if (targets.isEmpty()) return
        prefetchExecutor.execute {
            targets.forEach { target ->
                runCatching {
                    val key = if (quizViewModel.state.collectionMode) "id:${quizViewModel.state.collectionQuestionIds.getOrNull(target) ?: return@forEach}" else "${quizViewModel.state.testId}:$target"
                    synchronized(questionCache) { if (questionCache.containsKey(key)) return@runCatching }
                    val q = if (quizViewModel.state.collectionMode) quizViewModel.state.collectionQuestionIds.getOrNull(target)?.let { quizViewModel.questionById(it) } else quizViewModel.questionAt(quizViewModel.state.testId,target)
                    if (q != null) synchronized(questionCache) { questionCache[key] = q }
                }
            }
        }
    }

    private fun updateSectionChip(q: Question, status: String?) {
        val bookmark = quizViewModel.bookmark(q.stableKey)
        val raw = quizViewModel.state.sectionLabel?.trim()?.takeIf { it.isNotBlank() && !it.equals("practice", true) }
            ?: bookmark?.let { bookmarkLabel(it) }
            ?: when {
                status == "wrong" -> "Wrong"
                status == "correct" -> "Solved"
                else -> "Unsolved"
            }
        sectionChip.text = raw.uppercase()
        val bg = when (raw.lowercase()) {
            "important" -> ThemeManager.bookmarkFill(this, "important")
            "revise" -> ThemeManager.bookmarkFill(this, "revise")
            "doubt" -> ThemeManager.bookmarkFill(this, "doubt")
            "favourite", "favorite" -> ThemeManager.bookmarkFill(this, "favorite")
            "wrong" -> ThemeManager.wrongBg(this)
            "solved" -> ThemeManager.correctBg(this)
            else -> ThemeManager.explanationBg(this)
        }
        sectionChip.setTextColor(when (raw.lowercase()) {
            "wrong" -> ThemeManager.wrongText(this)
            "solved" -> ThemeManager.correctText(this)
            "important", "revise", "doubt", "favourite", "favorite" -> ThemeManager.bookmarkText(this, raw)
            else -> ThemeManager.accent(this)
        })
        sectionChip.background = rounded(bg, 10f)
    }

    private fun applyHeaderHeight() {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) as? ViewGroup
        val header = root?.getChildAt(0)
        if (header != null) {
            header.minimumHeight = headerHeightPx()
            val lp = header.layoutParams
            lp.height = headerHeightPx()
            header.layoutParams = lp
            header.requestLayout()
        }
    }

    private fun headerHeightPx(): Int {
        val dpi = resources.displayMetrics.densityDpi.toFloat()
        val px = dpi * (headerMm / 25.4f)
        return px.toInt().coerceIn(dp(44), dp(60))
    }

    private fun showQuestionJumpMenu(anchor: View) {
        if (quizViewModel.state.questionCount == 0) return
        val popup = PopupWindow(this).apply {
            width = dp(290)
            height = dp(190)
            isFocusable = true
            isOutsideTouchable = true
            elevation = dp(14).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(ThemeManager.elevated(this@QuizActivity)); cornerRadius = dp(20).toFloat(); setStroke(dp(1), if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(55,69,86) else Color.rgb(210,223,231)) }
        }
        val heading = TextView(this).apply {
            text = "Jump to question"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.text(this@QuizActivity))
        }
        box.addView(heading, LinearLayout.LayoutParams(-1, dp(28)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 17f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setSelectAllOnFocus(true)
            hint = "1 – ${quizViewModel.state.questionCount}"
            setPadding(dp(8), 0, dp(8), 0)
            background = rounded(ThemeManager.optionBg(this@QuizActivity), 12f)
        }
        input.setText((quizViewModel.state.position + 1).toString())
        row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        val go = actionButton("GO", Color.rgb(27, 128, 91), 14f) {
            val n = input.text.toString().toIntOrNull()
            if (n != null && n in 1..quizViewModel.state.questionCount) {
                quizViewModel.moveToPosition(n - 1)
                popup.dismiss()
                show()
            } else input.error = "Enter 1–${quizViewModel.state.questionCount}"
        }
        row.addView(go, LinearLayout.LayoutParams(dp(78), dp(52)).apply { setMargins(dp(10), 0, 0, 0) })
        box.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(8), 0, 0) })
        val hint = TextView(this).apply {
            text = "Swipe left/right to move one question"
            textSize = 12f
            setTextColor(ThemeManager.muted(this@QuizActivity))
            gravity = Gravity.CENTER
        }
        box.addView(hint, LinearLayout.LayoutParams(-1, dp(28)).apply { setMargins(0, dp(5), 0, 0) })
        popup.contentView = box
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        popup.showAtLocation(scroll, Gravity.TOP or Gravity.END, dp(12), (loc[1] + dp(48)).coerceAtLeast(dp(50)))
        if (AnimationPolicy.enabled(this)) box.startAnimation(ScaleAnimation(.94f, 1f, .94f, 1f, 1f, 0f).apply { duration = 160 })
        input.requestFocus()
        input.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupWindow(this).apply {
            width = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost(dp(390))
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isFocusable = true
            isOutsideTouchable = true
            elevation = dp(20).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        }
        val dark = ThemeManager.isDark(this)
        val border = if (dark) Color.rgb(48,61,75) else Color.rgb(216,224,231)
        val scrollPanel = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(ThemeManager.elevated(this@QuizActivity))
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), border)
            }
        }
        fun heading(text: String, sub: String? = null) {
            box.addView(TextView(this).apply {
                this.text = text
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ThemeManager.text(this@QuizActivity))
            }, LinearLayout.LayoutParams(-1, dp(30)))
            if (sub != null) box.addView(TextView(this).apply {
                this.text = sub
                textSize = 11.5f
                setTextColor(ThemeManager.muted(this@QuizActivity))
                setPadding(0, dp(1), 0, dp(12))
            }, LinearLayout.LayoutParams(-1, dp(28)))
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.muted(this@QuizActivity))
            setPadding(0, dp(10), 0, dp(6))
        }
        fun tile(text: String, selected: Boolean = false, click: () -> Unit): TextView = TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (selected) ThemeManager.accent(this@QuizActivity) else ThemeManager.text(this@QuizActivity))
            background = GradientDrawable().apply {
                setColor(if (selected) ThemeManager.explanationBg(this@QuizActivity) else ThemeManager.panel(this@QuizActivity))
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), if (selected) ThemeManager.accent(this@QuizActivity) else border)
            }
            setOnClickListener { click() }
        }
        fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEachIndexed { i, v ->
                addView(v, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i > 0) setMargins(dp(6), 0, 0, 0) })
            }
        }

        heading("Question settings", "Tune the reading surface without leaving your question.")

        box.addView(label("TEXT SIZE  •  ${"%.0f".format(fontScale * 100)}%"))
        val fs = SeekBar(this).apply {
            max = 45
            progress = ((fontScale - .85f) / .01f).toInt().coerceIn(0, 45)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        fontScale = (.85f + p * .01f).coerceIn(.85f, 1.30f)
                        uiPreferences.setFontScale(fontScale)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { popup.dismiss(); show() }
            })
            progressTintList = android.content.res.ColorStateList.valueOf(ThemeManager.accent(this@QuizActivity))
        }
        box.addView(fs, LinearLayout.LayoutParams(-1, dp(34)))

        box.addView(label("FONT"))
        val fonts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("sans-serif" to "Modern", "serif" to "Textbook", "sans-serif-condensed" to "Compact").forEachIndexed { i, pair ->
            val b = tile(pair.second, fontFamily == pair.first) {
                fontFamily = pair.first
                uiPreferences.setFontFamily(pair.first)
                popup.dismiss(); show()
            }
            fonts.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i > 0) setMargins(dp(6),0,0,0) })
        }
        box.addView(fonts, LinearLayout.LayoutParams(-1, dp(40)))

        box.addView(label("HEADER DENSITY"))
        val heights = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(8f to "Compact", 10f to "Balanced", 12f to "Roomy").forEachIndexed { i, pair ->
            val b = tile(pair.second, kotlin.math.abs(headerMm - pair.first) < .01f) {
                headerMm = pair.first
                uiPreferences.setHeaderMm(headerMm)
                popup.dismiss(); quizViewModel.persistPosition(); applyHeaderHeight()
            }
            heights.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i > 0) setMargins(dp(6),0,0,0) })
        }
        box.addView(heights, LinearLayout.LayoutParams(-1, dp(40)))

        box.addView(label("APPEARANCE"))
        val themes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            ThemeManager.LIGHT to "Light",
            ThemeManager.DARK to "Dark",
            ThemeManager.SEPIA to "Sepia",
            ThemeManager.AMOLED to "AMOLED",
            ThemeManager.MIDNIGHT to "Midnight",
            ThemeManager.COSMOS to "Cosmos",
            ThemeManager.AVATAR to "Pandora"
        ).forEachIndexed { i, pair ->
            val b = tile(pair.second, ThemeManager.get(this) == pair.first) {
                ThemeManager.set(this, pair.first)
                intent.putExtra("position", quizViewModel.state.position)
                quizViewModel.state.currentQuestion?.let { intent.putExtra("questionId", it.id) }
                popup.dismiss(); recreate()
            }
            themes.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i > 0) setMargins(dp(6),0,0,0) })
        }
        box.addView(themes, LinearLayout.LayoutParams(-1, dp(40)))

        val autoNote = tile("CREATE AUTO NOTE", false) {
            quizViewModel.state.currentQuestion?.let { q ->
                quizViewModel.captureQuestionToKnowledge(q, "Question settings") { r ->
                    if (AppManagers.isReady()) AppManagers.adaptive.onEvent("knowledge_captured", (r.tablesSaved + r.imagesSaved).toLong())
                    Toast.makeText(this, "Auto note saved • ${r.tablesSaved} tables • ${r.imagesSaved} images", Toast.LENGTH_SHORT).show()
                }
            }
        }
        box.addView(autoNote, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(0,dp(10),0,dp(4)) })
        scrollPanel.addView(box, ViewGroup.LayoutParams(-1, -2))
        popup.contentView = scrollPanel
        val x = ((resources.displayMetrics.widthPixels - popup.width) / 2).coerceAtLeast(dp(8))
        val safeTop = (scroll.rootView.getTag(R.id.adaptiveSafeTop) as? Int) ?: dp(32)
        val y = safeTop + dp(8)
        popup.showAtLocation(scroll, Gravity.TOP or Gravity.START, x, y)
        if (AnimationPolicy.enabled(this)) box.startAnimation(ScaleAnimation(.96f,1f,.96f,1f,.5f,.08f).apply{duration=180})
    }

    private fun updateNextButton() {
        if (!::nextButton.isInitialized) return
        nextButton.text = if (quizViewModel.state.position >= quizViewModel.state.questionCount - 1) {
            if (quizViewModel.state.examMode) "Submit  →" else "Finish  →"
        } else {
            "Next  →"
        }
        nextButton.contentDescription = if (quizViewModel.state.position >= quizViewModel.state.questionCount - 1) "Finish question session" else "Next question"
    }

    private fun moveQuestion(delta: Int) {
        if (quizViewModel.state.questionCount <= 0 || delta == 0) return
        val target = quizViewModel.targetPosition(quizViewModel.state.position, delta)
        if (target == quizViewModel.state.position) {
            Toast.makeText(this, if (delta < 0) "Already at the first question" else "Already at the last question", Toast.LENGTH_SHORT).show()
            return
        }
        recordCurrentTime()
        quizViewModel.moveToPosition(target)
        show()
        startGuidanceTimer()
    }

    private fun advanceQuestion() {
        if (quizViewModel.state.questionCount <= 0) return
        recordCurrentTime()
        if (quizViewModel.targetPosition(quizViewModel.state.position, 1) != quizViewModel.state.position) {
            quizViewModel.moveToPosition(quizViewModel.targetPosition(quizViewModel.state.position, 1))
            show()
            startGuidanceTimer()
        } else if (quizViewModel.state.examMode) {
            submitExam(false)
        } else {
            finishStudySession()
        }
    }

    private fun finishStudySession() {
        recordCurrentTime()
        quizViewModel.persistPosition()
        val attempted = quizViewModel.state.position + 1
        val total = quizViewModel.state.questionCount
        AlertDialog.Builder(this)
            .setTitle("Question bank complete")
            .setMessage("You reached the end of this study set.\n\nQuestions reached: $attempted/$total")
            .setNegativeButton("Stay") { _, _ -> }
            .setPositiveButton("Finish") { _, _ ->
                StudyEventSpine.publishAsync(StudyEventSpine.Event("quiz_finished", quizViewModel.state.currentQuestion?.stableKey, "quiz"))
                finish()
            }
            .show()
    }

    private fun updateBookmarkButton(q: Question, button: TextView) {
        val value = quizViewModel.bookmark(q.stableKey)
        if (value.isNullOrBlank()) {
            button.text = "🔖  Bookmark"
            button.setTextColor(ThemeManager.text(this@QuizActivity))
            button.background = rounded(ThemeManager.elevated(this@QuizActivity), 16f)
        } else {
            button.text = "🔖  ${bookmarkLabel(value)}"
            button.setTextColor(ThemeManager.bookmarkText(this, value))
            button.background = rounded(ThemeManager.bookmarkFill(this, value), 16f)
        }
    }

    private fun bookmarkLabel(value: String) = when (value) {
        "important" -> "Important"
        "revise" -> "Revise"
        "doubt" -> "Doubt"
        "favorite" -> "Favourite"
        else -> "Bookmark"
    }

    private fun addHtmlText(html: String?, size: Float, bold: Boolean = false, color: Int? = null, noteQuestionId: Long? = null) {
        val raw = html ?: ""
        val imageSrcs = Regex("<img\\s+[^>]*(?:src|data-src|data-original|data-lazy-src)\\s*=\\s*[\"']([^\"']+)[^>]*>", RegexOption.IGNORE_CASE)
            .findAll(raw).map { it.groupValues[1] }.toList() +
            Regex("background-image\\s*:\\s*url\\s*\\(\\s*[\"']?([^\\)\"']+)[\"']?\\s*\\)", RegexOption.IGNORE_CASE)
                .findAll(raw).map { it.groupValues[1] }.toList()
        var cleaned = raw.replace(Regex("<img\\s+[^>]*>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("(?i)data:image/[a-z0-9.+-]+;base64,[A-Za-z0-9+/=\\r\\n]+"), "")
        cleaned = cleaned.replace(Regex("\\s+face\\s*=\\s*['\"][^'\"]*['\"]", RegexOption.IGNORE_CASE), "")

        if (Regex("<table\\b", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) {
            addRichHtmlWithTables(cleaned, size, bold, color, noteQuestionId)
        } else {
            addSelectableHtmlText(cleaned, size, bold, color, noteQuestionId)
        }
        imageSrcs.forEach { addImage(it) }
    }

    /** Render HTML fragments and real Android TableLayouts for Match-the-Following questions. */
    private fun addRichHtmlWithTables(html: String, size: Float, bold: Boolean, color: Int?, noteQuestionId: Long?) {
        val tableRx = Regex("<table\\b[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        var cursor = 0
        for (m in tableRx.findAll(html)) {
            val before = html.substring(cursor, m.range.first)
            if (before.replace(Regex("<[^>]+>"), "").trim().isNotBlank()) addSelectableHtmlText(before, size, bold, color, noteQuestionId)
            addNativeHtmlTable(m.value, size, color)
            cursor = m.range.last + 1
        }
        val after = html.substring(cursor)
        if (after.replace(Regex("<[^>]+>"), "").trim().isNotBlank()) addSelectableHtmlText(after, size, bold, color, noteQuestionId)
    }

    private fun addSelectableHtmlText(html: String, size: Float, bold: Boolean, color: Int?, noteQuestionId: Long?) {
        val t = TextView(this).apply {
            text = toSpanned(html)
            textSize = size
            typeface = Typeface.create(fontFamily, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(color ?: ThemeManager.text(this@QuizActivity))
            setLineSpacing(0f, 1.25f)
            setPadding(0, 0, 0, dp(14))
            setTypeface(Typeface.create(fontFamily, if (bold) Typeface.BOLD else Typeface.NORMAL), if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (noteQuestionId != null) configureNoteSelection(this, noteQuestionId)
        }
        content.addView(t, LinearLayout.LayoutParams(-1, -2))
    }

    private fun configureNoteSelection(t: TextView, noteQuestionId: Long) {
        t.setTextIsSelectable(true)
        t.isLongClickable = true
        t.setCustomSelectionActionModeCallback(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu?.add(0, 0x4E4F5445, 0, "Save to Notes")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (menu?.findItem(android.R.id.copy) == null) menu?.add(0, android.R.id.copy, 1, "Copy")
                return true
            }
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item?.itemId != 0x4E4F5445) return false
                val lo = minOf(t.selectionStart.coerceAtLeast(0), t.selectionEnd.coerceAtLeast(0))
                val hi = maxOf(t.selectionStart.coerceAtLeast(0), t.selectionEnd.coerceAtLeast(0))
                val selected = t.text?.subSequence(lo, hi)?.toString()?.trim().orEmpty()
                if (selected.isNotBlank()) {
                    quizViewModel.appendNote(noteQuestionId, selected)
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Explanation", selected))
                    Toast.makeText(this@QuizActivity, "Selected text added to Notes", Toast.LENGTH_SHORT).show()
                }
                mode?.finish(); return true
            }
            override fun onDestroyActionMode(mode: ActionMode?) {}
        })
    }

    private fun addNativeHtmlTable(tableHtml: String, size: Float, color: Int?) {
        val outer = HorizontalScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, dp(4), 0, dp(16))
        }
        val table = TableLayout(this).apply {
            isStretchAllColumns = false
            isShrinkAllColumns = false
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }
        val rows = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(tableHtml)
        var rowIndex = 0
        for (rowMatch in rows) {
            val tr = TableRow(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val cells = Regex("<(td|th)\\b([^>]*)>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(rowMatch.groupValues[1])
            var cellIndex = 0
            for (cell in cells) {
                val tag = cell.groupValues[1].lowercase()
                val attrs = cell.groupValues[2]
                val inner = cell.groupValues[3]
                val tv = TextView(this).apply {
                    text = toSpanned(inner)
                    textSize = maxOf(13f, size - 1f)
                    setTextColor(color ?: ThemeManager.text(this@QuizActivity))
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    gravity = Gravity.CENTER_VERTICAL
                    if (tag == "th") setTypeface(Typeface.create(fontFamily, Typeface.BOLD))
                    background = GradientDrawable().apply {
                        setColor(if (tag == "th") {
                            if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(38,55,70) else Color.rgb(226,239,247)
                        } else {
                            if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(25,34,44) else if (rowIndex % 2 == 0) Color.WHITE else Color.rgb(247,250,252)
                        })
                        setStroke(dp(1), if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(76,92,108) else Color.rgb(190,201,211))
                    }
                    minWidth = dp(92)
                }
                val colspan = Regex("colspan\\s*=\\s*[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                val lp = TableRow.LayoutParams(dp(110), -2)
                lp.span = colspan.coerceAtLeast(1)
                tr.addView(tv, lp)
                cellIndex++
            }
            if (cellIndex > 0) { table.addView(tr); rowIndex++ }
        }
        if (table.childCount > 0) {
            outer.addView(table, ViewGroup.LayoutParams(-2, -2))
            content.addView(outer, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun extractImageUris(html: String?): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        Regex("<img\\s+[^>]*(?:src|data-src|data-original|data-lazy-src)\\s*=\\s*[\"']([^\"']+)[^>]*>", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { out.add(it.groupValues[1]) }
        Regex("background-image\\s*:\\s*url\\s*\\(\\s*[\"']?([^\\)\"']+)[\"']?\\s*\\)", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { out.add(it.groupValues[1]) }
        return out.toList()
    }

    /**
     * Render QBank media using the Chromium/WebView HTML image pipeline first.
     *
     * This deliberately mirrors how Chrome renders an <img>: the browser resolves
     * the source URL, negotiates the response type and decodes the image. Native
     * BitmapFactory remains a fallback for providers/formats WebView cannot render.
     */
    private fun addImage(uri: String, host: ViewGroup = content) {
        val raw = uri.trim()
        if (raw.isBlank()) return
        val imageTag = "rovex-image:$raw"
        for (i in 0 until host.childCount) if (host.getChildAt(i).tag == imageTag) return

        val web = android.webkit.WebView(this).apply {
            tag = imageTag
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            // QBank exports commonly contain legacy HTTP image URLs.
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    view?.evaluateJavascript("""
                        (function(){
                          var i=document.images[0];
                          if(!i)return;
                          function fit(){
                            var nw=i.naturalWidth||0, nh=i.naturalHeight||0;
                            if(!nw||!nh)return;
                            var w=Math.max(1,window.innerWidth-8);
                            var h=Math.max(40,Math.min(720,nh*(w/nw)));
                            document.body.style.height=Math.ceil(h)+'px';
                          }
                          i.onload=fit; i.onerror=function(){document.body.style.height='40px';};
                          fit(); setTimeout(fit,250); setTimeout(fit,1000);
                        })();
                    """.trimIndent(), null)
                }
            }
        }
        var imageDownAt=0L
        web.setOnTouchListener { _, event ->
            when(event.actionMasked){
                android.view.MotionEvent.ACTION_DOWN -> { imageDownAt=System.currentTimeMillis(); false }
                android.view.MotionEvent.ACTION_UP -> { if(System.currentTimeMillis()-imageDownAt<650L) showImageFullscreen(raw); false }
                else -> false
            }
        }
        web.setOnLongClickListener {
            quizViewModel.state.currentQuestion?.let { q -> showImageSaveMenu(raw, q) }
            true
        }
        host.addView(web, LinearLayout.LayoutParams(-1, dp(300)).apply { setMargins(0, dp(2), 0, dp(2)) })
        // Saving an image + personal cue belongs to My Notes, not the Knowledge Vault.
        // Keep this action visible so the user does not have to discover long-press behavior.
        val saveFill = if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(255, 219, 118) else Color.rgb(255, 240, 188)
        // Keep the foreground contrast-safe even if a custom/theme transition changes the
        // dialog palette underneath this view; never leave dark text on a dark action surface.
        val saveFg = readableForeground(saveFill)
        val saveToNotes = TextView(this).apply {
            text = "SAVE IMAGE + NOTE  →  MY NOTES"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(saveFg)
            background = rounded(saveFill, 12f)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { quizViewModel.state.currentQuestion?.let { q -> showImageNoteEditor(raw, q) } }
            contentDescription = "Save image and note to My Notes"
        }
        host.addView(saveToNotes, LinearLayout.LayoutParams(-1, dp(38)).apply { setMargins(dp(2), 0, dp(2), dp(8)) })

        val relation = parseRelativeImage(raw)
        val imageSrc = relation?.second ?: raw
        val baseUrl = when {
            relation != null -> relation.first
            raw.startsWith("data:image", true) -> "https://rovex.local/"
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            else -> raw.substringBeforeLast('/', raw)
        }
        val safeSrc = android.text.TextUtils.htmlEncode(imageSrc)
        val html = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;padding:0;background:transparent;overflow:hidden}img{display:block;max-width:100%;width:auto;height:auto;margin:0 auto}</style>
            </head><body><img src="$safeSrc" alt="Question image"></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }

    private fun parseRelativeImage(raw: String): Pair<String,String>? {
        if (!raw.startsWith("rovex-rel://image?", true)) return null
        return runCatching {
            val params = raw.substringAfter('?').split('&').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to java.net.URLDecoder.decode(part.substring(i + 1), Charsets.UTF_8.name())
            }.toMap()
            val base = params["base"].orEmpty()
            val path = params["path"].orEmpty()
            if (base.isBlank() || path.isBlank()) null else base to path
        }.getOrNull()
    }

    private fun fallbackImageWebView(image: ImageView, host: ViewGroup, raw: String) {
        if (raw.isBlank()) return
        val index = host.indexOfChild(image)
        if (index < 0) return
        val relation = if (raw.startsWith("rovex-rel://image?")) runCatching {
            val q = raw.substringAfter('?')
            val params = q.split('&').mapNotNull { part ->
                val i = part.indexOf('='); if (i <= 0) null else part.substring(0, i) to java.net.URLDecoder.decode(part.substring(i + 1), Charsets.UTF_8.name())
            }.toMap()
            params["base"] to params["path"]
        }.getOrNull() else null
        val imageSrc = relation?.second ?: raw
        val baseUrl = relation?.first ?: raw
        val web = android.webkit.WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = object : android.webkit.WebViewClient() {}
            setBackgroundColor(Color.TRANSPARENT)
            var tapDownX = 0f
            var tapDownY = 0f
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { tapDownX = event.x; tapDownY = event.y; false }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = event.x - tapDownX; val dy = event.y - tapDownY
                        if (kotlin.math.abs(dx) < dp(18) && kotlin.math.abs(dy) < dp(18)) showImageFullscreen(raw)
                        false
                    }
                    else -> false
                }
            }
        }
        val html = "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body style=\"margin:0;background:transparent;text-align:center;\"><img src=\"${android.text.TextUtils.htmlEncode(imageSrc)}\" style=\"max-width:100%;height:auto;display:inline-block;\"></body></html>"
        host.removeViewAt(index)
        host.addView(web, index, LinearLayout.LayoutParams(-1, dp(220)))
        web.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                view?.evaluateJavascript("(function(){var i=document.images[0];if(!i)return;var h=Math.max(40,Math.min(window.innerHeight*0.8,i.naturalHeight?i.naturalHeight:220));document.body.style.height=h+'px';})()", null)
            }
        }
        web.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }

    private fun showImageFullscreen(raw:String){
        val dlg=Dialog(this).apply{window?.setBackgroundDrawableResource(android.R.color.black)}
        val web=android.webkit.WebView(this).apply{
            settings.javaScriptEnabled=false
            settings.domStorageEnabled=false
            settings.allowFileAccess=true
            settings.allowContentAccess=true
            settings.builtInZoomControls=true
            settings.displayZoomControls=false
            settings.setSupportZoom(true)
            settings.useWideViewPort=true
            settings.loadWithOverviewMode=true
            settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient=object:android.webkit.WebViewClient(){
                override fun onRenderProcessGone(view:android.webkit.WebView?,detail:android.webkit.RenderProcessGoneDetail?):Boolean{runCatching{view?.destroy()};dlg.dismiss();return true}
            }
            setBackgroundColor(Color.BLACK)
            setOnClickListener{dlg.dismiss()}
        }
        dlg.setContentView(web);dlg.show();dlg.window?.setLayout(-1,-1)
        val relation=parseRelativeImage(raw)
        val imageSrc=relation?.second ?: raw
        val baseUrl=relation?.first ?: when{
            raw.startsWith("http://",true)||raw.startsWith("https://",true)->raw.substringBeforeLast('/',raw)
            raw.startsWith("data:image",true)->"https://rovex.local/"
            else->raw.substringBeforeLast('/',raw)
        }
        val safe=android.text.TextUtils.htmlEncode(imageSrc)
        val page="<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=8,user-scalable=yes\"></head><body style=\"margin:0;background:#000;width:100%;height:100vh;display:flex;align-items:center;justify-content:center;overflow:auto;\"><img src=\"$safe\" style=\"max-width:100%;max-height:100%;width:auto;height:auto;object-fit:contain;\"></body></html>"
        web.loadDataWithBaseURL(baseUrl,page,"text/html","UTF-8",null)
    }

    private fun decodeScaledImage(value: String, maxW: Int, maxH: Int): android.graphics.Bitmap? {
        var uri = value.trim().replace("\\/", "/").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        if (uri.startsWith("rovex-rel://image?", true)) return null
        if (uri.startsWith("url(", true)) uri = uri.substringAfter('(').substringBeforeLast(')').trim().trim('"', '\'')
        if (uri.startsWith("//")) uri = "https:" + uri
        if (uri.startsWith("data:image", true)) {
            val comma = uri.indexOf(',')
            if (comma < 0) return null
            val payload = uri.substring(comma + 1)
            // Stream-decode the Base64 image twice: first for dimensions, then at the
            // calculated sample size. Never materialise the full decoded image byte[];
            // large QBank images are a common source of process-death/OOM on phones.
            fun stream() = android.util.Base64InputStream(payload.byteInputStream(Charsets.US_ASCII), android.util.Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, maxW, maxH)
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                inDither = true
            }
            return stream().use { BitmapFactory.decodeStream(it, null, opts) }
        }
        val parsed = Uri.parse(uri)
        fun open(): java.io.InputStream? {
            return when (parsed.scheme?.lowercase()) {
                "https", "http" -> {
                    val c = (java.net.URL(uri).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 20000
                        instanceFollowRedirects = true
                        useCaches = true
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36")
                        setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        setRequestProperty("Accept-Encoding", "identity")
                        setRequestProperty("Connection", "close")
                    }
                    if (c.responseCode !in 200..299) { c.disconnect(); null } else c.inputStream
                }
                "content", "file" -> contentResolver.openInputStream(parsed)
                else -> null
            }
        }
        // For file/content/network images, also avoid readBytes(): bounds are read first,
        // then the stream is reopened and decoded at the required sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, maxW, maxH)
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            inDither = true
        }
        return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun calculateSample(w: Int, h: Int, maxW: Int, maxH: Int): Int {
        var sample = 1
        while (w / (sample * 2) >= maxW && h / (sample * 2) >= maxH) sample *= 2
        while (w / sample > maxW * 2 || h / sample > maxH * 2) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun decodeBytes(data: ByteArray, maxW: Int, maxH: Int): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxW || bounds.outHeight / sample > maxH) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=android.graphics.Bitmap.Config.RGB_565; inScaled=true }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }

    private fun showImageSaveMenu(raw:String, q:Question) {
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(16));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@QuizActivity));cornerRadius=dp(24).toFloat()}}
        root.addView(RovexWaveTextView(this).apply{text="Save this image";textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@QuizActivity))})
        root.addView(TextView(this).apply{text="Keep the image alone, or attach your own memory note so it appears together in My Notes.";textSize=11.5f;setTextColor(ThemeManager.muted(this@QuizActivity));setPadding(0,dp(4),0,dp(12))})
        fun action(title:String,sub:String,fill:Int,fg:Int,click:()->Unit)=TextView(this).apply{text=android.text.SpannableStringBuilder().append(title).append("\n").append(sub);textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(fg);setPadding(dp(13),dp(10),dp(13),dp(10));background=rounded(fill,15f);setOnClickListener{click();dialog.dismiss()}}
        root.addView(action("SAVE IMAGE","Save image only to Knowledge Vault", if(ThemeManager.isDark(this)) Color.rgb(126,190,235) else Color.rgb(220,240,252), if(ThemeManager.isDark(this)) Color.rgb(12,28,40) else Color.rgb(25,57,76)){quizViewModel.saveKnowledgeImage(raw,q.id){ok->Toast.makeText(this,if(ok)"Image saved to Knowledge Vault" else "Could not save this image",Toast.LENGTH_SHORT).show()}} ,LinearLayout.LayoutParams(-1,dp(58)))
        val notesActionFill=if(ThemeManager.isDark(this)) Color.rgb(255,219,118) else Color.rgb(255,240,188)
        root.addView(action("SAVE TO MY NOTES + IMAGE","Write a memory cue and attach this image to My Notes", notesActionFill, readableForeground(notesActionFill)){showImageNoteEditor(raw,q) },LinearLayout.LayoutParams(-1,dp(70)).apply{setMargins(0,dp(8),0,0)})
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((resources.displayMetrics.widthPixels*.92f).toInt(),-2);AdaptiveTypographyManager.apply(root)
    }

    private fun showImageNoteEditor(raw:String,q:Question){
        val input=EditText(this).apply{hint="Write your memory cue, trap, mnemonic…";textSize=15f;minLines=4;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;setTextColor(ThemeManager.text(this@QuizActivity));setHintTextColor(ThemeManager.muted(this@QuizActivity));background=rounded(ThemeManager.elevated(this@QuizActivity),14f);setPadding(dp(12),dp(10),dp(12),dp(10))}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(4),dp(8),0);background=rounded(ThemeManager.dialogBg(this@QuizActivity),20f)}
        box.addView(TextView(this).apply{text="Note attached below saved image";textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@QuizActivity));setPadding(dp(4),dp(4),dp(4),dp(8))})
        box.addView(input,LinearLayout.LayoutParams(-1,dp(140)))
        val d=AlertDialog.Builder(this).setTitle("Save to My Notes").setView(box).setNegativeButton("Cancel",null).setPositiveButton("SAVE TO NOTES",null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val note=input.text.toString().trim()
            if (note.isBlank()) { input.error = "Add a short memory cue, trap or mnemonic"; return@setOnClickListener }
            quizViewModel.saveKnowledgeImageWithNote(raw,q.id,note) { ok ->
                Toast.makeText(this, if(ok) "Image + note saved to My Notes" else "Could not save image + note", Toast.LENGTH_SHORT).show()
            }
            d.dismiss()
        } }
        d.show();d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val saveButton=d.getButton(AlertDialog.BUTTON_POSITIVE)
        val saveButtonFill=if(ThemeManager.isDark(this)) Color.rgb(255,219,118) else Color.rgb(255,240,188)
        saveButton.setTextColor(readableForeground(saveButtonFill))
        saveButton.background=rounded(saveButtonFill,12f)
        saveButton.setPadding(dp(14),0,dp(14),0)
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ThemeManager.text(this))
    }

    private fun showMistakeTypeDialog(q: Question) {
        val choices = listOf(
            "Didn't know" to "The concept or fact was not in recall.",
            "Concept confusion" to "I knew related facts but mixed the underlying concept.",
            "Misread question" to "The stem, qualifier, image or wording was misread.",
            "Silly mistake" to "I knew it, but made an avoidable error.",
            "Changed correct answer" to "The first answer was right; I changed it incorrectly.",
            "Time pressure" to "Time pressure caused the wrong response."
        )
        val current=quizViewModel.mistakeType(q.stableKey)
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(16));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@QuizActivity));cornerRadius=dp(24).toFloat()}}
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(RovexWaveTextView(this).apply{text="Why did I miss this?";textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@QuizActivity))},LinearLayout.LayoutParams(0,dp(44),1f))
        head.addView(TextView(this).apply{text="×";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@QuizActivity));background=rounded(ThemeManager.elevated(this@QuizActivity),14f);setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(44),dp(44)))
        root.addView(head)
        root.addView(TextView(this).apply{text="Tag the reason so Rovex can build a targeted collection for revision.";textSize=11.5f;setTextColor(ThemeManager.muted(this@QuizActivity));setPadding(0,dp(2),0,dp(10))})
        choices.forEachIndexed{idx,pair->
            val selected=pair.first.equals(current,true)
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(10),dp(13),dp(10));background=GradientDrawable().apply{setColor(if(selected)ThemeManager.pastelAccentFill(this@QuizActivity,idx) else ThemeManager.elevated(this@QuizActivity));cornerRadius=dp(15).toFloat();setStroke(dp(1),if(selected)ThemeManager.pastelAccentText(this@QuizActivity,idx) else Color.argb(45,100,120,140))};setOnClickListener{quizViewModel.setMistakeType(q.stableKey,pair.first);dialog.dismiss();show()}}
            card.addView(TextView(this).apply{text=pair.first;textSize=14.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(if(selected)ThemeManager.pastelAccentText(this@QuizActivity,idx) else ThemeManager.text(this@QuizActivity))})
            card.addView(TextView(this).apply{text=pair.second;textSize=11f;setTextColor(if(selected)ThemeManager.pastelAccentText(this@QuizActivity,idx) else ThemeManager.muted(this@QuizActivity));setPadding(0,dp(3),0,0)})
            root.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((resources.displayMetrics.widthPixels*.92f).toInt(),-2);AdaptiveTypographyManager.apply(root)
    }

    private fun showNoteEditor(q:Question){
        val input=EditText(this).apply{setText(quizViewModel.note(q.id) ?: "");setHint("Your memory cue, trap, or mnemonic");inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=4;setPadding(dp(12),dp(10),dp(12),dp(10))}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(4),dp(8),0)};box.addView(input,LinearLayout.LayoutParams(-1,dp(150)))
        AlertDialog.Builder(this).setTitle("Note for this question").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->quizViewModel.saveNote(q.id,input.text.toString().trim());Toast.makeText(this,"Note saved",Toast.LENGTH_SHORT).show()}.show()
    }

    private fun showBookmarkMenu(anchor: View) {
        if (quizViewModel.state.questionCount == 0) return
        val popup = PopupWindow(this).apply {
            width = dp(336)
            height = dp(276)
            isFocusable = true
            isOutsideTouchable = true
            elevation = dp(14).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(ThemeManager.elevated(this@QuizActivity))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(55,69,86) else Color.rgb(205,220,231))
            }
            elevation = dp(8).toFloat()
        }
        val titleView = TextView(this).apply {
            text = "Bookmark category"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.text(this@QuizActivity))
            setPadding(dp(6), dp(2), dp(6), dp(10))
        }
        box.addView(titleView)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val values = listOf(
            "important" to "★  Important",
            "revise" to "↻  Revise",
            "doubt" to "❔  Doubt",
            "favorite" to "♥  Favourite"
        )
        for (r in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..1) {
                val pair = values[r * 2 + c]
                val b = actionButton(pair.second, bookmarkColor(pair.first), 14.5f) {
                    quizViewModel.state.currentQuestion?.let { q ->
                        quizViewModel.setBookmark(q.stableKey, pair.first)
                        // Bookmarking is only a study-state mutation. Notes and flashcards
                        // are deliberate user actions and must never be created as side effects.
                        if (AppManagers.isReady()) AppManagers.adaptive.onEvent("bookmark_changed")
                    }
                    quizViewModel.persistPosition()
                    popup.dismiss()
                    show()
                }
                b.setTextColor(ThemeManager.bookmarkText(this@QuizActivity, pair.first))
                row.addView(b, LinearLayout.LayoutParams(0, dp(82), 1f).apply {
                    setMargins(if (c == 0) 0 else dp(6), dp(4), if (c == 0) dp(6) else 0, dp(4))
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, dp(90)))
        }
        box.addView(grid)
        popup.contentView = box
        popup.setOnDismissListener { }
        popup.showAtLocation(scroll, Gravity.TOP or Gravity.END, dp(10), popupTopY(anchor))
        val anim = AnimationSet(true).apply {
            addAnimation(ScaleAnimation(.92f, 1f, .92f, 1f, 1f, .5f).apply { duration = 170 })
            addAnimation(AlphaAnimation(.25f, 1f).apply { duration = 170 })
        }
        if (AnimationPolicy.enabled(this)) box.startAnimation(anim)
    }

    private fun popupTopY(anchor: View): Int {
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        return (loc[1] + dp(8)).coerceAtLeast(dp(44))
    }

    private fun bookmarkGradient() = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(19, 111, 151), Color.rgb(27, 157, 117))).apply { cornerRadius = dp(24).toFloat() }

    private fun bookmarkColor(v: String) = ThemeManager.bookmarkFill(this@QuizActivity, v)

    private fun bookmarkPopupDrawable(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(75,88,105) else Color.rgb(205,215,225))
    }

    private fun readableForeground(background:Int):Int{
        val r=Color.red(background)/255.0;val g=Color.green(background)/255.0;val b=Color.blue(background)/255.0
        fun linear(v:Double)=if(v<=0.03928)v/12.92 else Math.pow((v+0.055)/1.055,2.4)
        val luminance=.2126*linear(r)+.7152*linear(g)+.0722*linear(b)
        return if(luminance<0.42) Color.WHITE else Color.rgb(24,29,36)
    }

    private fun showGeminiQuestionContext(q: Question) {
        val dialog = Dialog(this)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            background = rounded(ThemeManager.dialogBg(this@QuizActivity), 24f)
        }
        val title = TextView(this).apply { text = "BEN + GEMINI • EXAM INTELLIGENCE"; textSize = 17f * fontScale; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(this@QuizActivity)) }
        outer.addView(title, LinearLayout.LayoutParams(-1, -2))
        val subtitle = TextView(this).apply { text = "Question context • PYQ/exam history • visual study aid"; textSize = 11.5f * fontScale; setTextColor(ThemeManager.muted(this@QuizActivity)); setPadding(0, dp(3), 0, dp(10)) }
        outer.addView(subtitle)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun chip(label: String, action: () -> Unit): TextView = TextView(this).apply {
            text = label; textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(ThemeManager.text(this@QuizActivity)); background = rounded(ThemeManager.elevated(this@QuizActivity), 14f); setPadding(dp(10), 0, dp(10), 0); setOnClickListener { action() }
        }
        val contextChip = chip("EXAM CONTEXT") { loadGeminiContext(q, false, dialog) }
        val pyqChip = chip("PYQ / EXAMS") { loadGeminiContext(q, true, dialog) }
        val visualChip = chip("VISUALIZE") { generateGeminiVisual(q, dialog) }
        tabs.addView(contextChip, LinearLayout.LayoutParams(0, dp(40), 1f))
        tabs.addView(pyqChip, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(6),0,dp(6),0) })
        tabs.addView(visualChip, LinearLayout.LayoutParams(0, dp(40), 1f))
        outer.addView(tabs)
        val bodyScroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(10), dp(2), dp(6)) }
        val bodyText = TextView(this).apply { text = "Choose a view above. Ben will keep the QBank keyed answer authoritative and use Gemini as the grounded research collaborator."; textSize = 14.5f * fontScale; setTextColor(ThemeManager.text(this@QuizActivity)); setLineSpacing(0f,1.18f) }
        body.addView(bodyText)
        val image = ImageView(this).apply { visibility = View.GONE; adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Gemini generated medical study visual" }
        body.addView(image, LinearLayout.LayoutParams(-1, dp(280)).apply { setMargins(0, dp(10), 0, dp(8)) })
        val saveImage = TextView(this).apply {
            text = "SAVE VISUAL + MEMORY CUE → MY NOTES"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; visibility = View.GONE
            val fill = if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(255,219,118) else Color.rgb(255,240,188); setTextColor(readableForeground(fill)); background = rounded(fill, 13f)
            setOnClickListener { val b = image.drawable?.let { d -> (d as? android.graphics.drawable.BitmapDrawable)?.bitmap }; if (b != null) showGeneratedImageNoteEditor(q, b) }
        }
        body.addView(saveImage, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(0,0,0,dp(8)) })
        bodyScroll.addView(body)
        outer.addView(bodyScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val close = TextView(this).apply { text = "CLOSE"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(ThemeManager.text(this@QuizActivity)); background = rounded(ThemeManager.elevated(this@QuizActivity), 13f); setOnClickListener { dialog.dismiss() } }
        outer.addView(close, LinearLayout.LayoutParams(-1, dp(44)))
        dialog.setContentView(outer)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .94f).toInt(), (resources.displayMetrics.heightPixels * .82f).toInt())
        AdaptiveTypographyManager.apply(outer)

        fun renderResult(result: BenGeminiCoordinator.Result) {
            bodyText.text = buildString {
                append(result.answer)
                if (result.grounded && result.sources.isNotEmpty()) {
                    append("\n\nSOURCES")
                    result.sources.forEach { source -> append("\n[${source.citationIndex}] ${source.title}\n${source.uri}") }
                }
            }
            bodyScroll.post { bodyScroll.scrollTo(0,0) }
        }
        contextChip.setOnClickListener { loadGeminiContext(q, false, dialog, bodyText, bodyScroll) }
        pyqChip.setOnClickListener { loadGeminiContext(q, true, dialog, bodyText, bodyScroll) }
        visualChip.setOnClickListener { generateGeminiVisual(q, dialog, image, saveImage, bodyText) }
        dialog.setOnDismissListener { }
    }

    private fun loadGeminiContext(q: Question, pyq: Boolean, dialog: Dialog, body: TextView? = null, scroll: ScrollView? = null) {
        val target = body ?: return
        target.text = if (pyq) "Building local PYQ/exam-source map, then asking Gemini to contextualize it…" else "Preparing grounded exam context for this exact question…"
        lifecycleScope.launch {
            val account = GoogleAccountManager(this@QuizActivity)
            if (account.currentEmail() == null) {
                val login = account.signIn()
                if (login.isFailure) { target.text = account.friendlyError(login.exceptionOrNull() ?: IllegalStateException()); return@launch }
            }
            val result = runCatching {
                if (pyq) BenGeminiCoordinator(this@QuizActivity).previousYearContext(q)
                else BenGeminiCoordinator(this@QuizActivity).explainQuestion(q)
            }.getOrElse { BenGeminiCoordinator.Result("Gemini request failed: ${it.message ?: "unknown error"}", emptyList(), false, account.currentEmail()) }
            if (isFinishing || isDestroyed || !dialog.isShowing) return@launch
            target.text = buildString {
                append(result.answer)
                if (result.grounded && result.sources.isNotEmpty()) {
                    append("\n\nSOURCES")
                    result.sources.forEach { source -> append("\n[${source.citationIndex}] ${source.title}\n${source.uri}") }
                }
            }
            scroll?.post { scroll.scrollTo(0,0) }
        }
    }

    private fun generateGeminiVisual(q: Question, dialog: Dialog, image: ImageView? = null, save: TextView? = null, body: TextView? = null) {
        val target = body ?: return
        target.text = "Gemini is drawing a focused exam visual…"
        lifecycleScope.launch {
            val result = runCatching {
                BenGeminiImageGenerator(this@QuizActivity).generate("Create a medically accurate, exam-oriented educational visual for this question. Prefer a clean labeled diagram, algorithm, mechanism map, or comparison table as appropriate. No decorative text. Question: ${q.text.take(3500)} Keyed answer: ${q.correctAnswer.orEmpty().take(500)}")
            }.getOrElse { Result.failure<android.graphics.Bitmap>(it) }
            if (isFinishing || isDestroyed || !dialog.isShowing) return@launch
            result.onSuccess { bitmap ->
                image?.setImageBitmap(bitmap); image?.visibility = View.VISIBLE; save?.visibility = View.VISIBLE
                target.text = "Visual generated by Gemini. Use the button below to attach it to this question's My Notes with a memory cue."
            }.onFailure { target.text = "Gemini visual unavailable: ${it.message ?: "unknown error"}\n\nText + PYQ research remains available above." }
        }
    }

    private fun showGeneratedImageNoteEditor(q: Question, bitmap: android.graphics.Bitmap) {
        val input = EditText(this).apply { hint = "Memory cue, exam trap, or why this visual matters"; minLines = 3; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        AlertDialog.Builder(this).setTitle("Save Gemini visual to My Notes").setView(input).setNegativeButton("CANCEL", null).setPositiveButton("SAVE", null).create().also { dialog ->
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val note = input.text.toString().trim().ifBlank { "Gemini visual • exam context for this question" }
                    val stream = java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    val raw = "data:image/png;base64," + android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    quizViewModel.saveKnowledgeImageWithNote(raw, q.id, note) { ok -> Toast.makeText(this, if(ok) "Gemini visual saved to My Notes" else "Could not save Gemini visual", Toast.LENGTH_SHORT).show(); if(ok) dialog.dismiss() }
                }
            }
            dialog.show()
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toSpanned(html: String): Spanned {
        if (html.length > 180_000) return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        synchronized(spannedCache) { spannedCache[html]?.let { return it } }
        val parsed = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        synchronized(spannedCache) { spannedCache[html] = parsed }
        return parsed
    }

}

/** Prevents accidental vertical scrolling while the user is still answering. */
private class LockedScrollView(context: android.content.Context) : ScrollView(context) {
    var locked: Boolean = false
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = if (locked) false else super.onInterceptTouchEvent(ev)
    override fun onTouchEvent(ev: MotionEvent): Boolean = if (locked) false else super.onTouchEvent(ev)
}
