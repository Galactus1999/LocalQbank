#!/usr/bin/env python3
from pathlib import Path
import re,sys

OLD='versionName = "8.3.628"'
OLD_CODE='versionCode = 714'
NEW='versionName = "8.3.629"'
NEW_CODE='versionCode = 715'

def one(root,name):
    hits=list(root.rglob(name))
    if not hits: raise SystemExit("v8.3.629 missing "+name)
    return hits[0]

def replace_once(s,old,new,label):
    if old not in s: raise SystemExit("v8.3.629 anchor missing: "+label)
    return s.replace(old,new,1)

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_629_qbank_quiz_regressions.py <project>")
    root=Path(sys.argv[1])
    g=(root/"app"/"build.gradle.kts").read_text()
    if OLD not in g or OLD_CODE not in g: raise SystemExit("v8.3.629 wrong baseline")

    # 1) Separate ordinal quiz navigation from the persisted/raw q.position field.
    p=one(root,"QBankQuestionRepository.kt"); s=p.read_text()
    anchor='''    fun rawQuestionPosition(testId: String, questionId: Long): Int? = db.rawQuery(
        "SELECT q.position FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.id=? AND s.deleting=0 LIMIT 1",
        arrayOf(testId, questionId.toString())
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }

    fun questionAt(testId: String, position: Int): Question? = db.rawQuery('''
    repl='''    fun rawQuestionPosition(testId: String, questionId: Long): Int? = db.rawQuery(
        "SELECT COUNT(*) FROM question q2 JOIN source s2 ON s2.id=(SELECT t2.source_id FROM test t2 WHERE t2.id=q2.test_id LIMIT 1) AND s2.deleting=0 WHERE q2.test_id=? AND q2.position < (SELECT q.position FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.id=? AND s.deleting=0 LIMIT 1)",
        arrayOf(testId, testId, questionId.toString())
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }

    /** Raw persisted q.position lookup used by stable-key recovery. */
    private fun questionAtStoredPosition(testId: String, position: Int): Question? = questionAtQuery(
        testId, "q.position=?", arrayOf(testId, position.toString())
    )

    /** Quiz-facing ordinal lookup. position is 0-based display order, not q.position. */
    fun questionAtOrdinal(testId: String, ordinal: Int): Question? {
        if (ordinal < 0) return null
        return questionAtQuery(
            testId, "q.test_id=? AND s.deleting=0", arrayOf(testId),
            "ORDER BY q.position LIMIT 1 OFFSET $ordinal"
        )
    }

    fun questionAt(testId: String, position: Int): Question? = questionAtStoredPosition(testId, position)

    private fun questionAtQuery(testId: String, where: String, args: Array<String>, order: String = "ORDER BY q.position LIMIT 1"): Question? = db.rawQuery(
        "SELECT q.id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE $where LIMIT 1",
        args
    ).use { c ->
        if (!c.moveToFirst()) return@use null
        val id = c.getLong(0)
        val opts = mutableListOf<Option>()
        db.rawQuery("SELECT label,text,is_correct FROM option_item WHERE question_id=? ORDER BY position", arrayOf(id.toString())).use { o ->
            while (o.moveToNext()) opts.add(Option(o.getString(0) ?: "", o.getString(1) ?: "", o.getInt(2) != 0))
        }
        Question(id, stableKey(testId,c.getInt(1),c.getString(2)), c.getInt(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getString(9), opts, images(id,"question"), images(id,"explanation"))
    }

    private fun questionAtStoredPositionLegacy(testId: String, position: Int): Question? = db.rawQuery('''
    if anchor not in s: raise SystemExit("v8.3.629 QBankQuestionRepository anchor missing")
    # Replace the whole existing questionAt implementation through questionById.
    a=s.index(anchor)
    b=s.index('    fun questionById(questionId: Long): Question?',a)
    newblock='''    fun rawQuestionPosition(testId: String, questionId: Long): Int? = db.rawQuery(
        "SELECT COUNT(*) FROM question q2 JOIN test t2 ON t2.id=q2.test_id JOIN source s2 ON s2.id=t2.source_id AND s2.deleting=0 WHERE q2.test_id=? AND q2.position < (SELECT q.position FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.id=? AND s.deleting=0 LIMIT 1)",
        arrayOf(testId, testId, questionId.toString())
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }

    private fun questionAtQuery(testId: String, where: String, args: Array<String>, order: String = "ORDER BY q.position LIMIT 1"): Question? = db.rawQuery(
        "SELECT q.id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE $where AND s.deleting=0 $order",
        args
    ).use { c ->
        if (!c.moveToFirst()) return@use null
        val id = c.getLong(0)
        val opts = mutableListOf<Option>()
        db.rawQuery("SELECT label,text,is_correct FROM option_item WHERE question_id=? ORDER BY position", arrayOf(id.toString())).use { o ->
            while (o.moveToNext()) opts.add(Option(o.getString(0) ?: "", o.getString(1) ?: "", o.getInt(2) != 0))
        }
        Question(id, stableKey(testId,c.getInt(1),c.getString(2)),c.getInt(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),opts,images(id,"question"),images(id,"explanation"))
    }

    private fun questionAtStoredPosition(testId: String, position: Int): Question? =
        questionAtQuery(testId, "q.test_id=? AND q.position=?", arrayOf(testId, position.toString()))

    /** 0-based ordinal question lookup. This is the contract used by quiz navigation. */
    fun questionAtOrdinal(testId: String, ordinal: Int): Question? {
        if (ordinal < 0) return null
        return questionAtQuery(testId, "q.test_id=?", arrayOf(testId), "ORDER BY q.position LIMIT 1 OFFSET $ordinal")
    }

    /** Compatibility/raw-position lookup retained for stable-key and legacy callers. */
    fun questionAt(testId: String, position: Int): Question? = questionAtStoredPosition(testId, position)

'''
    s=s[:a]+newblock+s[b:]
    s=s.replace('return questionAt(testId, position)?.takeIf { it.sourceId.orEmpty() == sourceQuestionId }','return questionAtStoredPosition(testId, position)?.takeIf { it.sourceId.orEmpty() == sourceQuestionId }',1)
    # questionById uses stored position and remains raw-safe.
    p.write_text(s)

    # 2) Expose ordinal lookup through the quiz repository/data source.
    p=one(root,"QuizDataSources.kt"); s=p.read_text()
    if "fun questionAtOrdinal(testId: String, ordinal: Int): Question?" not in s:
        marker="interface QuizQuestionDataSource : AutoCloseable {"
        i=s.index(marker)+len(marker)
        s=s[:i]+"\n    fun questionAtOrdinal(testId: String, ordinal: Int): Question?"+s[i:]
    if "override fun questionAtOrdinal(testId: String, ordinal: Int)" not in s:
        marker="class QBankQuizDataSource(context: android.content.Context) : QuizQuestionDataSource, QuizNoteDataSource {"
        i=s.index(marker)+len(marker)
        i=s.index("\n",i)+1
        s=s[:i]+"    override fun questionAtOrdinal(testId: String, ordinal: Int) = db.questionAtOrdinal(testId, ordinal)\n"+s[i:]
    p.write_text(s)

    p=one(root,"QuizSessionRepository.kt"); s=p.read_text()
    if "fun questionAtOrdinal(testId: String, ordinal: Int): Question?" not in s:
        marker="    fun questionAt(testId: String, position: Int): Question? = questions.questionAt(testId, position)"
        i=s.index(marker)+len(marker)
        s=s[:i]+"\n    fun questionAtOrdinal(testId: String, ordinal: Int): Question? = questions.questionAtOrdinal(testId, ordinal)"+s[i:]
    p.write_text(s)

    p=one(root,"QuizViewModel.kt"); s=p.read_text()
    if "fun questionAtOrdinal(testId: String, ordinal: Int): Question?" not in s:
        marker="    fun questionAt(testId: String, position: Int): Question? = data.questionAt(testId, position)"
        i=s.index(marker)+len(marker)
        s=s[:i]+"\n    fun questionAtOrdinal(testId: String, ordinal: Int): Question? = data.questionAtOrdinal(testId, ordinal)"+s[i:]
    p.write_text(s)

    for name in ["QuizQuestionLoader.kt","QuizQuestionPrefetcher.kt"]:
        p=one(root,name); s=p.read_text()
        s=s.replace("quizViewModel.questionAt(quizViewModel.state.testId, quizViewModel.state.position)","quizViewModel.questionAtOrdinal(quizViewModel.state.testId, quizViewModel.state.position)")
        s=s.replace("quizViewModel.questionAt(quizViewModel.state.testId, target)","quizViewModel.questionAtOrdinal(quizViewModel.state.testId, target)")
        p.write_text(s)

    # 3) Make Home resume use the same ordinal contract when it resolves a saved ordinal.
    p=one(root,"QBankDb.kt"); s=p.read_text()
    s=s.replace('    fun questionAt(testId:String, position:Int):Question? = questionRepository.questionAt(testId, position)','    fun questionAt(testId:String, position:Int):Question? = questionRepository.questionAt(testId, position)\n    fun questionAtOrdinal(testId:String, ordinal:Int):Question? = questionRepository.questionAtOrdinal(testId, ordinal)',1)
    p.write_text(s)
    p=one(root,"MainRepository.kt"); s=p.read_text()
    s=s.replace('db.questionAt(test.id, safePos)','db.questionAtOrdinal(test.id, safePos)')
    p.write_text(s)

    # 4) Theme changes: preserve the exact visible question, not the original launch position.
    p=one(root,"QuizActivity.kt"); s=p.read_text()
    s=s.replace('recreateQuestion = { recreate() },','recreateQuestion = ::recreateQuestionSafely,',1)
    anchor='''    private fun extraText(key: String): String? = when (val v = intent.extras?.get(key)) {'''
    method='''    private fun recreateQuestionSafely() {
        val q = quizViewModel.state.currentQuestion
        if (q != null) {
            intent.putExtra("questionId", q.id)
            intent.putExtra("position", quizViewModel.state.position)
            intent.putExtra("testId", quizViewModel.state.testId)
            intent.putExtra("resumeLaunch", true)
        }
        quizViewModel.persistPosition()
        recreate()
    }
'''
    if anchor not in s: raise SystemExit("theme anchor missing")
    s=s.replace(anchor,method+anchor,1)
    p.write_text(s)

    # 5) Bookmark changes: never rebuild the question after a bookmark toggle/removal.
    p=one(root,"QuizMenuController.kt"); s=p.read_text()
    s=s.replace('    private val showQuestion: () -> Unit,','    private val showQuestion: () -> Unit,\n    private val refreshBookmarkState: () -> Unit,',1)
    s=s.replace('''quizViewModel.persistPosition();popup.dismiss();showQuestion()};b.setTextColor''','''quizViewModel.persistPosition();popup.dismiss();refreshBookmarkState()};b.setTextColor''',1)
    s=s.replace('''quizViewModel.persistPosition(); popup.dismiss(); showQuestion()
            }, LinearLayout.LayoutParams(-1, dp(44))''','''quizViewModel.persistPosition(); popup.dismiss(); refreshBookmarkState()
            }, LinearLayout.LayoutParams(-1, dp(44))''',1)
    p.write_text(s)
    # Add callback from Activity.
    p=one(root,"QuizActivity.kt"); s=p.read_text()
    s=s.replace('            showQuestion = ::show,\n            applyHeaderHeight = ::applyHeaderHeight,','            showQuestion = ::show,\n            refreshBookmarkState = ::refreshBookmarkState,\n            applyHeaderHeight = ::applyHeaderHeight,',1)
    # Add method next to section-chip updater.
    anchor='    private fun updateSectionChip(q: Question, status: String?) {'
    method='''    private fun refreshBookmarkState() {
        val q = quizViewModel.state.currentQuestion ?: return
        if (::bookmarkButton.isInitialized) updateBookmarkButton(q, bookmarkButton)
        if (::sectionChip.isInitialized) updateSectionChip(q, quizViewModel.progressStatus(q.stableKey))
    }
'''
    s=s.replace(anchor,method+anchor,1)
    p.write_text(s)

    # 6) Ben + AI: make the full-screen dialog opaque in every theme and dim the underlying question.
    p=one(root,"BenQuestionAiContextDialog.kt"); s=p.read_text()
    s=s.replace('background = ThemeManager.backgroundDrawable(activity)','background = ThemeManager.dialogBg(activity)',1)
    s=s.replace('''window.setBackgroundDrawableResource(android.R.color.transparent)''','''window.setBackgroundDrawable(GradientDrawable().apply { setColor(ThemeManager.dialogBg(activity)) })''',1)
    s=s.replace('''window.setDimAmount(0f)''','''window.setDimAmount(if (ThemeManager.isDark(activity)) 0.58f else 0.32f)''',1)
    s=s.replace('''setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled=true''','''setBackgroundColor(ThemeManager.dialogBg(activity))
            isVerticalScrollBarEnabled=true''',1)
    p.write_text(s)

    # 7) Celebration: replace the unpleasant milestone asset with the already-tested correct-answer cue.
    p=one(root,"RovexSoundFeedback.kt"); s=p.read_text()
    s=s.replace('milestoneId = p.load(context.applicationContext, R.raw.rover_milestone, 1)','milestoneId = p.load(context.applicationContext, R.raw.rover_correct, 1)',1)
    p.write_text(s)

    # 8) Regression test: prove ordinal lookup survives a gap in persisted q.position.
    p=one(root,"QBankDbSearchRegressionTest.kt"); s=p.read_text()
    test='''

    @Test
    fun quizOrdinalLookupSurvivesNonContiguousStoredQuestionPositions() {
        db.execSQL("DELETE FROM question_image")
        db.execSQL("DELETE FROM option_item")
        db.execSQL("DELETE FROM question")
        db.execSQL("DELETE FROM test")
        db.execSQL("DELETE FROM source")
        db.execSQL("INSERT INTO source(id,file_name,display_name,provider,imported_at) VALUES(1,'ordinal.apkg','Ordinal','test',1)")
        db.execSQL("INSERT INTO test(id,source_id,title,path,num_questions) VALUES('ordinal-test',1,'Ordinal Test','ordinal',3)")
        db.execSQL("INSERT INTO question(id,test_id,position,source_question_id,text,raw_text) VALUES(11,'ordinal-test',0,'q1','first','first')")
        db.execSQL("INSERT INTO question(id,test_id,position,source_question_id,text,raw_text) VALUES(12,'ordinal-test',1,'q2','second','second')")
        db.execSQL("INSERT INTO question(id,test_id,position,source_question_id,text,raw_text) VALUES(13,'ordinal-test',4,'q5','fifth stored position','fifth stored position')")
        assertEquals(3, qbankDb.questionCount("ordinal-test"))
        assertEquals(13L, qbankDb.questionAtOrdinal("ordinal-test", 2)?.id)
        assertEquals("fifth stored position", qbankDb.questionAtOrdinal("ordinal-test", 2)?.text)
        assertEquals(2, qbankDb.rawQuestionPosition("ordinal-test", 13L))
    }
'''
    if 'quizOrdinalLookupSurvivesNonContiguousStoredQuestionPositions' not in s:
        idx=s.rfind('\n}')
        s=s[:idx]+test+s[idx:]
    p.write_text(s)

    # 9) Version.
    gp=root/"app"/"build.gradle.kts"; g=gp.read_text().replace(OLD,NEW,1).replace(OLD_CODE,NEW_CODE,1); gp.write_text(g)
    print("v8.3.629 quiz/QBank regression repair applied")
if __name__=="__main__": main()
