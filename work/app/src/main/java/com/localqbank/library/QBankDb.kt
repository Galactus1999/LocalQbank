package com.localqbank.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest
import android.database.Cursor

class QBankDb(context: Context) {
    private val dbFile: File = context.getDatabasePath("qbank.db")
    private val db: SQLiteDatabase
    init {
        dbFile.parentFile?.mkdirs()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.setForeignKeyConstraintsEnabled(true)
        // Enable WAL through the Android API. Do not execute PRAGMA journal_mode=WAL
        // with execSQL because journal_mode returns a result row and can crash on startup.
        db.enableWriteAheadLogging()
        createSchema()
    }
    private fun createSchema() {
        db.execSQL("CREATE TABLE IF NOT EXISTS source(id INTEGER PRIMARY KEY AUTOINCREMENT, file_name TEXT UNIQUE NOT NULL, display_name TEXT, series_number TEXT, provider TEXT NOT NULL, imported_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS test(id TEXT PRIMARY KEY, source_id INTEGER NOT NULL, title TEXT NOT NULL, path TEXT, num_questions INTEGER, total_marks REAL, duration_seconds INTEGER, time_per_question REAL, original_id TEXT, series_number TEXT, FOREIGN KEY(source_id) REFERENCES source(id) ON DELETE CASCADE)")
        ensureColumn("source", "display_name", "TEXT")
        ensureColumn("source", "series_number", "TEXT")
        ensureColumn("test", "series_number", "TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS question(id INTEGER PRIMARY KEY AUTOINCREMENT, test_id TEXT NOT NULL, position INTEGER NOT NULL, source_question_id TEXT, text TEXT, raw_text TEXT, correct_answer TEXT, explanation TEXT, bot TEXT, video TEXT, audio TEXT, FOREIGN KEY(test_id) REFERENCES test(id) ON DELETE CASCADE, UNIQUE(test_id,position))")
        db.execSQL("CREATE TABLE IF NOT EXISTS option_item(id INTEGER PRIMARY KEY AUTOINCREMENT, question_id INTEGER NOT NULL, position INTEGER NOT NULL, label TEXT, text TEXT, is_correct INTEGER DEFAULT 0, FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS question_image(id INTEGER PRIMARY KEY AUTOINCREMENT, question_id INTEGER NOT NULL, kind TEXT NOT NULL, position INTEGER NOT NULL, uri TEXT NOT NULL, FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS progress(question_id INTEGER PRIMARY KEY, selected_answer TEXT, status TEXT, bookmark TEXT, review INTEGER DEFAULT 0, attempt_count INTEGER DEFAULT 0, last_attempted INTEGER DEFAULT 0, FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE CASCADE)")
        // v2 progress store: stable-key based durable study state. The legacy `progress`
        // table is retained for backward compatibility, but all new quiz progress is written
        // transactionally here. This avoids coupling progress identity to mutable numeric IDs.
        db.execSQL("CREATE TABLE IF NOT EXISTS progress_v2(stable_key TEXT PRIMARY KEY NOT NULL, selected_answer TEXT, status TEXT, bookmark TEXT, review INTEGER DEFAULT 0, attempts INTEGER DEFAULT 0, last_attempted INTEGER DEFAULT 0, time_ms INTEGER DEFAULT 0, mistake_type TEXT, ease_factor REAL DEFAULT 2.5, repetitions INTEGER DEFAULT 0, interval_days REAL DEFAULT 0, next_due INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS progress_meta(meta_key TEXT PRIMARY KEY NOT NULL, meta_value TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_progress_v2_due ON progress_v2(next_due,last_attempted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_test_source ON test(source_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_question_test ON question(test_id,position)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_question_source ON question(source_question_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS question_note(question_id INTEGER PRIMARY KEY, note TEXT NOT NULL, updated_at INTEGER NOT NULL, FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS question_note_image(id INTEGER PRIMARY KEY AUTOINCREMENT, question_id INTEGER NOT NULL, path TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(question_id) REFERENCES question(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_question_note_image_question ON question_note_image(question_id,created_at)")
        // FTS4 keeps global search fast even for very large QBanks. Content is rebuilt lazily
        // from the normalized SQLite tables, so the 160 MB source HTML is never searched.
        try {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS question_fts USING fts4(question_id UNINDEXED, test_id UNINDEXED, source_id UNINDEXED, content)")
        } catch (_: Exception) { /* Some vendor SQLite builds may omit FTS; LIKE fallback remains available. */ }
    }
    private fun ensureColumn(table: String, column: String, definition: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(1).equals(column, true)) { found = true; break }
            found
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }

    fun ensureSearchIndex(){ rebuildSearchIndexIfNeeded() }
    private fun rebuildSearchIndexIfNeeded(){
        try {
            var schemaOk=false
            db.rawQuery("PRAGMA table_info(question_fts)", null).use { c ->
                var cols=0; while(c.moveToNext()){ if(c.getString(1)=="source_id") schemaOk=true; cols++ }
                if(cols < 4) schemaOk=false
            }
            if(!schemaOk){ db.execSQL("DROP TABLE IF EXISTS question_fts"); db.execSQL("CREATE VIRTUAL TABLE question_fts USING fts4(question_id UNINDEXED, test_id UNINDEXED, source_id UNINDEXED, content)") }
            val indexed = db.rawQuery("SELECT COUNT(*) FROM question_fts", null).use { c -> c.moveToFirst(); c.getLong(0) }
            val questions = db.rawQuery("SELECT COUNT(*) FROM question", null).use { c -> c.moveToFirst(); c.getLong(0) }
            if (indexed == questions) return
            db.beginTransaction()
            try {
                db.delete("question_fts", null, null)
                db.rawQuery("SELECT q.id,q.test_id,s.id,COALESCE(q.text,'')||' '||COALESCE(q.raw_text,'')||' '||COALESCE(q.explanation,'')||' '||COALESCE(t.title,'')||' '||COALESCE(t.path,'')||' '||COALESCE(s.display_name,s.file_name,'')||' '||COALESCE((SELECT group_concat(o.text,' ') FROM option_item o WHERE o.question_id=q.id),'') FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id", null).use { c ->
                    val st=db.compileStatement("INSERT INTO question_fts(question_id,test_id,source_id,content) VALUES(?,?,?,?)")
                    while(c.moveToNext()){ st.bindLong(1,c.getLong(0)); st.bindString(2,c.getString(1)); st.bindLong(3,c.getLong(2)); st.bindString(4,c.getString(3)); st.executeInsert(); st.clearBindings() }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (_: Exception) { }
    }

    fun questionExists(questionId:Long):Boolean = db.rawQuery("SELECT 1 FROM question WHERE id=? LIMIT 1", arrayOf(questionId.toString())).use { it.moveToFirst() }
    /**
     * Lightweight keyset-paginated corpus access for offline AI indexing. This returns only
     * bounded text/context rows and never materializes the whole QBank in memory.
     */
    fun aiIndexQuestionCount(): Int = db.rawQuery("SELECT COUNT(*) FROM question", null).use { c -> c.moveToFirst(); c.getInt(0) }

    fun aiIndexBatch(afterQuestionId: Long = 0L, limit: Int = 64): List<AiIndexRow> {
        val safeLimit = limit.coerceIn(1, 256)
        val sql = "SELECT q.id, COALESCE(q.text,''), COALESCE(q.explanation,''), COALESCE(t.title,''), COALESCE(t.path,''), COALESCE(s.display_name,s.file_name,'') " +
            "FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id " +
            "WHERE q.id>? ORDER BY q.id LIMIT $safeLimit"
        return db.rawQuery(sql, arrayOf(afterQuestionId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(AiIndexRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)))
                }
            }
        }
    }

    /** Bulk existence check used by collection/backup flows. Avoids one SQLite query per ID. */
    fun existingQuestionIds(ids: LongArray): LongArray {
        if (ids.isEmpty()) return longArrayOf()
        val out = ArrayList<Long>(ids.size)
        ids.distinct().chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.rawQuery("SELECT id FROM question WHERE id IN ($placeholders)", chunk.map { it.toString() }.toTypedArray()).use { c ->
                while (c.moveToNext()) out.add(c.getLong(0))
            }
        }
        return out.toLongArray()
    }

    data class StoredProgress(
        val selected: String?, val status: String?, val bookmark: String?, val review: Boolean,
        val attempts: Int, val lastAttempted: Long, val timeMs: Long, val mistake: String?,
        val easeFactor: Float, val repetitions: Int, val intervalDays: Float, val nextDue: Long
    )

    fun readStoredProgress(stableKey: String): StoredProgress? = db.rawQuery(
        "SELECT selected_answer,status,bookmark,review,attempts,last_attempted,time_ms,mistake_type,ease_factor,repetitions,interval_days,next_due FROM progress_v2 WHERE stable_key=? LIMIT 1",
        arrayOf(stableKey)
    ).use { c ->
        if (!c.moveToFirst()) return@use null
        StoredProgress(c.getStringOrNull(0), c.getStringOrNull(1), c.getStringOrNull(2), c.getInt(3) != 0,
            c.getInt(4), c.getLong(5), c.getLong(6), c.getStringOrNull(7), c.getFloat(8), c.getInt(9), c.getFloat(10), c.getLong(11))
    }

    fun writeStoredProgress(stableKey: String, value: StoredProgress) {
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR REPLACE INTO progress_v2(stable_key,selected_answer,status,bookmark,review,attempts,last_attempted,time_ms,mistake_type,ease_factor,repetitions,interval_days,next_due) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf(stableKey, value.selected, value.status, value.bookmark, if (value.review) 1 else 0, value.attempts, value.lastAttempted, value.timeMs, value.mistake, value.easeFactor, value.repetitions, value.intervalDays, value.nextDue))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deleteStoredProgress(stableKey: String) { db.delete("progress_v2", "stable_key=?", arrayOf(stableKey)) }

    fun allStoredProgress(): Map<String, StoredProgress> = db.rawQuery(
        "SELECT stable_key,selected_answer,status,bookmark,review,attempts,last_attempted,time_ms,mistake_type,ease_factor,repetitions,interval_days,next_due FROM progress_v2", null
    ).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), StoredProgress(c.getStringOrNull(1), c.getStringOrNull(2), c.getStringOrNull(3), c.getInt(4) != 0, c.getInt(5), c.getLong(6), c.getLong(7), c.getStringOrNull(8), c.getFloat(9), c.getInt(10), c.getFloat(11), c.getLong(12))) }
    }

    fun getProgressMeta(key: String): String? = db.rawQuery("SELECT meta_value FROM progress_meta WHERE meta_key=? LIMIT 1", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
    fun putProgressMeta(key: String, value: String?) {
        if (value == null) db.delete("progress_meta", "meta_key=?", arrayOf(key))
        else db.execSQL("INSERT OR REPLACE INTO progress_meta(meta_key,meta_value) VALUES(?,?)", arrayOf(key, value))
    }
    fun allProgressMeta(): Map<String,String> = db.rawQuery("SELECT meta_key,meta_value FROM progress_meta", null).use { c -> buildMap { while(c.moveToNext()) put(c.getString(0), c.getString(1)) } }
    fun replaceStoredProgress(entries: Map<String, StoredProgress>, meta: Map<String,String>) {
        db.beginTransaction()
        try {
            db.delete("progress_v2", null, null); db.delete("progress_meta", null, null)
            val ps = db.compileStatement("INSERT INTO progress_v2(stable_key,selected_answer,status,bookmark,review,attempts,last_attempted,time_ms,mistake_type,ease_factor,repetitions,interval_days,next_due) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")
            entries.forEach { (key,v) ->
                ps.bindString(1,key); bindNullable(ps,2,v.selected); bindNullable(ps,3,v.status); bindNullable(ps,4,v.bookmark); ps.bindLong(5,if(v.review)1 else 0); ps.bindLong(6,v.attempts.toLong()); ps.bindLong(7,v.lastAttempted); ps.bindLong(8,v.timeMs); bindNullable(ps,9,v.mistake); ps.bindDouble(10,v.easeFactor.toDouble()); ps.bindLong(11,v.repetitions.toLong()); ps.bindDouble(12,v.intervalDays.toDouble()); ps.bindLong(13,v.nextDue); ps.executeInsert(); ps.clearBindings()
            }
            val ms = db.compileStatement("INSERT INTO progress_meta(meta_key,meta_value) VALUES(?,?)")
            meta.forEach { (k,v) -> ms.bindString(1,k); ms.bindString(2,v); ms.executeInsert(); ms.clearBindings() }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    fun beginProgressMetaTransaction(testId:String, position:Int, sourceId:Long, sourceName:String?) {
        db.beginTransaction()
        try {
            putProgressMeta(key = "${testId}:position", value = position.toString())
            if(sourceId>0L) putProgressMeta("source:$sourceId:resume", "$testId|$position")
            if(!sourceName.isNullOrBlank()) putProgressMeta("sourceName:$sourceName:resume", "$testId|$position")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun key(id:String,s:String)=id+":"+s

    fun close(){db.close()}
    fun sources(): List<Source> = db.rawQuery("SELECT id,COALESCE(display_name,file_name),provider,COALESCE(series_number,'') FROM source ORDER BY imported_at DESC",null).use{c->buildList{while(c.moveToNext())add(Source(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)))}}
    fun sourceNameForTest(testId:String):String? = db.rawQuery("SELECT COALESCE(s.display_name,s.file_name) FROM source s JOIN test t ON t.source_id=s.id WHERE t.id=? LIMIT 1", arrayOf(testId)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
    fun testById(testId:String):Test? = db.rawQuery("SELECT id,title,path,num_questions,total_marks,duration_seconds,time_per_question,COALESCE(series_number,'') FROM test WHERE id=? LIMIT 1",arrayOf(testId)).use { c -> if(c.moveToFirst()) Test(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getDouble(4),c.getInt(5),c.getDouble(6),c.getString(7)) else null }
    fun tests(sourceId:Long):List<Test> = db.rawQuery("SELECT id,title,path,num_questions,total_marks,duration_seconds,time_per_question,COALESCE(series_number,'') FROM test WHERE source_id=? ORDER BY rowid",arrayOf(sourceId.toString())).use{c->buildList{while(c.moveToNext())add(Test(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getDouble(4),c.getInt(5),c.getDouble(6),c.getString(7)))}}
    fun questionContext(questionId:Long):Pair<String,String> = db.rawQuery("SELECT COALESCE(t.title,''), COALESCE(t.path,'') FROM question q JOIN test t ON t.id=q.test_id WHERE q.id=? LIMIT 1",arrayOf(questionId.toString())).use { c ->
        if(c.moveToFirst()){ val title=c.getString(0).orEmpty(); val path=c.getString(1).orEmpty(); val subject=(path.split("/","\\",">","::").map{it.trim()}.filter{it.isNotBlank()}.firstOrNull() ?: title.substringBefore("-").trim()).ifBlank{"General"}; subject to title.ifBlank{"Question $questionId"} } else "Unlinked" to "Question $questionId"
    }

    fun questionCount(testId:String):Int = db.rawQuery("SELECT COUNT(*) FROM question WHERE test_id=?", arrayOf(testId)).use { c -> c.moveToFirst(); c.getInt(0) }

    fun rawQuestionPosition(testId:String, questionId:Long):Int? = db.rawQuery("SELECT position FROM question WHERE test_id=? AND id=? LIMIT 1", arrayOf(testId, questionId.toString())).use { c -> if (c.moveToFirst()) c.getInt(0) else null }

    fun questionAt(testId:String, position:Int):Question? {
        return db.rawQuery("SELECT id,position,source_question_id,COALESCE(text,''),raw_text,correct_answer,explanation,bot,video,audio FROM question WHERE test_id=? AND position=? LIMIT 1", arrayOf(testId, position.toString())).use { c ->
            if (!c.moveToFirst()) return@use null
            val id=c.getLong(0); val opts=mutableListOf<Option>()
            db.rawQuery("SELECT label,text,is_correct FROM option_item WHERE question_id=? ORDER BY position",arrayOf(id.toString())).use{o->while(o.moveToNext())opts.add(Option(o.getString(0) ?: "",o.getString(1) ?: "",o.getInt(2)!=0))}
            Question(id,stableKey(testId,c.getInt(1),c.getString(2)),c.getInt(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),opts,images(id,"question"),images(id,"explanation"))
        }
    }

    fun questionById(questionId:Long):Question? {
        return db.rawQuery("SELECT test_id,position FROM question WHERE id=? LIMIT 1", arrayOf(questionId.toString())).use { c ->
            if (!c.moveToFirst()) return@use null
            questionAt(c.getString(0), c.getInt(1))
        }
    }

    fun sourceIdForTest(testId:String):Long = db.rawQuery("SELECT source_id FROM test WHERE id=? LIMIT 1",arrayOf(testId)).use { c -> if(c.moveToFirst()) c.getLong(0) else 0L }
    fun testIdForQuestion(questionId:Long):String? = db.rawQuery("SELECT test_id FROM question WHERE id=? LIMIT 1", arrayOf(questionId.toString())).use { c -> if(c.moveToFirst()) c.getString(0) else null }

    fun questions(testId:String):List<Question>{
        val out=mutableListOf<Question>()
        db.rawQuery("SELECT id,position,source_question_id,text,raw_text,correct_answer,explanation,bot,video,audio FROM question WHERE test_id=? ORDER BY position",arrayOf(testId)).use{c->while(c.moveToNext()){
            val id=c.getLong(0); val opts=mutableListOf<Option>()
            db.rawQuery("SELECT label,text,is_correct FROM option_item WHERE question_id=? ORDER BY position",arrayOf(id.toString())).use{o->while(o.moveToNext())opts.add(Option(o.getString(0) ?: "",o.getString(1) ?: "",o.getInt(2)!=0))}
            out.add(Question(id,stableKey(testId,c.getInt(1),c.getString(2)),c.getInt(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getString(9),opts,images(id,"question"),images(id,"explanation")))
        }}
        return out
    }
    private fun images(qid:Long,kind:String)=db.rawQuery("SELECT uri FROM question_image WHERE question_id=? AND kind=? ORDER BY position",arrayOf(qid.toString(),kind)).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
    private fun stableKey(testId:String, position:Int, sourceQuestionId:String?):String = "$testId:$position:${sourceQuestionId.orEmpty()}"

    fun allStableKeys():List<String> = db.rawQuery("SELECT test_id,position,source_question_id FROM question ORDER BY rowid",null).use { c -> buildList { while(c.moveToNext()) add(stableKey(c.getString(0), c.getInt(1), c.getString(2))) } }

    /**
     * Question-reference index. Dashboard paths use lightweight=true so opening Rovex never
     * materializes every question's HTML/text just to count or route questions.
     */
    fun allQuestionRefs(lightweight: Boolean = false):List<QuestionRef> {
        val textExpr = if (lightweight) "''" else "COALESCE(q.text,'')"
        val sql = "SELECT q.id,q.test_id,q.position,q.source_question_id,$textExpr,t.title,t.path,s.id,COALESCE(s.display_name,s.file_name) FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id ORDER BY s.imported_at DESC,t.rowid,q.position"
        return db.rawQuery(sql,null).use{c->buildList{while(c.moveToNext()){
            val id=c.getLong(0); val testId=c.getString(1); val pos=c.getInt(2); val sid=c.getString(3)
            add(QuestionRef(id,"$testId:$pos:${sid ?: ""}",testId,pos,c.getString(4) ?: "",c.getString(5),c.getLong(7),c.getString(8),categoryFor(c.getString(6), c.getString(5), c.getString(8))))
        }}}
    }
    private fun categoryFor(path:String?, title:String, sourceName:String):String {
        fun useful(v:String):Boolean {
            val x=v.trim().lowercase()
            return x.isNotBlank() && x !in setOf("test","tests","section","sections","qbank","question bank","quiz","quizzes","practice")
        }
        val raw=(path ?: "").trim().trim('/').replace("\\", "/")
        val parts=raw.split('/').map{it.trim()}.filter{useful(it)}
        if(parts.isNotEmpty()) return parts.last().take(48)
        val titleParts=title.split(Regex("\\s*[-–—|:]\\s*"), limit=2).map{it.trim()}
        if(titleParts.isNotEmpty() && useful(titleParts.first())) return titleParts.first().take(48)
        val file=sourceName.substringBeforeLast('.', sourceName).trim()
        return if(useful(file)) file.take(48) else "General"
    }

    fun updateSourceMetadata(sourceId:Long, displayName:String, seriesNumber:String):Boolean {
        val name = displayName.trim().take(180)
        if (name.isBlank()) return false
        return try {
            db.execSQL("UPDATE source SET display_name=?,series_number=? WHERE id=?", arrayOf(name, seriesNumber.trim().take(80), sourceId))
            AppState.changed()
            true
        } catch (_: android.database.sqlite.SQLiteConstraintException) { false }
    }

    fun updateTestMetadata(testId:String, title:String, seriesNumber:String):Boolean {
        val name = title.trim().take(180)
        if (name.isBlank()) return false
        return try {
            db.execSQL("UPDATE test SET title=?,series_number=? WHERE id=?", arrayOf(name, seriesNumber.trim().take(80), testId))
            AppState.changed()
            true
        } catch (_: Exception) { false }
    }

    fun deleteSource(sourceId:Long){
        var changed=false
        db.beginTransaction()
        try{
            changed=db.delete("source","id=?",arrayOf(sourceId.toString()))>0
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
        if(changed) AppState.changed()
    }
    fun questionRefsForStableKeys(keys: Collection<String>): List<QuestionRef> {
        if (keys.isEmpty()) return emptyList()
        val parsed = keys.mapNotNull { key ->
            val first = key.indexOf(':')
            val last = key.lastIndexOf(':')
            if (first <= 0 || last <= first || last >= key.lastIndex) null
            else Triple(key.substring(0, first), key.substring(first + 1, last).toIntOrNull() ?: return@mapNotNull null, key.substring(last + 1))
        }.distinct()
        if (parsed.isEmpty()) return emptyList()
        val out = ArrayList<QuestionRef>(parsed.size)
        parsed.chunked(100).forEach { chunk ->
            val where = chunk.joinToString(" OR ") { "(q.test_id=? AND q.position=?)" }
            val args = chunk.flatMap { listOf(it.first, it.second.toString()) }.toTypedArray()
            db.rawQuery("SELECT q.id,q.test_id,q.position,COALESCE(q.source_question_id,''),COALESCE(q.text,''),COALESCE(t.title,''),s.id,COALESCE(s.display_name,s.file_name,''),COALESCE(t.path,'') FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE $where ORDER BY s.imported_at DESC,t.rowid,q.position", args).use { c ->
                while (c.moveToNext()) {
                    val testId=c.getString(1); val pos=c.getInt(2); val sourceQuestionId=c.getString(3)
                    out.add(QuestionRef(c.getLong(0), stableKey(testId,pos,sourceQuestionId), testId, pos, c.getString(4), c.getString(5), c.getLong(6), c.getString(7), categoryFor(c.getString(8), c.getString(5), c.getString(7))))
                }
            }
        }
        return out
    }

    fun questionRefsForIds(ids:LongArray):List<QuestionRef>{
        if(ids.isEmpty()) return emptyList()
        val wanted=ids.toSet(); return allQuestionRefs().filter{it.id in wanted}
    }
    fun questionIdsMatchingText(token:String):LongArray = search(token).map{it.id}.toLongArray()
    fun saveNote(questionId:Long,note:String){
        val cleaned=note.trim()
        db.beginTransaction()
        try {
            if(cleaned.isBlank()) {
                // An image can itself be the saved note. Keep the owning Notes row alive
                // while attachments exist so image+text relationships are never orphaned
                // from the Notes surface when the text is cleared.
                val hasImages=db.rawQuery(
                    "SELECT 1 FROM question_note_image WHERE question_id=? LIMIT 1",
                    arrayOf(questionId.toString())
                ).use { it.moveToFirst() }
                if(hasImages) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO question_note(question_id,note,updated_at) VALUES(?,?,?)",
                        arrayOf(questionId,"[Image note]",System.currentTimeMillis())
                    )
                } else {
                    db.delete("question_note","question_id=?",arrayOf(questionId.toString()))
                }
            } else {
                db.execSQL(
                    "INSERT OR REPLACE INTO question_note(question_id,note,updated_at) VALUES(?,?,?)",
                    arrayOf(questionId,cleaned,System.currentTimeMillis())
                )
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        AppState.changed()
    }
    fun deleteNote(questionId:Long){
        val paths=noteImages(questionId)
        db.beginTransaction()
        try {
            db.delete("question_note","question_id=?",arrayOf(questionId.toString()))
            db.delete("question_note_image","question_id=?",arrayOf(questionId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        paths.forEach { runCatching { File(it).delete() } }
        AppState.changed()
    }
    fun deleteNotes(questionIds:LongArray){
        if(questionIds.isEmpty()) return
        val ids=questionIds.distinct()
        val paths=ids.flatMap { noteImages(it) }
        db.beginTransaction()
        try {
            ids.chunked(500).forEach { chunk ->
                val args=chunk.map{it.toString()}.toTypedArray()
                val placeholders=chunk.joinToString(","){ "?" }
                db.delete("question_note","question_id IN ($placeholders)",args)
                db.delete("question_note_image","question_id IN ($placeholders)",args)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        paths.forEach { runCatching { File(it).delete() } }
        AppState.changed()
    }
    fun note(questionId:Long):String? = db.rawQuery("SELECT note FROM question_note WHERE question_id=?",arrayOf(questionId.toString())).use{c->if(c.moveToFirst())c.getString(0) else null}
    fun notes():List<Pair<Long,String>> = db.rawQuery("SELECT question_id,note FROM question_note ORDER BY updated_at DESC",null).use{c->buildList{while(c.moveToNext())add(c.getLong(0) to c.getString(1))}}
    data class NoteRecord(
        val questionId: Long, val note: String, val updatedAt: Long,
        val testId: String, val subject: String, val testTitle: String, val sourceName: String, val position: Int,
        val imagePaths: List<String> = emptyList()
    )

    fun noteCount(): Int = db.rawQuery("SELECT COUNT(*) FROM question_note", null).use { c -> c.moveToFirst(); c.getInt(0) }

    fun noteRecords(): List<NoteRecord> = db.rawQuery(
        "SELECT n.question_id,n.note,n.updated_at,COALESCE(t.id,''),COALESCE(t.path,''),COALESCE(t.title,''),COALESCE(s.display_name,s.file_name,''),q.position,COALESCE((SELECT group_concat(path, char(31)) FROM question_note_image ni WHERE ni.question_id=n.question_id), '') FROM question_note n LEFT JOIN question q ON q.id=n.question_id LEFT JOIN test t ON t.id=q.test_id LEFT JOIN source s ON s.id=t.source_id ORDER BY n.updated_at DESC", null
    ).use { c -> buildList {
        while (c.moveToNext()) {
            val testId = c.getString(3).orEmpty()
            val path = c.getString(4).orEmpty()
            val title = c.getString(5).orEmpty().ifBlank { "Question ${c.getLong(0)}" }
            val sourceName = c.getString(6).orEmpty().ifBlank { "Unknown QBank" }
            val subject = path.split('/', '\\', '>', ':').map { it.trim() }.firstOrNull { it.isNotBlank() }
                ?: title.split(Regex("\\s*[-–—|:]\\s*"), limit = 2).firstOrNull().orEmpty().trim().ifBlank { "General" }
            val images = c.getString(8).orEmpty().split(31.toChar()).map { it.trim() }.filter { it.isNotBlank() }
            add(NoteRecord(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), testId, subject, title, sourceName, c.getInt(7), images))
        }
    } }
    fun addNoteImage(questionId: Long, path: String) {
        if (path.isBlank()) return
        db.execSQL("INSERT INTO question_note_image(question_id,path,created_at) VALUES(?,?,?)", arrayOf(questionId, path, System.currentTimeMillis()))
        AppState.changed()
    }
    fun noteImages(questionId: Long): List<String> = db.rawQuery("SELECT path FROM question_note_image WHERE question_id=? ORDER BY created_at", arrayOf(questionId.toString())).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    fun replaceNotes(notes:List<Pair<Long,String>>){
        db.beginTransaction()
        try {
            db.delete("question_note", null, null)
            db.delete("question_note_image", null, null)
            val st=db.compileStatement("INSERT OR REPLACE INTO question_note(question_id,note,updated_at) VALUES(?,?,?)")
            notes.forEach { (id,note) -> if(note.isNotBlank()){ st.bindLong(1,id); st.bindString(2,note); st.bindLong(3,System.currentTimeMillis()); st.executeInsert(); st.clearBindings() } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        AppState.changed()
    }
    fun questionRefsByKeyword(keyword:String):List<QuestionRef>{
        val k=keyword.trim(); if(k.isBlank()) return emptyList()
        return allQuestionRefs().filter{it.testTitle.contains(k,true)||it.category.contains(k,true)||it.sourceName.contains(k,true)}
    }
    fun sourceQuestionCount(sourceId:Long):Int=db.rawQuery("SELECT COUNT(*) FROM question q JOIN test t ON t.id=q.test_id WHERE t.source_id=?",arrayOf(sourceId.toString())).use{c->c.moveToFirst();c.getInt(0)}
    fun testQuestionRefs(testId:String):List<QuestionRef> = allQuestionRefs().filter { it.testId == testId }
    fun progressSummaryForTest(testId:String, store:ProgressStore):ProgressSummary {
        var total=0; var solved=0; var correct=0; var resume=0; var foundResume=false
        db.rawQuery("SELECT position,source_question_id FROM question WHERE test_id=? ORDER BY position",arrayOf(testId)).use { c ->
            while(c.moveToNext()) {
                total++
                val key="$testId:${c.getInt(0)}:${c.getString(1)}"
                val st=store.status(key)
                if(st!=null){ solved++; if(st=="correct") correct++ } else if(!foundResume){ resume=c.getInt(0); foundResume=true }
            }
        }
        return ProgressSummary(total,solved,correct,resume)
    }
    fun progressSummaryForSource(sourceId:Long, store:ProgressStore):ProgressSummary {
        var total=0; var solved=0; var correct=0
        db.rawQuery("SELECT q.test_id,q.position,q.source_question_id FROM question q JOIN test t ON t.id=q.test_id WHERE t.source_id=? ORDER BY t.rowid,q.position",arrayOf(sourceId.toString())).use { c ->
            while(c.moveToNext()){ total++; val key="${c.getString(0)}:${c.getInt(1)}:${c.getString(2)}"; val st=store.status(key); if(st!=null){solved++;if(st=="correct")correct++} }
        }
        return ProgressSummary(total,solved,correct,0)
    }
    fun testProgressSummaries(sourceId:Long, progress:ProgressSnapshot):Map<String,ProgressSummary> {
        val out = LinkedHashMap<String, ProgressSummary>()
        db.rawQuery("SELECT test_id,position,source_question_id FROM question q JOIN test t ON t.id=q.test_id WHERE t.source_id=? ORDER BY t.rowid,q.position", arrayOf(sourceId.toString())).use { c ->
            var current: String? = null
            var total = 0; var solved = 0; var correct = 0; var resume = 0; var foundResume = false
            fun flush() {
                val id = current ?: return
                out[id] = ProgressSummary(total, solved, correct, resume)
            }
            while (c.moveToNext()) {
                val id = c.getString(0)
                if (current != null && current != id) { flush(); total = 0; solved = 0; correct = 0; resume = 0; foundResume = false }
                current = id
                total++
                val key = stableKey(id, c.getInt(1), c.getString(2))
                val state = progress.record(key)
                if (state?.status != null) { solved++; if (state.status == "correct") correct++ }
                else if (!foundResume) { resume = c.getInt(1); foundResume = true }
            }
            flush()
        }
        return out
    }

    private fun escapeLike(value:String):String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun search(query:String, resultLimit:Int = 400):List<SearchHit>{
        val tokens=query.trim().split(Regex("\\s+")).map{it.trim()}.filter{it.length>=2}.distinct().take(8)
        if(tokens.isEmpty()) return emptyList()
        // Prefer FTS4. Every token must occur somewhere in the indexed QBank/question metadata.
        try {
            val match=tokens.joinToString(" AND "){ it.replace(Regex("[^\\p{L}\\p{N}_]"), "") + "*" }
            return db.rawQuery("SELECT q.id,q.test_id,t.title,q.position,q.source_question_id,q.text,t.path,COALESCE(s.display_name,s.file_name) FROM question_fts f JOIN question q ON q.id=f.question_id JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE question_fts MATCH ? ORDER BY CASE WHEN lower(q.text) LIKE ? THEN 0 WHEN lower(t.title) LIKE ? OR lower(COALESCE(t.path,'')) LIKE ? OR lower(COALESCE(s.display_name,s.file_name)) LIKE ? THEN 1 ELSE 2 END, s.imported_at DESC,t.rowid,q.position LIMIT ${resultLimit.coerceIn(1, 1200)}", arrayOf(match, "%${escapeLike(query.trim().lowercase())}%", "%${escapeLike(query.trim().lowercase())}%", "%${escapeLike(query.trim().lowercase())}%", "%${escapeLike(query.trim().lowercase())}%")).use{c->buildList{while(c.moveToNext()) add(SearchHit(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(5) ?: "",c.getString(6),c.getString(7),stableKey(c.getString(1), c.getInt(3), c.getString(4))))}}
        } catch (_: Exception) { }
        // Safe fallback for SQLite builds without FTS4. Tokenized AND semantics avoid huge unbounded scans.
        val clauses=mutableListOf<String>(); val args=mutableListOf<String>()
        for(token in tokens){
            val like="%${escapeLike(token)}%"
            clauses += "(q.text LIKE ? ESCAPE '\\' OR q.raw_text LIKE ? ESCAPE '\\' OR q.explanation LIKE ? ESCAPE '\\' OR t.title LIKE ? ESCAPE '\\' OR COALESCE(t.path,'') LIKE ? ESCAPE '\\' OR COALESCE(s.display_name,s.file_name) LIKE ? ESCAPE '\\' OR EXISTS(SELECT 1 FROM option_item o WHERE o.question_id=q.id AND o.text LIKE ? ESCAPE '\\'))"
            repeat(7){args += like}
        }
        return db.rawQuery("SELECT q.id,q.test_id,t.title,q.position,q.source_question_id,q.text,t.path,COALESCE(s.display_name,s.file_name) FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE ${clauses.joinToString(" AND ")} ORDER BY s.imported_at DESC,t.rowid,q.position LIMIT ${resultLimit.coerceIn(1, 1200)}",args.toTypedArray()).use{c->buildList{while(c.moveToNext()) add(SearchHit(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(5) ?: "",c.getString(6),c.getString(7),stableKey(c.getString(1), c.getInt(3), c.getString(4))))}}
    }

    /**
     * Hard-filtered visual retrieval. A question must own at least one imported
     * question/explanation image; textual mentions of "image" are never sufficient.
     * This is intentionally separate from generic FTS search so a request such as
     * "pathology slides" can return image-bearing pathology questions whose stems
     * never contain the words slide/image.
     */
    fun searchImageQuestions(query:String, resultLimit:Int = 400):List<SearchHit>{
        val tokens=query.trim().split(Regex("\\s+")).map{it.trim()}.filter{it.length>=2}.distinct().take(8)
        if(tokens.isEmpty()) return emptyList()
        val safeLimit=resultLimit.coerceIn(1,1200)
        try {
            val match=tokens.joinToString(" AND "){ it.replace(Regex("[^\\p{L}\\p{N}_]"), "") + "*" }
            val sql="SELECT q.id,q.test_id,t.title,q.position,q.source_question_id,q.text,t.path,COALESCE(s.display_name,s.file_name) " +
                "FROM question_fts f JOIN question q ON q.id=f.question_id JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id " +
                "WHERE question_fts MATCH ? AND EXISTS(SELECT 1 FROM question_image qi WHERE qi.question_id=q.id) " +
                "ORDER BY s.imported_at DESC,t.rowid,q.position LIMIT $safeLimit"
            val result=db.rawQuery(sql,arrayOf(match)).use{c->buildList{while(c.moveToNext()) add(SearchHit(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(5) ?: "",c.getString(6),c.getString(7),stableKey(c.getString(1), c.getInt(3), c.getString(4))))}}
            if(result.isNotEmpty()) return result
        } catch (_: Exception) { }
        val clauses=tokens.map{"(q.text LIKE ? ESCAPE '\\\\' OR q.raw_text LIKE ? ESCAPE '\\\\' OR q.explanation LIKE ? ESCAPE '\\\\' OR t.title LIKE ? ESCAPE '\\\\' OR COALESCE(t.path,'') LIKE ? ESCAPE '\\\\' OR COALESCE(s.display_name,s.file_name) LIKE ? ESCAPE '\\\\' OR EXISTS(SELECT 1 FROM option_item o WHERE o.question_id=q.id AND o.text LIKE ? ESCAPE '\\\\'))"}
        val args=tokens.flatMap{val like="%${escapeLike(it)}%";List(7){like}}
        val sql="SELECT q.id,q.test_id,t.title,q.position,q.source_question_id,q.text,t.path,COALESCE(s.display_name,s.file_name) FROM question q JOIN test t ON t.id=q.test_id JOIN source s ON s.id=t.source_id WHERE EXISTS(SELECT 1 FROM question_image qi WHERE qi.question_id=q.id) AND ${clauses.joinToString(" AND ")} ORDER BY s.imported_at DESC,t.rowid,q.position LIMIT $safeLimit"
        return db.rawQuery(sql,args.toTypedArray()).use{c->buildList{while(c.moveToNext()) add(SearchHit(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(5) ?: "",c.getString(6),c.getString(7),stableKey(c.getString(1), c.getInt(3), c.getString(4))))}}
    }

    fun searchSections(query:String):List<SectionHit>{
        val tokens=query.trim().split(Regex("\\s+")).map{it.trim()}.filter{it.length>=2}.distinct().take(8)
        if(tokens.isEmpty()) return emptyList()
        val clauses=tokens.map{ "(t.title LIKE ? ESCAPE '\\' OR COALESCE(t.path,'') LIKE ? ESCAPE '\\' OR COALESCE(s.display_name,s.file_name) LIKE ? ESCAPE '\\')" }
        val args=tokens.flatMap{ val like="%${escapeLike(it)}%"; listOf(like,like,like) }
        return db.rawQuery("SELECT t.id,t.title,COALESCE(t.path,''),COALESCE(s.display_name,s.file_name),t.num_questions FROM test t JOIN source s ON s.id=t.source_id WHERE ${clauses.joinToString(" AND ")} ORDER BY s.imported_at DESC,t.rowid LIMIT 200",args.toTypedArray()).use{c->buildList{while(c.moveToNext()) add(SectionHit(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)))}}
    }

    fun beginStreamingImport(fileName:String, provider:String): StreamingImportSession {
        var sourceId = 0L
        db.beginTransaction()
        try {
            db.delete("source", "file_name=?", arrayOf(fileName))
            val st=db.compileStatement("INSERT INTO source(file_name,display_name,series_number,provider,imported_at) VALUES(?,?,?,?,?)")
            st.bindString(1,fileName); st.bindString(2,fileName); st.bindString(3,""); st.bindString(4,provider); st.bindLong(5,System.currentTimeMillis())
            sourceId=st.executeInsert()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return StreamingImportSession(sourceId, fileName)
    }

    inner class StreamingImportSession(private val sourceId:Long, private val fileName:String) {
        private val tests=LinkedHashMap<String,Pair<String,Int>>()
        private var total=0
        private var closed=false
        private var inTransaction=false
        // Reuse compiled statements across the streaming session. This avoids reparsing SQL
        // for every question/option/image and materially reduces large-import CPU overhead.
        private val questionStatement = db.compileStatement("INSERT INTO question(test_id,position,source_question_id,text,raw_text,correct_answer,explanation,bot,video,audio) VALUES(?,?,?,?,?,?,?,?,?,?)")
        private val optionStatement = db.compileStatement("INSERT INTO option_item(question_id,position,label,text,is_correct) VALUES(?,?,?,?,?)")
        private val imageStatement = db.compileStatement("INSERT INTO question_image(question_id,kind,position,uri) VALUES(?,?,?,?)")
        private val ftsStatement: android.database.sqlite.SQLiteStatement? = try {
            db.compileStatement("INSERT INTO question_fts(question_id,test_id,source_id,content) VALUES(?,?,?,?)")
        } catch (_: Exception) { null }

        init { beginTransaction() }

        private fun beginTransaction() {
            if (!inTransaction && !closed) {
                db.beginTransaction()
                inTransaction=true
            }
        }

        /** Commit the questions accumulated for the current HTML section, then start a fresh transaction. */
        fun commitSection() {
            if (closed || !inTransaction) return
            try {
                for ((_,pair) in tests) db.execSQL("UPDATE test SET num_questions=? WHERE id=?", arrayOf(pair.second,pair.first))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                inTransaction=false
            }
            beginTransaction()
        }

        fun addQuestion(section:String, q:ImportedQuestion) {
            check(!closed) { "Import session is closed" }
            beginTransaction()
            val title=section.trim().ifBlank { "Imported Section" }.take(120)
            val test=tests.getOrPut(title) {
                val original=title
                val id=makeId(fileName, original, tests.size)
                db.execSQL("INSERT INTO test(id,source_id,title,path,num_questions,total_marks,duration_seconds,time_per_question,original_id,series_number) VALUES(?,?,?,?,?,?,?,?,?,?)", arrayOf(id,sourceId,title,null,0,0.0,0,0.0,original,""))
                id to 0
            }
            val testId=test.first
            val pos=test.second
            questionStatement.bindString(1,testId); questionStatement.bindLong(2,pos.toLong()); bindNullable(questionStatement,3,q.sourceId); bindNullable(questionStatement,4,q.text); bindNullable(questionStatement,5,q.rawText); bindNullable(questionStatement,6,q.correctAnswer); bindNullable(questionStatement,7,q.explanation); bindNullable(questionStatement,8,q.bot); bindNullable(questionStatement,9,q.video); bindNullable(questionStatement,10,q.audio)
            val qid=questionStatement.executeInsert(); questionStatement.clearBindings()
            q.options.forEachIndexed { i,o -> optionStatement.bindLong(1,qid); optionStatement.bindLong(2,i.toLong()); bindNullable(optionStatement,3,o.label); bindNullable(optionStatement,4,o.text); optionStatement.bindLong(5,if(o.correct)1L else 0L); optionStatement.executeInsert(); optionStatement.clearBindings() }
            q.questionImages.forEachIndexed { i,u -> imageStatement.bindLong(1,qid); imageStatement.bindString(2,"question"); imageStatement.bindLong(3,i.toLong()); imageStatement.bindString(4,u); imageStatement.executeInsert(); imageStatement.clearBindings() }
            q.explanationImages.forEachIndexed { i,u -> imageStatement.bindLong(1,qid); imageStatement.bindString(2,"explanation"); imageStatement.bindLong(3,i.toLong()); imageStatement.bindString(4,u); imageStatement.executeInsert(); imageStatement.clearBindings() }
            ftsStatement?.let { st ->
                try { st.bindLong(1,qid); st.bindString(2,testId); st.bindLong(3,sourceId); st.bindString(4,q.text+" "+(q.rawText ?: "")+" "+(q.explanation ?: "")+" "+title+" "+q.options.joinToString(" "){it.text}+" "+fileName); st.executeInsert() } catch (_: Exception) {} finally { st.clearBindings() }
            }
            tests[title]=testId to (pos+1)
            total++
            // Keep very large single-section QBanks from holding one SQLite transaction for hours.
            if(total % 250 == 0) commitSection()
        }

        /** Roll back only the active transaction and remove any already-committed partial import. */
        fun abort() {
            if (closed) return
            try {
                if (inTransaction) {
                    db.endTransaction()
                    inTransaction=false
                }
                db.beginTransaction()
                db.delete("source", "id=?", arrayOf(sourceId.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                questionStatement.close(); optionStatement.close(); imageStatement.close(); ftsStatement?.close()
                closed=true
            }
        }

        fun finish(success:Boolean) {
            if(closed) return
            if (!success) { abort(); return }
            try {
                for ((_,pair) in tests) db.execSQL("UPDATE test SET num_questions=? WHERE id=?", arrayOf(pair.second,pair.first))
                if(inTransaction) db.setTransactionSuccessful()
            } finally {
                if(inTransaction) db.endTransaction()
                inTransaction=false
                questionStatement.close(); optionStatement.close(); imageStatement.close(); ftsStatement?.close()
                closed=true
            }
        }
    }

    fun importBundle(fileName:String, provider:String, tests:List<ImportedTest>, onProgress:(done:Int,total:Int)->Unit = {_,_->}):Int {
        var importedCount = 0
        db.beginTransaction()
        val sourceStatement=db.compileStatement("INSERT INTO source(file_name,display_name,series_number,provider,imported_at) VALUES(?,?,?,?,?)")
        val testStatement=db.compileStatement("INSERT INTO test(id,source_id,title,path,num_questions,total_marks,duration_seconds,time_per_question,original_id,series_number) VALUES(?,?,?,?,?,?,?,?,?,?)")
        val questionStatement=db.compileStatement("INSERT INTO question(test_id,position,source_question_id,text,raw_text,correct_answer,explanation,bot,video,audio) VALUES(?,?,?,?,?,?,?,?,?,?)")
        val optionStatement=db.compileStatement("INSERT INTO option_item(question_id,position,label,text,is_correct) VALUES(?,?,?,?,?)")
        val imageStatement=db.compileStatement("INSERT INTO question_image(question_id,kind,position,uri) VALUES(?,?,?,?)")
        val ftsStatement:android.database.sqlite.SQLiteStatement?=try{db.compileStatement("INSERT INTO question_fts(question_id,test_id,source_id,content) VALUES(?,?,?,?)")}catch(_:Exception){null}
        try {
            db.delete("source","file_name=?",arrayOf(fileName))
            sourceStatement.bindString(1,fileName);sourceStatement.bindString(2,fileName);sourceStatement.bindString(3,"");sourceStatement.bindString(4,provider);sourceStatement.bindLong(5,System.currentTimeMillis());val sourceId=sourceStatement.executeInsert();sourceStatement.clearBindings()
            var count=0
            val totalQuestions = tests.sumOf { it.questions.size }.coerceAtLeast(1)
            for ((ti,t) in tests.withIndex()) {
                val testId=makeId(fileName,t.originalId ?: t.title,ti)
                testStatement.bindString(1,testId);testStatement.bindLong(2,sourceId);testStatement.bindString(3,t.title);bindNullable(testStatement,4,t.path);testStatement.bindLong(5,t.questions.size.toLong());testStatement.bindDouble(6,t.totalMarks);testStatement.bindLong(7,t.duration.toLong());testStatement.bindDouble(8,t.timePerQuestion);bindNullable(testStatement,9,t.originalId);testStatement.bindString(10,t.seriesNumber ?: "");testStatement.executeInsert();testStatement.clearBindings()
                for ((qi,q) in t.questions.withIndex()) {
                    questionStatement.bindString(1,testId);questionStatement.bindLong(2,qi.toLong());bindNullable(questionStatement,3,q.sourceId);bindNullable(questionStatement,4,q.text);bindNullable(questionStatement,5,q.rawText);bindNullable(questionStatement,6,q.correctAnswer);bindNullable(questionStatement,7,q.explanation);bindNullable(questionStatement,8,q.bot);bindNullable(questionStatement,9,q.video);bindNullable(questionStatement,10,q.audio);val qid=questionStatement.executeInsert();questionStatement.clearBindings()
                    q.options.forEachIndexed{oi,o->optionStatement.bindLong(1,qid);optionStatement.bindLong(2,oi.toLong());bindNullable(optionStatement,3,o.label);bindNullable(optionStatement,4,o.text);optionStatement.bindLong(5,if(o.correct)1L else 0L);optionStatement.executeInsert();optionStatement.clearBindings()}
                    q.questionImages.forEachIndexed{ii,u->imageStatement.bindLong(1,qid);imageStatement.bindString(2,"question");imageStatement.bindLong(3,ii.toLong());imageStatement.bindString(4,u);imageStatement.executeInsert();imageStatement.clearBindings()}
                    q.explanationImages.forEachIndexed{ii,u->imageStatement.bindLong(1,qid);imageStatement.bindString(2,"explanation");imageStatement.bindLong(3,ii.toLong());imageStatement.bindString(4,u);imageStatement.executeInsert();imageStatement.clearBindings()}
                    ftsStatement?.let { st ->
                        try { st.bindLong(1,qid);st.bindString(2,testId);st.bindLong(3,sourceId);st.bindString(4,q.text+" "+(q.rawText ?: "")+" "+(q.explanation ?: "")+" "+t.title+" "+q.options.joinToString(" "){it.text}+" "+t.title);st.executeInsert() } catch(_:Exception){} finally{st.clearBindings()}
                    }
                    count++
                    if (count == 1 || count % 25 == 0 || count == totalQuestions) onProgress(count,totalQuestions)
                }
            }
            db.setTransactionSuccessful()
            importedCount=count
        } finally {
            db.endTransaction()
            sourceStatement.close();testStatement.close();questionStatement.close();optionStatement.close();imageStatement.close();ftsStatement?.close()
        }
        AppState.changed("import_completed")
        return importedCount
    }
    private fun bindNullable(st:android.database.sqlite.SQLiteStatement,i:Int,v:String?){if(v==null)st.bindNull(i) else st.bindString(i,v)}
    private fun makeId(file:String,original:String,index:Int):String{val raw="$file|$original|$index";return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){String.format("%02x",it)}.take(24)}
}

data class Source(val id:Long,val fileName:String,val provider:String,val seriesNumber:String="")
data class Test(val id:String,val title:String,val path:String?,val count:Int,val marks:Double,val duration:Int,val timePerQuestion:Double,val seriesNumber:String="")
data class Option(val label:String,val text:String,val correct:Boolean)
data class Question(val id:Long,val stableKey:String,val position:Int,val sourceId:String?,val text:String,val rawText:String?,val correctAnswer:String?,val explanation:String?,val bot:String?,val video:String?,val audio:String?,val options:List<Option>,val questionImages:List<String>,val explanationImages:List<String>)

data class AiIndexRow(val id:Long,val text:String,val explanation:String,val testTitle:String,val path:String,val sourceName:String)
data class QuestionRef(val id:Long,val stableKey:String,val testId:String,val position:Int,val text:String,val testTitle:String,val sourceId:Long,val sourceName:String,val category:String)
data class SearchHit(val id:Long,val testId:String,val testTitle:String,val position:Int,val text:String,val path:String?,val sourceName:String,val stableKey:String)
data class SectionHit(val testId:String,val title:String,val path:String,val sourceName:String,val count:Int)
data class ProgressSummary(val total:Int,val solved:Int,val correct:Int,val resumePosition:Int)
data class ImportedTest(val originalId:String?,val title:String,val path:String?,val totalMarks:Double,val duration:Int,val timePerQuestion:Double,val questions:List<ImportedQuestion>,val seriesNumber:String?=null)
data class ImportedQuestion(val sourceId:String?,val text:String,val rawText:String?,val correctAnswer:String?,val explanation:String?,val bot:String?,val video:String?,val audio:String?,val options:List<ImportedOption>,val questionImages:List<String>,val explanationImages:List<String>)
data class ImportedOption(val label:String,val text:String,val correct:Boolean)

private object ProgressDbRegistry {
    @Volatile private var instance: QBankDb? = null
    fun get(context: Context): QBankDb {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: QBankDb(context.applicationContext).also { instance = it }
        }
    }
}

class ProgressStore(context: Context) : ProgressRepository, AutoCloseable {
    private val appContext = context.applicationContext
    private val db = ProgressDbRegistry.get(appContext)
    private val legacyPrefs = appContext.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val migrationKey = "__sqlite_progress_v2_migrated"
    private val fields = setOf("answer", "status", "bookmark", "review", "attempts", "lastAttempted", "timeMs", "mistake", "ef", "reps", "intervalDays", "nextDue")

    init { synchronized(db) { migrateLegacyIfNeeded() } }

    private fun key(id:String,s:String)=id+":"+s
    private fun changed(cloud:Boolean=true){
        PerformanceManager.invalidateProgress()
        AppState.changed()
        BackupManager(appContext).onProgressChanged(cloud)
    }

    private fun stored(id: String): QBankDb.StoredProgress = db.readStoredProgress(id)
        ?: QBankDb.StoredProgress(null,null,null,false,0,0L,0L,null,2.5f,0,0f,0L)

    override fun selected(id:String)=stored(id).selected
    override fun status(id:String)=stored(id).status
    override fun bookmark(id:String)=stored(id).bookmark
    override fun review(id:String)=stored(id).review
    override fun position(testId:String):Int=db.getProgressMeta(key(testId,"position"))?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    override fun sourceResume(sourceId:Long):String?=db.getProgressMeta("source:$sourceId:resume")
    override fun sourceResumeByName(sourceName:String):String?=db.getProgressMeta("sourceName:${sourceName}:resume")

    override fun setPosition(testId:String,position:Int,sourceId:Long,sourceName:String?){
        val value=position.coerceAtLeast(0)
        db.beginProgressMetaTransaction(testId, value, sourceId, sourceName)
        changed(false)
    }

    override fun easeFactor(id:String)=stored(id).easeFactor
    override fun repetitions(id:String)=stored(id).repetitions
    override fun intervalDays(id:String)=stored(id).intervalDays
    override fun nextDue(id:String)=stored(id).nextDue
    override fun allProgressKeys():Set<String> = synchronized(db) { db.allStoredProgress().keys }

    override fun setAnswer(id:String,a:String,status:String){
        synchronized(db) {
        val previous=stored(id)
        val result=SpacedRepetitionScheduler.schedule(
            SpacedRepetitionScheduler.ScheduleInput(
                correct=status=="correct", easeFactor=previous.easeFactor,
                repetitions=previous.repetitions, previousIntervalDays=previous.intervalDays
            ), now=System.currentTimeMillis()
        )
        db.writeStoredProgress(id, previous.copy(
            selected=a, status=status, attempts=previous.attempts+1,
            lastAttempted=System.currentTimeMillis(), easeFactor=result.easeFactor,
            repetitions=result.repetitions, intervalDays=result.intervalDays, nextDue=result.nextDueAt
        ))
        changed()
        }
    }

    override fun isDue(id:String):Boolean{
        val x=stored(id); if(x.status==null)return true
        if(x.lastAttempted==0L)return true
        if(x.nextDue>0L)return System.currentTimeMillis()>=x.nextDue
        val day=86_400_000L
        return System.currentTimeMillis()-x.lastAttempted>=when(x.attempts){1->day;2->3*day;3->7*day;4->14*day;else->30*day}
    }
    override fun isScheduledDue(id:String):Boolean{
        val x=stored(id); if(x.status.isNullOrBlank())return false
        if(x.nextDue>0L)return System.currentTimeMillis()>=x.nextDue
        if(x.lastAttempted<=0L)return false
        val day=86_400_000L
        return System.currentTimeMillis()-x.lastAttempted>=when(x.attempts){1->day;2->3*day;3->7*day;4->14*day;else->30*day}
    }
    override fun attempts(id:String)=stored(id).attempts
    override fun lastAttempted(id:String)=stored(id).lastAttempted
    override fun timeMs(id:String)=stored(id).timeMs
    override fun mistakeType(id:String)=stored(id).mistake
    override fun setMistakeType(id:String,value:String?){ synchronized(db) { db.writeStoredProgress(id,stored(id).copy(mistake=value?.takeIf{it.isNotBlank()})) }; changed() }
    override fun addTime(id:String,elapsedMs:Long){
        if(elapsedMs<=0L)return
        synchronized(db) {
            val x=stored(id)
            db.writeStoredProgress(id,x.copy(timeMs=x.timeMs+elapsedMs))
        }
        changed(false)
    }
    override fun setBookmark(id:String,value:String?){
        synchronized(db) { db.writeStoredProgress(id,stored(id).copy(bookmark=value?.takeIf{it.isNotBlank()})) }
        changed()
    }
    override fun setReview(id:String,value:Boolean){
        synchronized(db) { db.writeStoredProgress(id,stored(id).copy(review=value)) }
        changed()
    }
    override fun clear(id:String){ synchronized(db) { db.deleteStoredProgress(id) }; changed() }

    /** Export-compatible view. Values retain the old key names so existing backup format stays stable. */
    override fun allEntries():Map<String,*> = synchronized(db) { buildMap {
        db.allStoredProgress().forEach { (id,x) ->
            x.selected?.let { put(key(id,"answer"),it) }; x.status?.let { put(key(id,"status"),it) }
            x.bookmark?.let { put(key(id,"bookmark"),it) }; if(x.review) put(key(id,"review"),true)
            if(x.attempts>0) put(key(id,"attempts"),x.attempts); if(x.lastAttempted>0) put(key(id,"lastAttempted"),x.lastAttempted)
            if(x.timeMs>0) put(key(id,"timeMs"),x.timeMs); x.mistake?.let { put(key(id,"mistake"),it) }
            put(key(id,"ef"),x.easeFactor); put(key(id,"reps"),x.repetitions); put(key(id,"intervalDays"),x.intervalDays); put(key(id,"nextDue"),x.nextDue)
        }
        putAll(db.allProgressMeta())
    } }

    override fun restore(entries:Map<String,*>){
        restoreInternal(entries, notify=true)
    }

    /** Backup restore uses this to avoid publishing an intermediate state before notes/UI/flashcards commit. */
    internal fun restoreSilently(entries:Map<String,*>) { restoreInternal(entries, notify=false) }

    private fun restoreInternal(entries:Map<String,*>, notify:Boolean){
        synchronized(db) {
            val grouped=HashMap<String,MutableMap<String,Any?>>()
            val meta=HashMap<String,String>()
            entries.forEach { (full,value) ->
                val suffix=full.substringAfterLast(":","")
                if(suffix in fields){ val id=full.substringBeforeLast(":"); grouped.getOrPut(id){HashMap()}[suffix]=value }
                else if(suffix=="position" || full.startsWith("source:") || full.startsWith("sourceName:") || full==migrationKey) meta[full]=value?.toString().orEmpty()
            }
            val out=grouped.mapValues { (_,m) ->
                fun str(k:String)=m[k] as? String
                fun long(k:String)=when(val v=m[k]){is Long->v;is Int->v.toLong();is Float->v.toLong();is Double->v.toLong();is Number->v.toLong();else->0L}
                fun int(k:String)=long(k).toInt()
                fun float(k:String,default:Float)=when(val v=m[k]){is Number->v.toFloat();else->default}
                QBankDb.StoredProgress(str("answer"),str("status"),str("bookmark"),m["review"]==true,int("attempts"),long("lastAttempted"),long("timeMs"),str("mistake"),float("ef",2.5f),int("reps"),float("intervalDays",0f),long("nextDue"))
            }
            db.replaceStoredProgress(out,meta)
        }
        if(notify) changed()
    }

    private fun migrateLegacyIfNeeded(){
        if(db.getProgressMeta(migrationKey)=="1") return
        val entries=legacyPrefs.all
        if(entries.isNotEmpty()) restoreInternal(entries, notify=false)
        db.putProgressMeta(migrationKey,"1")
        // Keep the legacy preferences intact for one release as a rollback/source-of-truth snapshot.
        // New writes never touch them. They can be removed in a later cleanup release after backup validation.
    }

    // The SQLite handle is process-scoped because ProgressStore is used by multiple authoritative
    // managers and Activities. Closing one repository instance must never invalidate another.
    override fun close() = Unit
}


