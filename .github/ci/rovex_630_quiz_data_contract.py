#!/usr/bin/env python3
from pathlib import Path
import sys

def one(root,name):
    xs=list(root.rglob(name))
    if len(xs)!=1: raise SystemExit(f"v8.3.630 expected one {name}, found {len(xs)}")
    return xs[0]

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_630_quiz_data_contract.py <project>")
    root=Path(sys.argv[1]).resolve()
    p=root/"app/src/main/java/com/localqbank/library/data/quiz/QuizDataSources.kt";\n    if not p.is_file(): raise SystemExit("v8.3.630 data quiz source missing")\n    s=p.read_text()
    old='''    fun questionAt(testId: String, position: Int): Question?
    fun testIdForQuestion(id: Long): String?
'''
    new='''    fun questionAt(testId: String, position: Int): Question?
    fun questionAtOrdinal(testId: String, ordinal: Int): Question?
    fun questionOrdinal(testId: String, questionId: Long): Int?
    fun testIdForQuestion(id: Long): String?
'''
    if old not in s: raise SystemExit("v8.3.630 QuizQuestionDataSource anchor missing")
    s=s.replace(old,new,1)
    old='''    override fun questionAt(testId: String, position: Int) = db.questionAt(testId, position)
    override fun testIdForQuestion(id: Long) = db.testIdForQuestion(id)
'''
    new='''    override fun questionAt(testId: String, position: Int) = db.questionAt(testId, position)
    override fun questionAtOrdinal(testId: String, ordinal: Int) = db.questionAtOrdinal(testId, ordinal)
    override fun questionOrdinal(testId: String, questionId: Long) = db.questionOrdinal(testId, questionId)
    override fun testIdForQuestion(id: Long) = db.testIdForQuestion(id)
'''
    if old not in s: raise SystemExit("v8.3.630 QBankQuizDataSource anchor missing")
    p.write_text(s.replace(old,new,1))
    p=one(root,"QBankDb.kt");s=p.read_text()
    old='''    fun questionAt(testId:String, position:Int):Question? = questionRepository.questionAt(testId, position)
    fun questionById(questionId:Long):Question? = questionRepository.questionById(questionId)
'''
    new='''    fun questionAt(testId:String, position:Int):Question? = questionRepository.questionAt(testId, position)
    fun questionAtOrdinal(testId:String, ordinal:Int):Question? = questionRepository.questionAtOrdinal(testId, ordinal)
    fun questionOrdinal(testId:String, questionId:Long):Int? = questionRepository.questionOrdinal(testId, questionId)
    fun questionById(questionId:Long):Question? = questionRepository.questionById(questionId)
'''
    if old not in s: raise SystemExit("v8.3.630 QBankDb anchor missing")
    p.write_text(s.replace(old,new,1))
    p=one(root,"app/build.gradle.kts") if False else root/"app/build.gradle.kts"
    s=p.read_text()
    if 'versionName = "8.3.629"' not in s or 'versionCode = 715' not in s: raise SystemExit("v8.3.630 requires v8.3.629/715")
    p.write_text(s.replace('versionName = "8.3.629"','versionName = "8.3.630"',1).replace('versionCode = 715','versionCode = 716',1))
    print("v8.3.630 quiz data contract repair: APPLIED")
if __name__=="__main__": main()
