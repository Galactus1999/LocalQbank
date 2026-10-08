from pathlib import Path
import sys
root=Path(sys.argv[1])
project=Path(sys.argv[2])
p=root/".github/ci/rovex_629_runtime_repair2.py"
s=p.read_text().replace('s=replace_once(s,old3,new3,"questionById")','s=s')
p.write_text(s)
q=next(project.rglob("QBankQuestionRepository.kt"))
s=q.read_text()
a=s.find("    fun questionById(questionId: Long): Question?")
b=s.find("\n    fun sourceIdForTest",a)
if a<0 or b<0: raise SystemExit("questionById boundary missing")
block='''    fun questionById(questionId: Long): Question? = db.rawQuery("SELECT q.id,q.test_id,q.position,q.source_question_id,COALESCE(q.text,''),q.raw_text,q.correct_answer,q.explanation,q.bot,q.video,q.audio FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE q.id=? AND s.deleting=0 LIMIT 1",arrayOf(questionId.toString())).use { c ->
        if (!c.moveToFirst()) return@use null
        val id=c.getLong(0); val testId=c.getString(1); val rawPos=c.getInt(2); val sourceKey=c.getString(3)
        val opts=mutableListOf<Option>()
        db.rawQuery("SELECT label,text,is_correct FROM option_item WHERE question_id=? ORDER BY position",arrayOf(id.toString())).use { o -> while(o.moveToNext()) opts.add(Option(o.getString(0) ?: "",o.getString(1) ?: "",o.getInt(2)!=0)) }
        Question(id,stableKey(testId,rawPos,sourceKey),rawPos,sourceKey,c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10),opts,images(id,"question"),images(id,"explanation"))
    }'''
q.write_text(s[:a]+block+s[b:])
