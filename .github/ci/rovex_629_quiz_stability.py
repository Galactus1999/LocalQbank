#!/usr/bin/env python3
from pathlib import Path
import sys, wave, math, struct

def one(root,name):
    hits=list(root.rglob(name))
    if not hits: raise SystemExit("v8.3.629 missing "+name)
    return hits[0]

def main():
    root=Path(sys.argv[1])

    p=one(root,"QBankQuestionRepository.kt")
    s=p.read_text()
    start=s.index("    fun questionAt(testId: String, position: Int): Question?")
    end=s.index("\n    fun questionById(questionId: Long)",start)
    fn=r'''    fun questionAt(testId: String, position: Int): Question? {
        if (position < 0) return null
        val sql = "SELECT q.id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio " +
            "FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND q.position=? AND s.deleting=0 LIMIT 1"
        val row = db.rawQuery(sql,arrayOf(testId,position.toString())).use { c ->
            if(c.moveToFirst()) arrayOf(c.getLong(0),c.getInt(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9)) else null
        } ?: db.rawQuery(
            "SELECT q.id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio " +
                "FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND s.deleting=0 ORDER BY q.position,q.rowid LIMIT 1 OFFSET ?",
            arrayOf(testId,position.toString())
        ).use { c ->
            if(c.moveToFirst()) arrayOf(c.getLong(0),c.getInt(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9)) else null
        } ?: return null
        val id=row[0] as Long
        val actualPosition=row[1] as Int
        val sourceQuestionId=row[2] as String?
        val opts=mutableListOf<Option>()
        db.rawQuery("SELECT label,text,is_correct FROM option_item WHERE question_id=? ORDER BY position",arrayOf(id.toString())).use { o ->
            while(o.moveToNext()) opts.add(Option(o.getString(0) ?: "",o.getString(1) ?: "",o.getInt(2)!=0))
        }
        return Question(id,stableKey(testId,actualPosition,sourceQuestionId),actualPosition,sourceQuestionId,row[3] as String?,row[4] as String?,row[5] as String?,row[6] as String?,row[7] as String?,row[8] as String?,row[9] as String?,opts,images(id,"question"),images(id,"explanation"))
    }
'''
    p.write_text(s[:start]+fn+s[end:])

    p=one(root,"QuizQuestionLoader.kt")
    s=p.read_text()
    a=s.index("    fun load(key: String)")
    b=s.index("\n        loadToken++",a)
    s=s[:a]+'''    fun load(key: String, requestedPosition: Int = quizViewModel.state.position) {
        val position = requestedPosition.coerceAtLeast(0)
        showLoading("Loading question " + (position + 1) + " / " + quizViewModel.state.questionCount + "…")'''+s[b:]
    s=s.replace(".getOrNull(quizViewModel.state.position)",".getOrNull(position)")
    s=s.replace("quizViewModel.questionAt(quizViewModel.state.testId, quizViewModel.state.position)","quizViewModel.questionAt(quizViewModel.state.testId, position)")
    p.write_text(s)

    p=one(root,"QuizQuestionShowCoordinator.kt")
    s=p.read_text().replace("questionLoader.load(key)","questionLoader.load(key, quizViewModel.state.position)",1)
    p.write_text(s)

    p=one(root,"QuizMenuController.kt")
    s=p.read_text()
    s=s.replace("quizViewModel.persistPosition();popup.dismiss();showQuestion()","quizViewModel.persistPosition();popup.dismiss();quizViewModel.state.currentQuestion?.let(updateBookmark)",2)
    p.write_text(s)

    p=one(root,"QuizActivity.kt")
    s=p.read_text()
    helper='''    fun recreateForThemeChange() {
        val q = quizViewModel.state.currentQuestion
        quizViewModel.setResumeCursor(quizViewModel.state.position, q?.stableKey)
        quizViewModel.persistPosition()
        if (q != null) {
            intent.putExtra("testId", quizViewModel.state.testId)
            intent.putExtra("questionId", q.id)
            intent.putExtra("position", quizViewModel.state.position)
            intent.putExtra("resumeLaunch", true)
        }
        recreate()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

'''
    anchor="    private fun build(): LinearLayout {"
    if "fun recreateForThemeChange()" not in s:
        s=s.replace(anchor,helper+anchor,1)
    s=s.replace("recreateQuestion = { recreate() }","recreateQuestion = { recreateForThemeChange() }",1)
    p.write_text(s)

    p=one(root,"BenQuestionAiContextDialog.kt")
    s=p.read_text()
    s=s.replace("window.setBackgroundDrawableResource(android.R.color.transparent)","window.setBackgroundDrawable(rounded(activity, ThemeManager.dialogBg(activity), 0f))",1)
    s=s.replace("window.setDimAmount(0f)","window.setDimAmount(if (ThemeManager.isDark(activity)) 0.58f else 0.42f)",1)
    s=s.replace("setBackgroundColor(Color.TRANSPARENT)","setBackgroundColor(ThemeManager.dialogBg(activity))",1)
    p.write_text(s)

    raw=one(root,"RovexSoundFeedback.kt")
    wav=raw.parent/"rovex_celebration_chime.wav"
    sr=44100; duration=0.42; frames=[]
    for i in range(int(sr*duration)):
        t=i/sr; env=min(1.0,t/0.012)*min(1.0,max(0.0,(duration-t)/0.11)); f=740.0 if t<0.19 else 988.0
        x=(0.52*math.sin(2*math.pi*f*t)+0.18*math.sin(2*math.pi*2*f*t))*env
        frames.append(struct.pack("<h",int(max(-1,min(1,x))*32767)))
    with wave.open(str(wav),"wb") as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(sr);w.writeframes(b"".join(frames))
    s=raw.read_text().replace("R.raw.rover_milestone","R.raw.rovex_celebration_chime",1)
    raw.write_text(s)
    print("v8.3.629 APPLIED")

if __name__=="__main__":
    if len(sys.argv)!=2: raise SystemExit("usage: overlay <project>")
    main()
