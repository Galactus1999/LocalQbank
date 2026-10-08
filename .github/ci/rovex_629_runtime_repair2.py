#!/usr/bin/env python3
from pathlib import Path
import sys

OLD_NAME='versionName = "8.3.628"'
OLD_CODE='versionCode = 714'
NEW_NAME='versionName = "8.3.629"'
NEW_CODE='versionCode = 715'

def one(root,name):
    p=list(root.rglob(name))
    if len(p)!=1: raise SystemExit(f"v8.3.629 expected one {name}, found {len(p)}")
    return p[0]

def replace_once(s,old,new,label):
    if old not in s: raise SystemExit("v8.3.629 anchor missing: "+label)
    return s.replace(old,new,1)

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_629_runtime_repair.py <project>")
    root=Path(sys.argv[1])

    # 1) QBank navigation: state.position is an ordinal (0..count-1), never the imported
    # database q.position. This prevents blank screens when deleted/imported rows leave gaps.
    p=one(root,"QBankQuestionRepository.kt"); s=p.read_text()
    old='''    fun questionAt(testId: String, position: Int): Question? = db.rawQuery(
        "SELECT q.id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.position=? AND s.deleting=0 LIMIT 1",
        arrayOf(testId, position.toString())
    ).use { c ->'''
    new='''    /**
     * Quiz position is an ordinal within the active QBank, not the imported q.position.
     * Imported/deleted data can leave gaps in q.position; addressing by equality therefore
     * produced null questions mid-session even though COUNT(*) still reported them.
     */
    fun questionAt(testId: String, position: Int): Question? = db.rawQuery(
        "SELECT q.id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND s.deleting=0 ORDER BY q.position,q.id LIMIT 1 OFFSET ?",
        arrayOf(testId, position.coerceAtLeast(0).toString())
    ).use { c ->'''
    s=replace_once(s,old,new,"questionAt")
    old2='''    fun rawQuestionPosition(testId: String, questionId: Long): Int? = db.rawQuery(
        "SELECT q.position FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.id=? AND s.deleting=0 LIMIT 1",
        arrayOf(testId, questionId.toString())
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }'''
    new2='''    /** Return the quiz ordinal for an exact question id, not its imported raw position. */
    fun rawQuestionPosition(testId: String, questionId: Long): Int? = db.rawQuery(
        "SELECT COUNT(*) FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.id=? AND s.deleting=0",
        arrayOf(testId, questionId.toString())
    ).use { exact ->
        if (!exact.moveToFirst() || exact.getInt(0) == 0) return@use null
        db.rawQuery(
            "SELECT COUNT(*) FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.position < (SELECT position FROM question WHERE id=? AND test_id=?) AND s.deleting=0",
            arrayOf(testId, questionId.toString(), testId)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
    }'''
    s=replace_once(s,old2,new2,"rawQuestionPosition")
    # questionById must resolve by exact DB id because questionAt is now ordinal.
    old3='''if (!c.moveToFirst()) null else questionAt(c.getString(0), c.getInt(1))'''
    new3='''if (!c.moveToFirst()) null else questionAt(c.getString(0), rawQuestionPosition(c.getString(0), questionId) ?: return@use null)'''
    s=replace_once(s,old3,new3,"questionById ordinal")    p.write_text(s)

    # 2) Removing a bookmark is an in-place UI mutation. Do not force a full question rebuild
    # while an async progress write is pending; that race was able to tear down the Activity.
    p=one(root,"QuizMenuController.kt"); s=p.read_text()
    old4='''val b=actionButton(pair.second,ThemeManager.bookmarkFill(context,pair.first),14.5f){quizViewModel.state.currentQuestion?.let{q->val next=if(quizViewModel.bookmark(q.stableKey)==pair.first)null else pair.first;quizViewModel.setBookmark(q.stableKey,next);if(AppManagers.isReady())AppManagers.adaptive.onEvent("bookmark_changed")};quizViewModel.persistPosition();popup.dismiss();showQuestion()};'''
    new4='''val b=actionButton(pair.second,ThemeManager.bookmarkFill(context,pair.first),14.5f){quizViewModel.state.currentQuestion?.let{q->val next=if(quizViewModel.bookmark(q.stableKey)==pair.first)null else pair.first;quizViewModel.setBookmark(q.stableKey,next);if(AppManagers.isReady())AppManagers.adaptive.onEvent("bookmark_changed")};popup.dismiss();showQuestion()};'''
    s=replace_once(s,old4,new4,"bookmark category")
    old5='''quizViewModel.persistPosition(); popup.dismiss(); showQuestion()
            }, LinearLayout.LayoutParams(-1, dp(44)).apply'''
    new5='''popup.dismiss()
                quizViewModel.state.currentQuestion?.let { q ->
                    val button = anchor as? TextView
                    button?.text = "🔖  Bookmark"
                    button?.setTextColor(ThemeManager.text(context))
                    button?.background = rounded(ThemeManager.elevated(context), 16f)
                }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply'''
    s=replace_once(s,old5,new5,"bookmark removal")
    p.write_text(s)

    # 3) Theme changes must restore the exact quiz state explicitly. Android recreates Activities
    # for configuration changes; do not rely solely on incidental View state.
    p=one(root,"QuizActivity.kt"); s=p.read_text()
    anchor='''override fun onResume() {
        super.onResume()'''
    insert='''    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("rovex.quiz.testId", quizViewModel.state.testId)
        outState.putInt("rovex.quiz.position", quizViewModel.state.position)
        outState.putString("rovex.quiz.stableKey", quizViewModel.state.currentQuestion?.stableKey)
        outState.putInt("rovex.quiz.questionCount", quizViewModel.state.questionCount)
        super.onSaveInstanceState(outState)
    }

'''
    s=replace_once(s,anchor,insert+anchor,"QuizActivity saved state")
    # Prefer the explicit saved position when the same quiz is recreated.
    old6='''val requestedPosition = extraInt("position", -1)'''
    new6='''val requestedPosition = extraInt("position", -1).let { requested ->
                    if (requested >= 0) requested else b?.getInt("rovex.quiz.position", -1) ?: -1
                }'''
    s=replace_once(s,old6,new6,"QuizActivity requestedPosition")
    old7='''val exactQuestionId = extraLong("questionId")'''
    new7='''val exactQuestionId = extraLong("questionId").let { if (it > 0L) it else -1L }'''
    s=replace_once(s,old7,new7,"QuizActivity exact id")
    p.write_text(s)

    # 4) BEN + AI must be an opaque foreground surface in dark themes. The WebView may be
    # transparent internally, but the Dialog window/root must never expose the question behind it.
    p=one(root,"BenQuestionAiContextDialog.kt"); s=p.read_text()
    s=replace_once(s,
        'background = ThemeManager.backgroundDrawable(activity)',
        'background = GradientDrawable().apply { setColor(ThemeManager.dialogBg(activity)); cornerRadius = 0f }',
        "Ben root opaque background")
    s=replace_once(s,
        'window.setBackgroundDrawableResource(android.R.color.transparent)',
        'window.setBackgroundDrawable(GradientDrawable().apply { setColor(ThemeManager.dialogBg(activity)); cornerRadius = 0f })',
        "Ben window opaque background")
    s=s.replace('window.setDimAmount(0f)','window.setDimAmount(if (ThemeManager.isDark(activity)) 0.24f else 0.10f)',1)
    p.write_text(s)

    # 5) Replace the harsh/weird milestone sample with the established short correct-answer cue.
    p=one(root,"RovexSoundFeedback.kt"); s=p.read_text()
    s=replace_once(s,'milestoneId = p.load(context.applicationContext, R.raw.rover_milestone, 1)',
                   'milestoneId = p.load(context.applicationContext, R.raw.rover_correct, 1)',
                   "milestone sound")
    p.write_text(s)

    # version bump
    g=[p for p in root.rglob("build.gradle.kts") if p.parent.name=="app"][0]; gs=g.read_text()
    if OLD_NAME not in gs or OLD_CODE not in gs: raise SystemExit("v8.3.629 baseline version mismatch")
    gs=gs.replace(OLD_NAME,NEW_NAME,1).replace(OLD_CODE,NEW_CODE,1); g.write_text(gs)
    print("V8.3.629_RUNTIME_REPAIR_APPLIED")
if __name__=="__main__": main()
