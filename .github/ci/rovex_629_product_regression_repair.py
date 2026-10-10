#!/usr/bin/env python3
from pathlib import Path
import sys,wave,math,struct

EXPECTED_VERSION='versionName = "8.3.628"'
EXPECTED_CODE='versionCode = 714'
NEW_VERSION='versionName = "8.3.629"'
NEW_CODE='versionCode = 715'

def one(root,name):
    xs=list(root.rglob(name))
    if len(xs)!=1: raise SystemExit(f"v8.3.629 expected one {name}, found {len(xs)}")
    return xs[0]
def rep(s,a,b,label):
    if a not in s: raise SystemExit("v8.3.629 missing "+label)
    return s.replace(a,b,1)

def qbank(root):
    p=one(root,"QBankQuestionRepository.kt");s=p.read_text()
    a='''    fun questionAt(testId: String, position: Int): Question? = db.rawQuery(
'''
    method='''    /** Quiz UI uses a zero-based ordinal; DB q.position is not assumed contiguous. */
    fun questionAtOrdinal(testId:String,ordinal:Int):Question?=db.rawQuery(
        "SELECT q.id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.test_id=? AND s.deleting=0 ORDER BY q.position,q.rowid LIMIT 1 OFFSET ?",
        arrayOf(testId,ordinal.coerceAtLeast(0).toString())
    ).use { c ->
        if(!c.moveToFirst()) return@use null
        val id=c.getLong(0);val opts=mutableListOf<Option>()
        db.rawQuery("SELECT label,text,is_correct FROM option_item WHERE question_id=? ORDER BY position",arrayOf(id.toString())).use { o -> while(o.moveToNext()) opts.add(Option(o.getString(0) ?: "",o.getString(1) ?: "",o.getInt(2)!=0)) }
        Question(id,stableKey(testId,c.getInt(1),c.getString(2)),c.getInt(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),opts,images(id,"question"),images(id,"explanation"))
    }
    fun questionOrdinal(testId:String,questionId:Long):Int?=db.rawQuery(
        "SELECT COUNT(*) FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id AND s.deleting=0 WHERE q.test_id=? AND q.position < (SELECT position FROM question WHERE id=? AND test_id=? LIMIT 1)",
        arrayOf(testId,questionId.toString(),testId)
    ).use { c -> if(c.moveToFirst()) c.getInt(0) else null }

'''
    p.write_text(s.replace(a,method+a,1))
    p=one(root,"QuizSessionRepository.kt");s=p.read_text();p=one(root,"QuizSessionRepository.kt");s=p.read_text();s=rep(s,'''    fun questionAt(testId: String, position: Int): Question? = questions.questionAt(testId, position)
''','''    fun questionAt(testId: String, position: Int): Question? = questions.questionAt(testId, position)
    fun questionAtOrdinal(testId:String,ordinal:Int):Question?=questions.questionAtOrdinal(testId,ordinal)
    fun questionOrdinal(testId:String,questionId:Long):Int?=questions.questionOrdinal(testId,questionId)
''','repository ordinal API');p.write_text(s)
    p=one(root,"QuizViewModel.kt");s=p.read_text();s=rep(s,'''    fun questionAt(testId: String, position: Int): Question? = data.questionAt(testId, position)
''','''    fun questionAt(testId: String, position: Int): Question? = data.questionAt(testId, position)
    fun questionAtOrdinal(testId:String,ordinal:Int):Question?=data.questionAtOrdinal(testId,ordinal)
''','viewmodel ordinal API');p.write_text(s)
    p=one(root,"QuizNavigationUseCase.kt");s=p.read_text();s=rep(s,'repository.rawQuestionPosition(testId, requestedQuestionId)','repository.questionOrdinal(testId, requestedQuestionId)','exact question ordinal');p.write_text(s)
    p=one(root,"QuizQuestionLoader.kt");s=p.read_text();s=rep(s,'quizViewModel.questionAt(quizViewModel.state.testId, quizViewModel.state.position)','quizViewModel.questionAtOrdinal(quizViewModel.state.testId, quizViewModel.state.position)','loader ordinal');p.write_text(s)
    p=one(root,"QuizQuestionPrefetcher.kt");s=p.read_text();s=rep(s,'quizViewModel.questionAt(quizViewModel.state.testId, target)','quizViewModel.questionAtOrdinal(quizViewModel.state.testId, target)','prefetch ordinal');p.write_text(s)

def bookmark(root):
    p=one(root,"QuizViewModel.kt");s=p.read_text()
    s=rep(s,'''private val pendingBookmarks = java.util.concurrent.ConcurrentHashMap<String, String?>()
''','''private val pendingBookmarks = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val pendingBookmarkClears = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
''','nullable ConcurrentHashMap')
    s=rep(s,'fun bookmark(stableKey: String): String? = pendingBookmarks[stableKey] ?: foregroundProgress.record(stableKey)?.bookmark','''fun bookmark(stableKey:String):String?=when{
        pendingBookmarkClears.contains(stableKey)->null
        pendingBookmarks.containsKey(stableKey)->pendingBookmarks[stableKey]
        else->foregroundProgress.record(stableKey)?.bookmark
    }''','bookmark read')
    old='''fun setBookmark(stableKey: String, value: String?) {
        pendingBookmarks[stableKey] = value
        bookmarks.set(stableKey, value)
        PerformanceManager.submit {
            // Keep the optimistic overlay until the durable write is reflected in the snapshot.
            foregroundProgress = PerformanceManager.progress(appContext)
            pendingBookmarks.remove(stableKey, value)
        }
    }'''
    new='''fun setBookmark(stableKey:String,value:String?){
        if(value.isNullOrBlank()){pendingBookmarks.remove(stableKey);pendingBookmarkClears.add(stableKey)}
        else{pendingBookmarkClears.remove(stableKey);pendingBookmarks[stableKey]=value}
        bookmarks.set(stableKey,value)
        PerformanceManager.submit{
            foregroundProgress=PerformanceManager.progress(appContext)
            if(value.isNullOrBlank()) pendingBookmarkClears.remove(stableKey) else pendingBookmarks.remove(stableKey,value)
        }
    }'''
    s=rep(s,old,new,'bookmark mutation');p.write_text(s)

def theme(root):
    p=one(root,"QuizActivity.kt");s=p.read_text();s=rep(s,'recreateQuestion = { recreate() }','recreateQuestion = { recreateForThemeChange() }','theme callback')
    anchor='''    private fun showCelebrationPlanner() {
        RovexCelebrationPlannerDialog.show(this, if (::counter.isInitialized) counter else content)
    }'''
    method='''    private fun recreateForThemeChange(){
        quizViewModel.state.currentQuestion?.let{q->
            intent.putExtra("questionId",q.id)
            intent.putExtra("position",quizViewModel.state.position)
        }
        quizViewModel.persistPosition()
        quizViewModel.setResumeCursor(quizViewModel.state.position,quizViewModel.state.currentQuestion?.stableKey)
        recreate()
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out)
    }

'''
    s=rep(s,anchor,method+anchor,'theme state owner');p.write_text(s)

def ben(root):
    p=one(root,"BenQuestionAiContextDialog.kt");s=p.read_text()
    s=s.replace('setBackgroundColor(Color.TRANSPARENT)','setBackgroundColor(ThemeManager.dialogBg(activity))',1)
    s=s.replace('window.setBackgroundDrawableResource(android.R.color.transparent)','window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ThemeManager.dialogBg(activity)))',1)
    s=s.replace('window.setDimAmount(0f)','window.setDimAmount(if(ThemeManager.isDark(activity)) 0.42f else 0.24f)',1)
    # Opaque root surface rather than the normal translucent living background.
    s=s.replace('background = ThemeManager.backgroundDrawable(activity)','background = rounded(activity, ThemeManager.dialogBg(activity), 0f)',1)
    p.write_text(s)

def sound(root):
    p=one(root,"app/src/main/res/raw/rover_milestone.wav") if list(root.rglob("rover_milestone.wav")) else root/"app/src/main/res/raw/rover_milestone.wav"
    p.parent.mkdir(parents=True,exist_ok=True)
    sr=44100;dur=.82;n=int(sr*dur);notes=[(523.25,0,.24),(659.25,.16,.26),(783.99,.33,.42)]
    frames=[]
    for i in range(n):
        t=i/sr;v=0
        for f,st,L in notes:
            if st<=t<st+L:
                x=(t-st)/L;e=min(1,x/.018)*min(1,(1-x)/.12)
                v+=(.24*math.sin(2*math.pi*f*(t-st))+.055*math.sin(4*math.pi*f*(t-st)))*e
        frames.append(struct.pack("<h",int(max(-.85,min(.85,v))*32767)))
    with wave.open(str(p),"wb") as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(sr);w.writeframes(b"".join(frames))

def main():
    if len(sys.argv)!=2:raise SystemExit("usage: rovex_629_product_regression_repair.py <project>")
    root=Path(sys.argv[1]).resolve()
    g=root/"app/build.gradle.kts";gs=g.read_text()
    if EXPECTED_VERSION not in gs or EXPECTED_CODE not in gs:raise SystemExit("v8.3.629 requires v8.3.628/714")
    qbank(root);bookmark(root);theme(root);ben(root);sound(root)
    g.write_text(gs.replace(EXPECTED_VERSION,NEW_VERSION,1).replace(EXPECTED_CODE,NEW_CODE,1))
    print("v8.3.629 product regression repair: APPLIED")
if __name__=="__main__":main()
