package com.localqbank.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlin.math.roundToInt

/** Dedicated flashcard store. QBank data never shares this database. */
class FlashcardDb(context: Context) {
    private val dbFile: File = context.getDatabasePath("flashcards.db")
    private val db: SQLiteDatabase

    init {
        dbFile.parentFile?.mkdirs()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
        db.execSQL("CREATE TABLE IF NOT EXISTS deck(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, source_file TEXT, imported_at INTEGER NOT NULL, card_count INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS card(id INTEGER PRIMARY KEY AUTOINCREMENT, deck_id INTEGER NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, tags TEXT, position INTEGER NOT NULL, due INTEGER DEFAULT 0, interval INTEGER DEFAULT 0, ease REAL DEFAULT 2.5, reps INTEGER DEFAULT 0, lapses INTEGER DEFAULT 0, bookmarked INTEGER DEFAULT 0, last_reviewed INTEGER DEFAULT 0, FOREIGN KEY(deck_id) REFERENCES deck(id) ON DELETE CASCADE, UNIQUE(deck_id,position))")
        ensureColumn("card", "bookmarked", "INTEGER DEFAULT 0")
        ensureColumn("card", "last_reviewed", "INTEGER DEFAULT 0")
        ensureColumn("card", "suspended", "INTEGER DEFAULT 0")
        ensureColumn("card", "marked", "INTEGER DEFAULT 0")
        ensureColumn("card", "last_rating", "INTEGER DEFAULT 0")
        ensureColumn("card", "due", "INTEGER DEFAULT 0")
        ensureColumn("card", "interval", "INTEGER DEFAULT 0")
        ensureColumn("card", "ease", "REAL DEFAULT 2.5")
        ensureColumn("card", "reps", "INTEGER DEFAULT 0")
        ensureColumn("card", "lapses", "INTEGER DEFAULT 0")
        ensureColumn("card", "prev_due", "INTEGER DEFAULT 0")
        ensureColumn("card", "prev_interval", "INTEGER DEFAULT 0")
        ensureColumn("card", "prev_ease", "REAL DEFAULT 2.5")
        ensureColumn("card", "prev_reps", "INTEGER DEFAULT 0")
        ensureColumn("card", "prev_lapses", "INTEGER DEFAULT 0")
        ensureColumn("card", "prev_last_reviewed", "INTEGER DEFAULT 0")
        ensureColumn("card", "prev_rating", "INTEGER DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS review_log(id INTEGER PRIMARY KEY AUTOINCREMENT, card_id INTEGER NOT NULL, reviewed_at INTEGER NOT NULL, rating INTEGER NOT NULL, old_interval INTEGER NOT NULL, new_interval INTEGER NOT NULL, FOREIGN KEY(card_id) REFERENCES card(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS flash_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        seedSetting("new_per_day", "30")
        seedSetting("max_reviews_per_day", "200")
        seedSetting("max_cards_per_day", "300")
        seedSetting("learning_again_min", "10")
        seedSetting("initial_interval_days", "1")
        seedSetting("easy_multiplier", "1.30")
        seedSetting("hard_multiplier", "1.20")
        seedSetting("leech_threshold", "8")
        seedSetting("today_new_limit", "0")
        seedSetting("today_review_limit", "0")
        seedSetting("today_total_limit", "0")
        seedSetting("today_override_date", "")
        seedSetting("desired_retention", "0.90")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_flash_deck ON card(deck_id,position)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_flash_due ON card(due,deck_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_flash_bookmarked ON card(bookmarked,deck_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_flash_deck_name ON deck(name COLLATE NOCASE)")
    }

    private fun ensureColumn(table:String,column:String,definition:String) {
        val exists = db.rawQuery("PRAGMA table_info($table)",null).use { c ->
            var found=false; while(c.moveToNext()) if(c.getString(1).equals(column,true)){found=true;break}; found
        }
        if(!exists) {
            try { db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition") }
            catch (e:Exception) {
                val stillExists = db.rawQuery("PRAGMA table_info($table)",null).use { c -> var found=false; while(c.moveToNext()) if(c.getString(1).equals(column,true)){found=true;break}; found }
                if(!stillExists) throw IllegalStateException("Flashcard database migration failed for $table.$column", e)
            }
        }
    }

    data class Deck(val id:Long,val name:String,val count:Int,val source:String?,val depth:Int,val parentName:String?)
    data class DueStats(val due:Int,val learning:Int,val hard:Int,val good:Int,val easy:Int)
    data class Card(val id:Long,val deckId:Long,val front:String,val back:String,val tags:String?,val position:Int,val bookmarked:Boolean,val due:Long,val interval:Int,val ease:Float,val reps:Int,val lapses:Int,val suspended:Boolean,val marked:Boolean,val lastRating:Int)

    private fun seedSetting(key:String, value:String){ db.execSQL("INSERT OR IGNORE INTO flash_settings(key,value) VALUES(?,?)", arrayOf(key,value)) }
    fun setting(key:String, default:String):String = db.rawQuery("SELECT value FROM flash_settings WHERE key=?",arrayOf(key)).use{c->if(c.moveToFirst())c.getString(0) else {seedSetting(key,default);default}}
    fun setSetting(key:String,value:String){db.execSQL("INSERT OR REPLACE INTO flash_settings(key,value) VALUES(?,?)",arrayOf(key,value))}
    fun settingInt(key:String,default:Int)=setting(key,default.toString()).toIntOrNull()?:default
    fun settingDouble(key:String,default:Double)=setting(key,default.toString()).toDoubleOrNull()?:default
    fun settingsSnapshot():Map<String,String> = db.rawQuery("SELECT key,value FROM flash_settings ORDER BY key",null).use { c ->
        buildMap { while(c.moveToNext()) put(c.getString(0),c.getString(1)) }
    }

    fun normalizeDeckName(raw:String):String {
        val n=raw.replace('／','/').replace('›','>').replace('»','>')
            .replace(Regex("\\s*::\\s*"),"::").trim(':',' ','\t','\n')
        val parts=n.split("::").map{it.trim()}.filter{it.isNotBlank()}
        return if(parts.isEmpty())"Uncategorized" else parts.joinToString("::")
    }

    fun upsertDeck(rawName:String,source:String,count:Int):Long {
        val name=normalizeDeckName(rawName)
        var id=0L
        db.beginTransaction(); try {
            id=upsertDeckInternal(name,source,count,true)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        // Notify only after commit so deck-library screens never render the pre-commit DB.
        AppState.changed()
        return id
    }

    /** Import-only transaction: one SQLite transaction for the complete APKG. */
    fun beginImport(){ db.beginTransaction() }
    fun finishImport(success:Boolean, notify:Boolean=true){
        if(success) db.setTransactionSuccessful()
        db.endTransaction()
        if(success && notify) AppState.changed()
    }
    fun upsertDeckForImport(rawName:String,source:String):Long {
        val name=normalizeDeckName(rawName)
        // Importer clears an old source once at the start of a fresh import.
        // Never delete cards here: this method is also used when resuming a checkpoint.
        return upsertDeckInternal(name,source,0,false)
    }
    private fun upsertDeckInternal(name:String,source:String,count:Int,replaceCards:Boolean):Long {
        db.execSQL("INSERT OR IGNORE INTO deck(name,source_file,imported_at,card_count) VALUES(?,?,?,?)",arrayOf<Any?>(name,source,System.currentTimeMillis(),count))
        val id=db.rawQuery("SELECT id FROM deck WHERE name=?",arrayOf(name)).use{c->if(c.moveToFirst())c.getLong(0) else throw IllegalStateException("Unable to create deck")}
        db.execSQL("UPDATE deck SET source_file=?, imported_at=?, card_count=? WHERE id=?",arrayOf<Any?>(source,System.currentTimeMillis(),count,id))
        if(replaceCards) db.delete("card","deck_id=?",arrayOf(id.toString()))
        return id
    }
    /** Fast import path: caller wraps the whole import in one transaction. */
    fun insertCard(deckId:Long,front:String,back:String,tags:String?,position:Int){
        db.execSQL("INSERT OR REPLACE INTO card(deck_id,front,back,tags,position,due) VALUES(?,?,?,?,?,?)",arrayOf<Any?>(deckId,front,back,tags ?: "",position,0L))
    }
    fun setDeckCount(deckId:Long,count:Int){db.execSQL("UPDATE deck SET card_count=? WHERE id=?",arrayOf(count,deckId))}

    /** Idempotent insert/update used only by an explicit user-triggered flashcard action.
     * Existing manually generated cards with the same front are corrected in-place. */
    fun upsertGeneratedCard(deckId:Long,front:String,back:String,tags:String?):Boolean {
        val existing=db.rawQuery("SELECT id,back,tags FROM card WHERE deck_id=? AND front=? ORDER BY id LIMIT 1",arrayOf(deckId.toString(),front)).use{c->
            if(c.moveToFirst()) Triple(c.getLong(0),c.getString(1),c.getString(2)) else null
        }
        if(existing!=null){
            if(existing.second==back && existing.third==(tags ?: "")) return false
            db.execSQL("UPDATE card SET back=?,tags=? WHERE id=?",arrayOf(back,tags ?: "",existing.first))
            return true
        }
        val next=db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM card WHERE deck_id=?",arrayOf(deckId.toString())).use{it.moveToFirst();it.getInt(0)}
        db.execSQL("INSERT INTO card(deck_id,front,back,tags,position,due) VALUES(?,?,?,?,?,?)",arrayOf(deckId,front,back,tags ?: "",next,0L))
        return true
    }

    fun decks():List<Deck> = db.rawQuery("SELECT id,name,card_count,source_file FROM deck ORDER BY name COLLATE NOCASE",null).use{c->buildList{while(c.moveToNext()){val name=c.getString(1);val parts=name.split("::");add(Deck(c.getLong(0),name,c.getInt(2),c.getString(3),parts.size-1,parts.dropLast(1).joinToString("::").ifBlank{null}))}}}
    fun cardCount(deckId:Long):Int=db.rawQuery("SELECT COUNT(*) FROM card WHERE deck_id=?",arrayOf(deckId.toString())).use{c->c.moveToFirst();c.getInt(0)}
    fun reviewCount():Int=db.rawQuery("SELECT COUNT(*) FROM card WHERE due<=? AND suspended=0",arrayOf(System.currentTimeMillis().toString())).use{c->c.moveToFirst();c.getInt(0)}
    fun reviewedToday():Int { val start=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis; return db.rawQuery("SELECT COUNT(*) FROM review_log WHERE reviewed_at>=?",arrayOf(start.toString())).use{c->c.moveToFirst();c.getInt(0)} }
    fun totalReviews():Int=db.rawQuery("SELECT COUNT(*) FROM review_log",null).use{c->c.moveToFirst();c.getInt(0)}
    fun learningCount():Int=db.rawQuery("SELECT COUNT(*) FROM card WHERE interval<=0 AND reps=0 AND suspended=0",null).use{c->c.moveToFirst();c.getInt(0)}
    fun bookmarkCount():Int=db.rawQuery("SELECT COUNT(*) FROM card WHERE bookmarked=1",null).use{c->c.moveToFirst();c.getInt(0)}
    fun bookmarkedCount(deckId:Long):Int=db.rawQuery("SELECT COUNT(*) FROM card WHERE deck_id=? AND bookmarked=1",arrayOf(deckId.toString())).use{c->c.moveToFirst();c.getInt(0)}
    fun dueStatsAll():DueStats {
        val now=System.currentTimeMillis()
        return db.rawQuery("SELECT interval,ease,lapses FROM card WHERE due<=?",arrayOf(now.toString())).use { c ->
            var learning=0; var hard=0; var good=0; var easy=0
            while(c.moveToNext()){
                val interval=c.getInt(0); val ease=c.getFloat(1); val lapses=c.getInt(2)
                when { interval<=0 || (lapses>0 && interval<=1) -> learning++
                    ease < 2.2f -> hard++
                    ease >= 2.7f -> easy++
                    else -> good++
                }
            }
            DueStats(learning+hard+good+easy,learning,hard,good,easy)
        }
    }

    fun dueStats(deckId:Long):DueStats {
        val now=System.currentTimeMillis()
        return db.rawQuery("SELECT interval,ease,lapses FROM card WHERE deck_id=? AND due<=?",arrayOf(deckId.toString(),now.toString())).use { c ->
            var learning=0; var hard=0; var good=0; var easy=0
            while(c.moveToNext()){
                val interval=c.getInt(0); val ease=c.getFloat(1); val lapses=c.getInt(2)
                when { interval<=0 || (lapses>0 && interval<=1) -> learning++
                    ease < 2.2f -> hard++
                    ease >= 2.7f -> easy++
                    else -> good++
                }
            }
            DueStats(learning+hard+good+easy,learning,hard,good,easy)
        }
    }

    private fun readCard(sql:String,args:Array<String>):Card?=db.rawQuery(sql,args).use{c->if(c.moveToFirst())Card(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getInt(6)!=0,c.getLong(7),c.getInt(8),c.getFloat(9),c.getInt(10),c.getInt(11),c.getInt(12)!=0,c.getInt(13)!=0,c.getInt(14))else null}
    fun cardAt(deckId:Long,pos:Int):Card?=readCard("SELECT id,deck_id,front,back,tags,position,bookmarked,due,interval,ease,reps,lapses,suspended,marked,last_rating FROM card WHERE deck_id=? AND position=? AND suspended=0",arrayOf(deckId.toString(),pos.toString()))
    fun reviewCardAt(pos:Int):Card?=readCard("SELECT id,deck_id,front,back,tags,position,bookmarked,due,interval,ease,reps,lapses,suspended,marked,last_rating FROM card WHERE due<=? AND suspended=0 ORDER BY due,id LIMIT 1 OFFSET ?",arrayOf(System.currentTimeMillis().toString(),pos.toString()))
    fun bookmarkedCardAt(pos:Int):Card?=readCard("SELECT id,deck_id,front,back,tags,position,bookmarked,due,interval,ease,reps,lapses,suspended,marked,last_rating FROM card WHERE bookmarked=1 AND suspended=0 ORDER BY id LIMIT 1 OFFSET ?",arrayOf(pos.toString()))

    /** Count is always the actual SQLite cardinality; no 100-card bucket/series is used. */
    fun continuousCardCount(mode:String,deckId:Long=0L):Int = modeCount(mode,deckId)

    fun modeCount(mode:String,deckId:Long=0L):Int {
        val now=System.currentTimeMillis()
        val start=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
        val reviewedTodayCount=reviewedToday()
        val today=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date())
        val overridesActive=setting("today_override_date","")==today
        fun effectiveLimit(normalKey:String, todayKey:String, default:Int):Int {
            val normal=settingInt(normalKey,default).coerceAtLeast(0)
            if(!overridesActive) return normal
            val override=settingInt(todayKey,0)
            // UI contract: 0 means "use normal limit", not "allow zero cards".
            return if(override>0) override else normal
        }
        val effectiveReviewLimit=effectiveLimit("max_reviews_per_day","today_review_limit",200)
        val effectiveNewLimit=effectiveLimit("new_per_day","today_new_limit",30)
        val effectiveTotalLimit=effectiveLimit("max_cards_per_day","today_total_limit",300)
        val totalLeft=(effectiveTotalLimit-reviewedTodayCount).coerceAtLeast(0)
        val dailyReviewCount=db.rawQuery("SELECT COUNT(*) FROM review_log WHERE reviewed_at>=? AND old_interval>0",arrayOf(start.toString())).use{c->c.moveToFirst();c.getInt(0)}
        val dailyReviewLeft=minOf((effectiveReviewLimit-dailyReviewCount).coerceAtLeast(0),totalLeft)
        val dailyNewCount=db.rawQuery("SELECT COUNT(*) FROM review_log WHERE reviewed_at>=? AND old_interval=0",arrayOf(start.toString())).use{c->c.moveToFirst();c.getInt(0)}
        val dailyNewLeft=minOf((effectiveNewLimit-dailyNewCount).coerceAtLeast(0),totalLeft)
        val where=when(mode){
            "due"->"due<=? AND suspended=0";"hard"->"last_rating=2 AND suspended=0";"again"->"last_rating=1 AND suspended=0";
            "unseen"->"reps=0 AND suspended=0";"marked"->"marked=1 AND suspended=0";"bookmarked"->"bookmarked=1 AND suspended=0";else->"suspended=0"
        }
        val args0=if(mode=="due")arrayOf(now.toString())else emptyArray<String>();val scope=if(deckId>0)" AND deck_id=?"else"";val a=if(deckId>0)args0+deckId.toString()else args0
        val raw=db.rawQuery("SELECT COUNT(*) FROM card WHERE $where$scope",a).use{c->c.moveToFirst();c.getInt(0)}
        return when(mode){"due"->minOf(raw,dailyReviewLeft);"unseen"->minOf(raw,dailyNewLeft);else->raw}
    }
    fun modeCardAt(mode:String,pos:Int,deckId:Long=0L):Card? {
        val base="SELECT id,deck_id,front,back,tags,position,bookmarked,due,interval,ease,reps,lapses,suspended,marked,last_rating FROM card WHERE "
        val cond=when(mode){
            "due" -> "due<=? AND suspended=0"
            "hard" -> "last_rating=2 AND suspended=0"
            "again" -> "last_rating=1 AND suspended=0"
            "unseen" -> "reps=0 AND suspended=0"
            "marked" -> "marked=1 AND suspended=0"
            "bookmarked" -> "bookmarked=1 AND suspended=0"
            else -> "suspended=0"
        }
        val args0=if(mode=="due") arrayOf(System.currentTimeMillis().toString()) else emptyArray<String>()
        val scope=if(deckId>0) " AND deck_id=?" else ""
        val args=if(deckId>0) args0 + deckId.toString() else args0
        return readCard("$base$cond$scope ORDER BY ${if(deckId>0)"position,id" else "due,id"} LIMIT 1 OFFSET ?",args + pos.toString())
    }
    fun setMarked(cardId:Long,value:Boolean){db.execSQL("UPDATE card SET marked=? WHERE id=?",arrayOf(if(value)1 else 0,cardId));AppState.changed()}
    fun toggleMarked(cardId:Long):Boolean { val c=db.rawQuery("SELECT marked FROM card WHERE id=?",arrayOf(cardId.toString())); val v=c.use{it.moveToFirst() && it.getInt(0)!=0}; setMarked(cardId,!v); return !v }
    fun setSuspended(cardId:Long,value:Boolean){db.execSQL("UPDATE card SET suspended=? WHERE id=?",arrayOf(if(value)1 else 0,cardId));AppState.changed()}

    fun setBookmarked(cardId:Long,value:Boolean){db.execSQL("UPDATE card SET bookmarked=? WHERE id=?",arrayOf(if(value)1 else 0,cardId));AppState.changed()}
    fun toggleBookmarked(cardId:Long):Boolean{val now=!isBookmarked(cardId);setBookmarked(cardId,now);return now}
    fun isBookmarked(cardId:Long):Boolean=db.rawQuery("SELECT bookmarked FROM card WHERE id=?",arrayOf(cardId.toString())).use{c->c.moveToFirst()&&c.getInt(0)!=0}

    /** Robust, configurable SRS. The algorithm intentionally stays deterministic/offline. */
    fun review(cardId:Long,rating:Int){
        if(rating !in 1..4) return
        val row: LongArray = db.rawQuery("SELECT interval,ease,reps,lapses,due,last_reviewed,last_rating FROM card WHERE id=?",arrayOf(cardId.toString())).use { cur ->
            if(cur.moveToFirst()) LongArray(7).also {
                it[0]=cur.getLong(0); it[1]=cur.getDouble(1).toBits(); it[2]=cur.getLong(2); it[3]=cur.getLong(3); it[4]=cur.getLong(4); it[5]=cur.getLong(5); it[6]=cur.getLong(6)
            } else null
        } ?: return
        var interval=row[0].toInt().coerceAtLeast(0); var ease=Double.fromBits(row[1]).coerceIn(1.3,3.5); var reps=row[2].toInt().coerceAtLeast(0); var lapses=row[3].toInt().coerceAtLeast(0)
        val initialDays=settingInt("initial_interval_days",1).coerceIn(1,3650)
        val easyMultiplier=settingDouble("easy_multiplier",1.30).coerceIn(1.01,5.0)
        val hardMultiplier=settingDouble("hard_multiplier",1.20).coerceIn(1.01,5.0)
        val leechThreshold=settingInt("leech_threshold",8).coerceIn(1,100)
        when(rating){
            1 -> { interval=0; lapses++; reps=0; ease=(ease-0.20).coerceAtLeast(1.3) }
            2 -> { interval=if(interval<=0) initialDays else maxOf(1,(interval*hardMultiplier).roundToInt()); ease=(ease-0.15).coerceAtLeast(1.3); reps++ }
            3 -> { interval=when{interval<=0->initialDays; interval<initialDays->initialDays; else->maxOf(2,(interval*ease).roundToInt())}; reps++ }
            4 -> { interval=when{interval<=0->maxOf(initialDays,(initialDays*easyMultiplier).roundToInt()); interval<initialDays->initialDays; else->maxOf(3,(interval*ease*easyMultiplier).roundToInt())}; ease=(ease+0.15).coerceAtMost(3.5); reps++ }
        }
        // Leech cards remain visible but are suspended after the configured number of failures.
        val shouldSuspend = lapses >= leechThreshold
        val day=24L*60L*60L*1000L
        val delay=when(rating){1->settingInt("learning_again_min",10).coerceIn(1,1440)*60_000L;2->if(row[0]<=0)day else interval*day;3->if(row[0]<=1)initialDays*day else interval*day;else->if(row[0]<=1)interval*day else interval*day}
        val now=System.currentTimeMillis(); val due=now+delay
        db.beginTransaction(); try {
            db.execSQL("UPDATE card SET prev_due=?,prev_interval=?,prev_ease=?,prev_reps=?,prev_lapses=?,prev_last_reviewed=?,prev_rating=?,interval=?,ease=?,reps=?,lapses=?,due=?,last_reviewed=?,last_rating=?,suspended=? WHERE id=?",arrayOf(row[4],row[0],Double.fromBits(row[1]),row[2],row[3],row[5],row[6],interval,ease,reps,lapses,due,now,rating,if(shouldSuspend)1 else 0,cardId))
            db.execSQL("INSERT INTO review_log(card_id,reviewed_at,rating,old_interval,new_interval) VALUES(?,?,?,?,?)",arrayOf(cardId,now,rating,row[0],interval))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        AppState.changed()
    }

    fun undoLast(cardId:Long):Boolean {
        val row=db.rawQuery("SELECT prev_due,prev_interval,prev_ease,prev_reps,prev_lapses,prev_last_reviewed,prev_rating FROM card WHERE id=?",arrayOf(cardId.toString())).use{c->if(c.moveToFirst())arrayOf(c.getLong(0).toString(),c.getInt(1).toString(),c.getDouble(2).toString(),c.getInt(3).toString(),c.getInt(4).toString(),c.getLong(5).toString(),c.getInt(6).toString())else null} ?: return false
        if(row[5]=="0" && row[6]=="0") return false
        db.execSQL("UPDATE card SET due=?,interval=?,ease=?,reps=?,lapses=?,last_reviewed=?,last_rating=? WHERE id=?",arrayOf(row[0],row[1],row[2],row[3],row[4],row[5],row[6],cardId))
        db.execSQL("DELETE FROM review_log WHERE id=(SELECT id FROM review_log WHERE card_id=? ORDER BY id DESC LIMIT 1)",arrayOf(cardId.toString()))
        AppState.changed(); return true
    }

    fun nextDayMillis():Long { val c=java.util.Calendar.getInstance(); c.add(java.util.Calendar.DAY_OF_YEAR,1); c.set(java.util.Calendar.HOUR_OF_DAY,0);c.set(java.util.Calendar.MINUTE,0);c.set(java.util.Calendar.SECOND,0);c.set(java.util.Calendar.MILLISECOND,0);return c.timeInMillis }
    fun buryUntilTomorrow(cardId:Long){db.execSQL("UPDATE card SET due=? WHERE id=?",arrayOf(nextDayMillis(),cardId));AppState.changed()}
    fun unsuspendDeck(deckId:Long, includeChildren:Boolean=false):Int { val pattern=db.rawQuery("SELECT name FROM deck WHERE id=?",arrayOf(deckId.toString())).use{c->if(c.moveToFirst())c.getString(0)else null}?:return 0; val where=if(includeChildren)"name=? OR name LIKE ?" else "id=?"; val args=if(includeChildren)arrayOf(pattern,pattern+"::%")else arrayOf(deckId.toString()); val ids=mutableListOf<String>();db.rawQuery("SELECT id FROM deck WHERE $where",args).use{c->while(c.moveToNext())ids.add(c.getLong(0).toString())};if(ids.isEmpty())return 0;var n=0;db.beginTransaction();try{ids.forEach{n+=db.update("card",android.content.ContentValues().apply{put("suspended",0)},"deck_id=?",arrayOf(it))};db.setTransactionSuccessful()}finally{db.endTransaction()};AppState.changed();return n}

    /**
     * Deletes a deck and, optionally, every descendant. We explicitly remove dependent rows
     * before the deck row as a defensive measure for databases imported/upgraded from older
     * schemas where foreign-key enforcement may not have been present historically.
     */
    fun deleteDeck(deckId:Long,includeSubdecks:Boolean=false):Int{
        val target=db.rawQuery("SELECT name FROM deck WHERE id=?",arrayOf(deckId.toString())).use{c->if(c.moveToFirst())c.getString(0)else null}?:return 0
        val ids=mutableListOf<String>()
        db.rawQuery(
            if(includeSubdecks)"SELECT id FROM deck WHERE name=? OR name LIKE ?" else "SELECT id FROM deck WHERE id=?",
            if(includeSubdecks)arrayOf(target,target+"::%")else arrayOf(deckId.toString())
        ).use{c->while(c.moveToNext())ids.add(c.getLong(0).toString())}
        return deleteDeckIds(ids)
    }

    /** Deletes a displayed category/tree even when the category itself has no deck row. */
    fun deleteDeckTree(name:String):Int{
        val target=normalizeDeckName(name)
        val ids=mutableListOf<String>()
        db.rawQuery("SELECT id FROM deck WHERE name=? OR name LIKE ?",arrayOf(target,target+"::%")).use{c->while(c.moveToNext())ids.add(c.getLong(0).toString())}
        return deleteDeckIds(ids)
    }

    private fun deleteDeckIds(ids:List<String>):Int{
        if(ids.isEmpty()) return 0
        var removed=0
        db.beginTransaction()
        try{
            ids.forEach{deckId->
                // review_log references card; clear it first for maximum compatibility.
                db.delete("review_log","card_id IN (SELECT id FROM card WHERE deck_id=?)",arrayOf(deckId))
                db.delete("card","deck_id=?",arrayOf(deckId))
                removed += db.delete("deck","id=?",arrayOf(deckId))
            }
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
        if(removed>0) AppState.changed()
        return removed
    }
    fun deleteSource(sourceFile:String):Int{val ids=mutableListOf<String>();db.rawQuery("SELECT id FROM deck WHERE source_file=?",arrayOf(sourceFile)).use{c->while(c.moveToNext())ids.add(c.getLong(0).toString())};if(ids.isEmpty())return 0;db.beginTransaction();try{ids.forEach{db.delete("deck","id=?",arrayOf(it))};db.setTransactionSuccessful()}finally{db.endTransaction()};AppState.changed();return ids.size}
    fun sourceNames(): List<String> = db.rawQuery("SELECT DISTINCT source_file FROM deck WHERE source_file IS NOT NULL AND source_file<>'' ORDER BY source_file COLLATE NOCASE",null).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
    fun close(){db.close()}
}
