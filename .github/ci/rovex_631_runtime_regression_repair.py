#!/usr/bin/env python3
from pathlib import Path
import sys

BASE_NAME='versionName = "8.3.630"'
BASE_CODE='versionCode = 716'
NEW_NAME='versionName = "8.3.631"'
NEW_CODE='versionCode = 717'

def one(root,name):
    xs=list(root.rglob(name))
    if len(xs)!=1: raise SystemExit(f"v8.3.631 expected one {name}, found {len(xs)}")
    return xs[0]

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_631_runtime_regression_repair.py <project>")
    root=Path(sys.argv[1]).resolve()
    g=root/"app/build.gradle.kts"
    gs=g.read_text()
    if BASE_NAME not in gs or BASE_CODE not in gs:
        raise SystemExit("v8.3.631 requires v8.3.630/716")

    # v8.3.629 runtime_repair2 already owns bookmark, theme, Ben and sound repairs.
    # v8.3.631 only hardens the QBank blank-frame path, avoiding duplicate brittle anchors.
    loader=one(root,"QuizQuestionLoader.kt")
    s=loader.read_text()
    old_start=s.find("    fun load(key: String) {")
    if old_start<0: raise SystemExit("v8.3.631 question loader anchor missing")
    old_end=s.find("\n    }",old_start)
    if old_end<0: raise SystemExit("v8.3.631 question loader boundary missing")
    old_end += len("\n    }")
    block=s[old_start:old_end]
    if "var loaded: Question? = null" not in block:
        new='''    fun load(key: String) {
        showLoading("Loading question " + (quizViewModel.state.position + 1) + " / " + quizViewModel.state.questionCount + "…")
        loadToken++
        val token = loadToken
        executor.execute {
            var loaded: Question? = null
            var lastError: Throwable? = null
            repeat(3) { attempt ->
                if (loaded != null) return@repeat
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
                if (loaded == null && attempt < 2) {
                    try { Thread.sleep(if (attempt == 0) 60L else 180L) }
                    catch (_: InterruptedException) {
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
        s=s[:old_start]+new+s[old_end:]
        loader.write_text(s)

    show=one(root,"QuizQuestionShowCoordinator.kt")
    s=show.read_text()
    if "content.removeAllViews()" in s:
        old='''    fun show() {
        content.removeAllViews()
        if (quizViewModel.state.questionCount == 0) {'''
        if old in s:
            s=s.replace(old,'''    fun show() {
        if (quizViewModel.state.questionCount == 0) {
            content.removeAllViews()
            if (quizViewModel.state.questionCount == 0) {''',1)
            s=s.replace('''        if (quizViewModel.state.questionCount == 0) {
            content.removeAllViews()
            if (quizViewModel.state.questionCount == 0) {
            showNoQuestions()
            return
        }''','''        if (quizViewModel.state.questionCount == 0) {
            content.removeAllViews()
            showNoQuestions()
            return
        }''',1)
    old='''        if (q == null) {
            questionLoader.load(key)
            return
        }

        val prepared = questionStateCoordinator.prepare(q)'''
    if old in s:
        s=s.replace(old,'''        if (q == null) {
            questionLoader.load(key)
            return
        }

        content.removeAllViews()
        val prepared = questionStateCoordinator.prepare(q)''',1)
    show.write_text(s)

    g.write_text(gs.replace(BASE_NAME,NEW_NAME,1).replace(BASE_CODE,NEW_CODE,1))
    print("v8.3.631 runtime regression repair: APPLIED")
if __name__=="__main__": main()
