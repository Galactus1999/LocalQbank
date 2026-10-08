#!/usr/bin/env python3
from pathlib import Path
import sys, wave, math, struct, re
BASE_NAME='versionName = "8.3.630"'; BASE_CODE='versionCode = 716'
NEW_NAME='versionName = "8.3.631"'; NEW_CODE='versionCode = 717'
def one(root,name):
    xs=list(root.rglob(name))
    if len(xs)!=1: raise SystemExit(f"v8.3.631 expected one {name}, found {len(xs)}")
    return xs[0]
def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_631_runtime_regression_repair.py <project>")
    root=Path(sys.argv[1]).resolve(); g=root/"app/build.gradle.kts"; gs=g.read_text()
    if BASE_NAME not in gs or BASE_CODE not in gs: raise SystemExit("v8.3.631 requires v8.3.630/716")

    p=one(root,"QuizQuestionLoader.kt"); s=p.read_text()
    start=s.find("    fun load(key: String) {"); end=s.find("\n    }",start)
    if start<0 or end<0: raise SystemExit("question loader boundary missing")
    end+=len("\n    }")
    new='''    fun load(key: String) {
        showLoading("Loading question " + (quizViewModel.state.position + 1) + " / " + quizViewModel.state.questionCount + "…")
        loadToken++
        val token = loadToken
        executor.execute {
            var loaded: Question? = null
            var lastError: Throwable? = null
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
                    try { Thread.sleep(if (attempt == 0) 60L else 180L) }
                    catch (_: InterruptedException) { Thread.currentThread().interrupt(); return@repeat }
                }
            }
            postToMain {
                if (isHostFinishing() || token != loadToken) return@postToMain
                if (loaded != null) {
                    synchronized(cache) { cache[key] = loaded!! }
                    onLoaded()
                } else {
                    if (lastError != null) ProductionCrashReporter.recordNonFatal(lastError!!, "QUIZ_QUESTION_LOAD_RETRY_EXHAUSTED")
                    showUnavailable()
                }
            }
        }
    }'''
    s=s[:start]+new+s[end:]; p.write_text(s)

    p=one(root,"QuizQuestionShowCoordinator.kt"); s=p.read_text()
    s=s.replace("    fun show() {\n        content.removeAllViews()\n        if (quizViewModel.state.questionCount == 0) {",
                "    fun show() {\n        if (quizViewModel.state.questionCount == 0) {\n            content.removeAllViews()\n            if (quizViewModel.state.questionCount == 0) {",1)
    # Normalize the branch if the previous overlay already partially changed it.
    s=s.replace("""        if (quizViewModel.state.questionCount == 0) {
            content.removeAllViews()
            if (quizViewModel.state.questionCount == 0) {
            showNoQuestions()
            return
        }""","""        if (quizViewModel.state.questionCount == 0) {
            content.removeAllViews()
            showNoQuestions()
            return
        }""",1)
    old="""        if (q == null) {
            questionLoader.load(key)
            return
        }

        val prepared = questionStateCoordinator.prepare(q)"""
    new="""        if (q == null) {
            questionLoader.load(key)
            return
        }

        content.removeAllViews()
        val prepared = questionStateCoordinator.prepare(q)"""
    if old not in s: raise SystemExit("show coordinator load anchor missing")
    s=s.replace(old,new,1); p.write_text(s)

    p=one(root,"QuizMenuController.kt"); s=p.read_text()
    old='''                quizViewModel.state.currentQuestion?.let { q -> quizViewModel.setBookmark(q.stableKey, null); if (AppManagers.isReady()) AppManagers.adaptive.onEvent("bookmark_changed") }
                quizViewModel.persistPosition(); popup.dismiss(); showQuestion()'''
    new='''                quizViewModel.state.currentQuestion?.let { q ->
                    quizViewModel.setBookmark(q.stableKey, null)
                    if (AppManagers.isReady()) AppManagers.adaptive.onEvent("bookmark_changed")
                }
                quizViewModel.persistPosition()
                popup.dismiss()'''
    if old not in s: raise SystemExit("bookmark removal anchor missing")
    s=s.replace(old,new,1); p.write_text(s)

    p=one(root,"QuizActivity.kt"); s=p.read_text()
    if "recreateQuestion = { recreate() }" in s:
        s=s.replace("recreateQuestion = { recreate() }","recreateQuestion = { recreateForThemeChange() }",1)
    if "recreateForThemeChange()" not in s:
        raise SystemExit("theme callback anchor missing")
    if "private fun recreateForThemeChange()" not in s:
        anchor='''    private fun showCelebrationPlanner() {
        RovexCelebrationPlannerDialog.show(this, if (::counter.isInitialized) counter else content)
    }'''
        if anchor not in s: raise SystemExit("theme method insertion anchor missing")
        method='''    private fun recreateForThemeChange() {
        quizViewModel.state.currentQuestion?.let { q ->
            intent.putExtra("questionId", q.id)
            intent.putExtra("position", quizViewModel.state.position)
        }
        quizViewModel.persistPosition()
        quizViewModel.setResumeCursor(quizViewModel.state.position, quizViewModel.state.currentQuestion?.stableKey)
        recreate()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

'''
        s=s.replace(anchor,method+anchor,1)
    p.write_text(s)

    p=one(root,"BenQuestionAiContextDialog.kt"); s=p.read_text()
    for marker in ("val web = WebView(activity).apply {","val web = BenChatWebView(activity).apply {"):
        if marker in s and "setBackgroundColor(ThemeManager.dialogBg(activity))" not in s:
            s=s.replace(marker,marker+"\n            setBackgroundColor(ThemeManager.dialogBg(activity))",1)
    if "setBackgroundColor(ThemeManager.dialogBg(activity))" not in s: raise SystemExit("Ben WebView anchor missing")
    if "window.setBackgroundDrawableResource(android.R.color.transparent)" in s:
        s=s.replace("window.setBackgroundDrawableResource(android.R.color.transparent)","window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ThemeManager.dialogBg(activity)))",1)
    if "window.setDimAmount(if (ThemeManager.isDark(activity))" in s:
        s=re.sub(r"window\.setDimAmount\(if \(ThemeManager\.isDark\(activity\)\) [^\\n]+","window.setDimAmount(if (ThemeManager.isDark(activity)) 0.32f else 0.12f)",s,count=1)
    p.write_text(s)

    p=one(root,"RovexSoundFeedback.kt"); s=p.read_text()
    s=s.replace("milestoneId = p.load(context.applicationContext, R.raw.rover_milestone, 1)","milestoneId = p.load(context.applicationContext, R.raw.rover_correct, 1)",1); p.write_text(s)

    raw=root/"app/src/main/res/raw"; raw.mkdir(parents=True,exist_ok=True); wav=raw/"rover_correct.wav"
    if not wav.exists():
        sr=44100; dur=.62; notes=[(523.25,0,.20),(659.25,.13,.22),(783.99,.28,.30)]; frames=[]
        for i in range(int(sr*dur)):
            t=i/sr; v=0.0
            for f,st,L in notes:
                if st<=t<st+L:
                    x=(t-st)/L; env=min(1,x/.018)*min(1,(1-x)/.10); v+=.22*math.sin(2*math.pi*f*(t-st))*env
            frames.append(struct.pack("<h",int(max(-.8,min(.8,v))*32767)))
        with wave.open(str(wav),"wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(b"".join(frames))
    g.write_text(gs.replace(BASE_NAME,NEW_NAME,1).replace(BASE_CODE,NEW_CODE,1))
    print("v8.3.631 runtime regression repair: APPLIED")
if __name__=="__main__": main()
