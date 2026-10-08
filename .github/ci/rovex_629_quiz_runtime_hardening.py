#!/usr/bin/env python3
from pathlib import Path
import sys

def one(root,name):
    hits=list(root.rglob(name))
    if len(hits)!=1: raise SystemExit(f"v8.3.629 expected exactly one {name}, got {len(hits)}")
    return hits[0]

def replace_once(s,old,new,label):
    n=s.count(old)
    if n!=1: raise SystemExit(f"v8.3.629 {label}: expected 1 match, got {n}")
    return s.replace(old,new,1)

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_629_quiz_runtime_hardening.py <project>")
    root=Path(sys.argv[1])
    gradle_files=list(root.rglob("build.gradle.kts"))
    app_gradles=[p for p in gradle_files if "com.android.application" in p.read_text()]
    if len(app_gradles)!=1: raise SystemExit(f"v8.3.629 expected exactly one Android application Gradle file, got {len(app_gradles)}")
    g=app_gradles[0]
    gs=g.read_text()
    if 'versionName = "8.3.628"' not in gs or 'versionCode = 714' not in gs:
        raise SystemExit("v8.3.629 wrong baseline")
    
    q=one(root,"QuizActivity.kt")
    s=q.read_text()
    # Configuration/theme recreation: launch extras are initial navigation intent, not authoritative
    # after Android recreates the Activity. SavedState/ViewModel must win on recreation.
    s=replace_once(
        s,
        'quizViewModel.applyResolution(resolution, preferResolutionPosition = exactQuestionId > 0L || resumeLaunch)',
        'quizViewModel.applyResolution(resolution, preferResolutionPosition = (exactQuestionId > 0L || resumeLaunch) && savedInstanceState == null)',
        'theme recreation position precedence'
    )
    q.write_text(s)

    loader=one(root,"QuizQuestionLoader.kt")
    s=loader.read_text()
    old='''    fun load(key: String) {
        showLoading("Loading question ${quizViewModel.state.position + 1} / ${quizViewModel.state.questionCount}…")
        loadToken++
        val token = loadToken
        executor.execute {
            val started = android.os.SystemClock.elapsedRealtime()
            val loaded = resultOf {
                if (quizViewModel.state.collectionMode) {
                    quizViewModel.state.collectionQuestionIds
                        .getOrNull(quizViewModel.state.position)
                        ?.let { quizViewModel.questionById(it) }
                } else {
                    quizViewModel.questionAt(quizViewModel.state.testId, quizViewModel.state.position)
                }
            }.getOrNull()
            val latency = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
            PerformanceManager.reportLatency(latency)
            if (AppManagers.isReady()) AppManagers.adaptive.onEvent("question_opened", latency)
            postToMain {
                if (isHostFinishing() || token != loadToken) return@postToMain
                if (loaded != null) {
                    synchronized(cache) { cache[key] = loaded }
                    onLoaded()
                } else {
                    showUnavailable()
                }
            }
        }
    }'''
    new='''    fun load(key: String) {
        showLoading("Loading question ${quizViewModel.state.position + 1} / ${quizViewModel.state.questionCount}…")
        loadToken++
        val token = loadToken
        executor.execute {
            var loaded: Question? = null
            var lastError: Throwable? = null
            // A transient SQLite cursor/connection failure must never turn a valid question into
            // a blank screen. Retry the same immutable position before surfacing an error.
            repeat(3) { attempt ->
                if (loaded != null) return@repeat
                val started = android.os.SystemClock.elapsedRealtime()
                val result = runCatching {
                    if (quizViewModel.state.collectionMode) {
                        quizViewModel.state.collectionQuestionIds
                            .getOrNull(quizViewModel.state.position)
                            ?.let { quizViewModel.questionById(it) }
                    } else {
                        quizViewModel.questionAt(quizViewModel.state.testId, quizViewModel.state.position)
                    }
                }
                loaded = result.getOrNull()
                lastError = result.exceptionOrNull()
                val latency = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
                PerformanceManager.reportLatency(latency)
                if (AppManagers.isReady()) AppManagers.adaptive.onEvent("question_opened", latency)
                if (loaded == null && attempt < 2) {
                    try { Thread.sleep(if (attempt == 0) 60L else 180L) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@repeat
                    }
                }
            }
            postToMain {
                if (isHostFinishing() || token != loadToken) return@postToMain
                if (loaded != null) {
                    synchronized(cache) { cache[key] = loaded!! }
                    onLoaded()
                } else {
                    if (lastError != null) {
                        ProductionCrashReporter.recordNonFatal(lastError!!, "QUIZ_QUESTION_LOAD_RETRY_EXHAUSTED")
                    }
                    showUnavailable()
                }
            }
        }
    }'''
    s=replace_once(s,old,new,'question loader retry')
    loader.write_text(s)

    show=one(root,"QuizQuestionShowCoordinator.kt")
    s=show.read_text()
    old='''    fun show() {
        content.removeAllViews()
        if (quizViewModel.state.questionCount == 0) {
            showNoQuestions()
            return
        }

        val key = if (quizViewModel.state.collectionMode) {'''
    new='''    fun show() {
        if (quizViewModel.state.questionCount == 0) {
            content.removeAllViews()
            showNoQuestions()
            return
        }

        val key = if (quizViewModel.state.collectionMode) {'''
    s=replace_once(s,old,new,'deferred content clearing')
    old2='''        if (q == null) {
            questionLoader.load(key)
            return
        }

        val prepared = questionStateCoordinator.prepare(q)'''
    new2='''        if (q == null) {
            // Keep the last rendered question visible while the new position is fetched.
            // Clearing first was the direct cause of user-visible blank frames when a load
            // transiently returned null.
            questionLoader.load(key)
            return
        }

        content.removeAllViews()
        val prepared = questionStateCoordinator.prepare(q)'''
    s=replace_once(s,old2,new2,'deferred content clear after load')
    show.write_text(s)

    ai=one(root,"BenQuestionAiContextDialog.kt")
    s=ai.read_text()
    s=replace_once(s,'background = ThemeManager.backgroundDrawable(activity)','background = android.graphics.drawable.ColorDrawable(ThemeManager.dialogBg(activity))','opaque Ben AI dialog surface')
    s=s.replace('''            setBackgroundColor(Color.TRANSPARENT)''','''            setBackgroundColor(ThemeManager.dialogBg(activity))''',1)
    s=s.replace('''            window.setBackgroundDrawableResource(android.R.color.transparent)''','''            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ThemeManager.dialogBg(activity)))''',1)
    s=s.replace('''            window.setDimAmount(0f)''','''            window.setDimAmount(if (ThemeManager.isDark(activity)) 0.28f else 0.12f)''',1)
    ai.write_text(s)

    menu=one(root,"QuizMenuController.kt")
    s=menu.read_text()
    old='''                quizViewModel.state.currentQuestion?.let { q -> quizViewModel.setBookmark(q.stableKey, null); if (AppManagers.isReady()) AppManagers.adaptive.onEvent("bookmark_changed") }
                quizViewModel.persistPosition(); popup.dismiss(); showQuestion()'''
    new='''                quizViewModel.state.currentQuestion?.let { q ->
                    quizViewModel.setBookmark(q.stableKey, null)
                    if (AppManagers.isReady()) AppManagers.adaptive.onEvent("bookmark_changed")
                }
                quizViewModel.persistPosition()
                // Removing a bookmark is an in-place metadata mutation. Do not rebuild the
                // question or navigation stack; rebuilding here could race the popup dismissal
                // and make the quiz Activity disappear back to its caller.
                popup.dismiss()'''
    s=replace_once(s,old,new,'bookmark removal navigation')
    menu.write_text(s)

    sound=one(root,"RovexSoundFeedback.kt")
    s=sound.read_text()
    s=replace_once(s,'milestoneId = p.load(context.applicationContext, R.raw.rover_milestone, 1)','milestoneId = p.load(context.applicationContext, R.raw.rover_correct, 1)','cleaner celebration cue')
    s=s.replace('''Cue.CLICK -> 0.72f; Cue.DEEP_TOUCH -> 0.46f; Cue.THEME_SWITCH -> 0.38f; else -> 0.85f''','''Cue.CLICK -> 0.72f; Cue.DEEP_TOUCH -> 0.46f; Cue.THEME_SWITCH -> 0.38f; Cue.MILESTONE -> 0.72f; else -> 0.85f''')
    sound.write_text(s)

    gs=gs.replace('versionName = "8.3.628"','versionName = "8.3.629"',1).replace('versionCode = 714','versionCode = 715',1)
    g.write_text(gs)
    print("v8.3.629 runtime hardening applied")
if __name__=="__main__": main()
