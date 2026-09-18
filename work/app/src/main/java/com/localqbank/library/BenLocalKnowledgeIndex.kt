package com.localqbank.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.Locale

/**
 * Lightweight persistent knowledge substrate for Ben.
 *
 * It deliberately stores compact concept/edge statistics rather than duplicating the QBank.
 * The canonical question corpus remains QBankDb + its existing FTS4 index. This layer adds
 * semantic-ish local graph memory and a bounded reranking signal without a new dependency.
 * All work is lazy: no database is opened from AppManagers startup.
 */
class BenLocalKnowledgeIndex(context: Context) {
    private val app = context.applicationContext
    private val dbFileName = "ben_knowledge_index.db"

    data class Evidence(val questionId: Long, val score: Int, val concept: String)
    data class Retrieval(val evidence: List<Evidence>, val edgeCount: Int)
    data class Stats(val concepts: Int, val edges: Int, val observations: Long)
    data class Observation(val questionId: Long, val concepts: List<String>, val domains: Set<String>)

    private fun open(): SQLiteDatabase {
        val db = app.openOrCreateDatabase(dbFileName, Context.MODE_PRIVATE, null)
        db.enableWriteAheadLogging()
        db.execSQL("CREATE TABLE IF NOT EXISTS concept(id TEXT PRIMARY KEY, label TEXT NOT NULL, domains TEXT NOT NULL, seen INTEGER NOT NULL DEFAULT 0, wrong INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS edge(source TEXT NOT NULL, target TEXT NOT NULL, weight INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(source,target))")
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        return db
    }

    fun observe(query: String, concepts: List<String>, domains: Set<String>) {
        val normalized = concepts.map(::normalize).filter { it.length >= 3 }.distinct().take(8)
        if (normalized.isEmpty()) return
        val db = runCatching { open() }.getOrNull() ?: return
        try {
            db.beginTransaction()
            normalized.forEach { concept ->
                db.execSQL("INSERT OR IGNORE INTO concept(id,label,domains) VALUES(?,?,?)", arrayOf(concept, concept, domains.joinToString("|")))
                db.execSQL("UPDATE concept SET seen=MIN(seen+1,100000) WHERE id=?", arrayOf(concept))
            }
            for (i in normalized.indices) for (j in i + 1 until normalized.size) {
                val a = normalized[i]; val b = normalized[j]
                db.execSQL("INSERT OR IGNORE INTO edge(source,target,weight) VALUES(?,?,0)", arrayOf(a,b))
                db.execSQL("INSERT OR IGNORE INTO edge(source,target,weight) VALUES(?,?,0)", arrayOf(b,a))
                db.execSQL("UPDATE edge SET weight=MIN(weight+1,100000) WHERE source=? AND target=?", arrayOf(a,b))
                db.execSQL("UPDATE edge SET weight=MIN(weight+1,100000) WHERE source=? AND target=?", arrayOf(b,a))
            }
            val old = db.rawQuery("SELECT value FROM meta WHERE key='observations'", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            db.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES('observations',?)", arrayOf((old + 1).coerceAtMost(1000000L).toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction(); db.close()
        }
    }

    /** Batch ingestion path used by the background QBank learner. One transaction per bounded batch. */
    fun observeBatch(observations: List<Observation>) {
        if (observations.isEmpty()) return
        val db = runCatching { open() }.getOrNull() ?: return
        try {
            db.beginTransaction()
            var added = 0L
            observations.forEach { observation ->
                val normalized = observation.concepts.map(::normalize).filter { it.length >= 3 }.distinct().take(8)
                if (normalized.isEmpty()) return@forEach
                normalized.forEach { concept ->
                    db.execSQL("INSERT OR IGNORE INTO concept(id,label,domains) VALUES(?,?,?)", arrayOf(concept, concept, observation.domains.joinToString("|")))
                    db.execSQL("UPDATE concept SET seen=MIN(seen+1,100000) WHERE id=?", arrayOf(concept))
                }
                for (i in normalized.indices) for (j in i + 1 until normalized.size) {
                    val a = normalized[i]; val b = normalized[j]
                    db.execSQL("INSERT OR IGNORE INTO edge(source,target,weight) VALUES(?,?,0)", arrayOf(a,b))
                    db.execSQL("INSERT OR IGNORE INTO edge(source,target,weight) VALUES(?,?,0)", arrayOf(b,a))
                    db.execSQL("UPDATE edge SET weight=MIN(weight+1,100000) WHERE source=? AND target=?", arrayOf(a,b))
                    db.execSQL("UPDATE edge SET weight=MIN(weight+1,100000) WHERE source=? AND target=?", arrayOf(b,a))
                }
                added++
            }
            val old = db.rawQuery("SELECT value FROM meta WHERE key='observations'", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            db.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES('observations',?)", arrayOf((old + added).coerceAtMost(1000000L).toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    /**
     * Uses the canonical QBank FTS index for recall, then reranks locally using concept overlap
     * and learned graph connectivity. Never loads the full corpus into memory.
     */
    fun retrieve(query: String, concepts: List<String>, limit: Int): Retrieval {
        val clean = query.trim()
        if (clean.length < 2) return Retrieval(emptyList(), 0)
        val db = QBankDb(app)
        val hits = try {
            db.search(clean, 80)
        } finally { db.close() }
        val wanted = concepts.map(::normalize).filter { it.length >= 3 }.toSet()
        val localDb = runCatching { open() }.getOrNull() ?: return Retrieval(hits.take(limit).map { Evidence(it.id, 0, "") }, 0)
        try {
            // Two-hop graph expansion is deliberately bounded. The graph stores local co-occurrence
            // evidence, not authoritative clinical relationships, so it is used only as a retrieval
            // boost and never as a medical fact source.
            val expandedWanted = linkedSetOf<String>().apply {
                addAll(wanted.take(8))
                wanted.take(6).forEach { seed ->
                    directNeighbors(localDb, seed, 6).forEach { add(it) }
                }
            }
            val out = hits.map { hit ->
                val text = hit.text.lowercase(Locale.US)
                val matched = wanted.filter { text.contains(it) }
                val hopMatched = expandedWanted.filter { it !in wanted && text.contains(it) }.take(4)
                val concept = (matched.firstOrNull() ?: hopMatched.firstOrNull()).orEmpty()
                val edgeBoost = if (concept.isBlank()) 0 else edgeWeight(localDb, concept, wanted)
                val score = matched.size * 12 + hopMatched.size * 4 + edgeBoost.coerceAtMost(24)
                Evidence(hit.id, score, concept)
            }.sortedByDescending { it.score }.take(limit.coerceIn(1, 20))
            val edges = if (wanted.isEmpty()) 0 else {
                val placeholders = wanted.joinToString(",") { "?" }
                localDb.rawQuery("SELECT COUNT(*) FROM edge WHERE source IN ($placeholders)", wanted.toTypedArray()).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            }
            return Retrieval(out, edges)
        } finally { localDb.close() }
    }

    fun stats(): Stats {
        val db = runCatching { open() }.getOrNull() ?: return Stats(0,0,0)
        try {
            fun count(table: String): Int = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getInt(0) }
            val observations = db.rawQuery("SELECT value FROM meta WHERE key='observations'", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            return Stats(count("concept"), count("edge"), observations)
        } finally { db.close() }
    }

    fun reset() {
        val db = runCatching { open() }.getOrNull() ?: return
        try { db.beginTransaction(); db.delete("edge", null, null); db.delete("concept", null, null); db.delete("meta", null, null); db.setTransactionSuccessful() }
        finally { db.endTransaction(); db.close() }
    }


    private fun directNeighbors(db: SQLiteDatabase, source: String, limit: Int): List<String> =
        db.rawQuery(
            "SELECT target FROM edge WHERE source=? ORDER BY weight DESC LIMIT ?",
            arrayOf(source, limit.coerceIn(1, 8).toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun edgeWeight(db: SQLiteDatabase, concept: String, wanted: Set<String>): Int {
        if (wanted.size < 2) return 0
        val others = wanted.filter { it != concept }.take(6)
        if (others.isEmpty()) return 0
        val args = ArrayList<String>(others.size + 1).apply { add(concept); addAll(others) }
        val placeholders = others.joinToString(",") { "?" }
        return db.rawQuery("SELECT COALESCE(SUM(weight),0) FROM edge WHERE source=? AND target IN ($placeholders)", args.toTypedArray()).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ").trim().take(80)
}
