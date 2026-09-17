package com.localqbank.library

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Durable progress backup. The QBank HTML/database is never copied into these small backups. */
class BackupManager(private val context: Context, private val initializeScheduling: Boolean = true) {
    companion object {
        const val PREFS = "backup_settings"
        const val TREE_URI = "backup_tree_uri"
        const val LAST_BACKUP = "last_backup"
        const val BACKUP_NAME = "Q_Progress_latest.qbackup"
        private const val AUTO_LATEST_NAME = "Rovex_Progress_latest.qbackup"
        private const val AUTO_PREFIX = "Rovex_Progress_"
        private const val AUTO_FOLDER = "Rovex/Backups"
        private const val AUTO_RELATIVE_PATH_SUFFIX = "/Rovex/Backups/"
        private const val LAST_PUBLIC_BACKUP = "last_public_backup"
        private const val LOCAL_DIR = "q_backups"
        private const val AUTO_LOCAL_DIR = "progress_backups"
        private const val FULL_BACKUP_NAME = "Rovex_Full_Backup.qbackup"
        private const val FULL_FORMAT = 1
        private const val RESTORE_STAGING = "restore_staging"
        private val io = Executors.newSingleThreadExecutor()
        private val snapshotQueued = AtomicBoolean(false)
        private val generation = AtomicLong(0)
        private val lastCloudSchedule = AtomicLong(0)
        private val lastLocalSchedule = AtomicLong(0)
    }
    private val app = context.applicationContext
    private val settings = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    init {
        if (initializeScheduling) {
            scheduleAutomaticLocal()
            val last=PrefsCompat.long(settings,LAST_BACKUP,0L)
            if(System.currentTimeMillis()-last > 6*60*60*1000L){
                val request=OneTimeWorkRequestBuilder<LocalAutomaticBackupWorker>().setInitialDelay(5,TimeUnit.SECONDS).addTag("rovex_automatic_backup_startup").build()
                WorkManager.getInstance(app).enqueueUniqueWork("rovex_automatic_backup_startup",ExistingWorkPolicy.KEEP,request)
            }
        }
    }

    fun onProgressChanged(cloud:Boolean=true) {
        // SQLite progress is committed synchronously by ProgressStore.
        // Coalesce rapid quiz navigation into one background snapshot; never block the UI.
        generation.incrementAndGet()
        // Local snapshots are durability aids, not part of the answer transaction. Debounce them
        // so a fast 50-question session does not perform dozens of JSON serializations and fsyncs.
        val nowLocal = System.currentTimeMillis()
        val previousLocal = lastLocalSchedule.get()
        if (nowLocal - previousLocal >= 1_500L && lastLocalSchedule.compareAndSet(previousLocal, nowLocal)) {
            if(snapshotQueued.compareAndSet(false,true)) io.submit {
                try { writeLocalSnapshot() } finally { snapshotQueued.set(false) }
            }
        }
        if(!cloud) return
        val uri = settings.getString(TREE_URI, null) ?: return
        // Do not enqueue/replace WorkManager work for every tap/answer. Cloud backup is
        // intentionally debounced; local progress is already persisted immediately.
        val now = System.currentTimeMillis()
        val previous = lastCloudSchedule.get()
        if (now - previous < 30_000L) return
        if (!lastCloudSchedule.compareAndSet(previous, now)) return
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setInitialDelay(2, TimeUnit.MINUTES)
            .setInputData(workDataOf("treeUri" to uri))
            .addTag("q_cloud_backup")
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("q_cloud_backup_after_changes", ExistingWorkPolicy.REPLACE, request)
    }

    /** Queue an immediate local durability checkpoint without touching the UI thread.
     * Used when Android tells the process its UI is leaving the foreground. The SQLite progress
     * record is already authoritative; this only tightens the backup freshness window.
     */
    fun flushLocalSnapshot() {
        if (!snapshotQueued.compareAndSet(false, true)) return
        io.submit {
            try {
                writeLocalSnapshot()
            } finally {
                snapshotQueued.set(false)
            }
        }
    }

    fun setTreeUri(uri: Uri) {
        try { app.contentResolver.takePersistableUriPermission(uri, IntentFlags.READ_WRITE) } catch (_: Exception) {}
        settings.edit().putString(TREE_URI, uri.toString()).apply()
        schedulePeriodic()
    }

    fun clearTreeUri() { settings.edit().remove(TREE_URI).apply(); WorkManager.getInstance(app).cancelUniqueWork("q_cloud_backup_after_changes"); WorkManager.getInstance(app).cancelUniqueWork("q_cloud_backup_periodic") }
    fun treeUri(): Uri? = settings.getString(TREE_URI, null)?.let { Uri.parse(it) }
    fun lastBackup(): Long = PrefsCompat.long(settings,LAST_BACKUP,0L)

    fun flushCloudBackup() {
        val uri=settings.getString(TREE_URI,null) ?: return
        val request=OneTimeWorkRequestBuilder<CloudBackupWorker>().setInputData(workDataOf("treeUri" to uri)).addTag("q_cloud_backup_flush").build()
        WorkManager.getInstance(app).enqueueUniqueWork("q_cloud_backup_flush",ExistingWorkPolicy.REPLACE,request)
    }

    /** Creates a complete, self-contained Rovex backup. This is intentionally manual because it
     * can be much larger than the lightweight automatic progress snapshot. It includes both
     * SQLite databases, all user SharedPreferences except backup transport state, and durable
     * app-owned files such as flashcard media/knowledge. Temporary import/cache/backup staging
     * data is excluded. SQLite snapshots use VACUUM INTO so WAL state is captured consistently. */
    fun createFullBackup(): File? {
        return synchronized(BackupManager::class.java) {
            try {
                val dir = File(app.filesDir, LOCAL_DIR).apply { mkdirs() }
                val target = File(dir, FULL_BACKUP_NAME)
                val tmp = File(dir, ".tmp_full.qbackup")
                val snapshotDir = File(app.cacheDir, "full_backup_db_snapshots").apply { mkdirs() }
                val qSnapshot = File(snapshotDir, "qbank.db")
                val fSnapshot = File(snapshotDir, "flashcards.db")
                qSnapshot.delete(); fSnapshot.delete()
                snapshotDatabase("qbank.db", qSnapshot)
                snapshotDatabase("flashcards.db", fSnapshot)

                val files = mutableListOf<Pair<String, File>>()
                files += "database/qbank.db" to qSnapshot
                files += "database/flashcards.db" to fSnapshot
                val prefsDir = File(app.applicationInfo.dataDir, "shared_prefs")
                prefsDir.listFiles()?.filter { it.isFile && it.extension == "xml" && it.name != "${PREFS}.xml" }
                    ?.forEach { files += "shared_prefs/${it.name}" to it }
                collectUserFiles(app.filesDir, "files", files)

                val manifest = JSONObject()
                    .put("format", FULL_FORMAT)
                    .put("type", "full")
                    .put("appVersion", app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown")
                    .put("createdAt", System.currentTimeMillis())
                    .put("package", app.packageName)
                    .put("files", org.json.JSONArray().apply {
                        files.forEach { (name, file) -> put(JSONObject().put("path", name).put("size", file.length()).put("sha256", sha256File(file))) }
                    })
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zip ->
                    putZipText(zip, "manifest.json", manifest.toString())
                    val buffer = ByteArray(1024 * 1024)
                    files.forEach { (name, file) ->
                        zip.putNextEntry(ZipEntry(name))
                        BufferedInputStream(file.inputStream(), buffer.size).use { input ->
                            while (true) { val n = input.read(buffer); if (n < 0) break; zip.write(buffer, 0, n) }
                        }
                        zip.closeEntry()
                    }
                }
                if (!tmp.renameTo(target)) { tmp.copyTo(target, true); tmp.delete() }
                target
            } catch (_: Exception) { null }
        }
    }

    /** Stages a complete backup for a safe restore on the next fresh app process. The current
     * database is never replaced while Activities/managers may still have open SQLite handles. */
    fun stageFullRestore(uri: Uri): Boolean {
        return synchronized(BackupManager::class.java) {
            try {
                val stage = File(app.filesDir, RESTORE_STAGING)
                stage.deleteRecursively(); stage.mkdirs()
                val input = app.contentResolver.openInputStream(uri) ?: return false
                input.use { extractAndValidateFullBackup(it, stage) }
                settings.edit().putLong("pending_full_restore", System.currentTimeMillis()).commit()
                true
            } catch (_: Exception) {
                File(app.filesDir, RESTORE_STAGING).deleteRecursively()
                false
            }
        }
    }

    /** Called before StartupCoordinator initializes any managers/databases in a fresh process. */
    fun applyPendingFullRestore(): Boolean {
        synchronized(BackupManager::class.java) {
            val stage = File(app.filesDir, RESTORE_STAGING)
            if (!stage.isDirectory || !settings.contains("pending_full_restore")) return false
            val rollback = File(app.filesDir, "pre_restore_rollback")
            return try {
                rollback.deleteRecursively(); rollback.mkdirs()
                val currentQ = app.getDatabasePath("qbank.db")
                val currentF = app.getDatabasePath("flashcards.db")
                snapshotDatabase("qbank.db", File(rollback, "qbank.db"))
                snapshotDatabase("flashcards.db", File(rollback, "flashcards.db"))
                val livePrefs = File(app.applicationInfo.dataDir, "shared_prefs").apply { mkdirs() }
                val prefsRollback = File(rollback, "shared_prefs").apply { mkdirs() }
                livePrefs.listFiles()?.filter { it.isFile && it.name != "${PREFS}.xml" }?.forEach { it.copyTo(File(prefsRollback, it.name), true) }
                val filesRollback = File(rollback, "files").apply { mkdirs() }
                copyUserFiles(app.filesDir, filesRollback)

                replaceDatabaseFromStage(File(stage, "database/qbank.db"), currentQ)
                replaceDatabaseFromStage(File(stage, "database/flashcards.db"), currentF)
                val stagedPrefs = File(stage, "shared_prefs")
                livePrefs.listFiles()?.filter { it.isFile && it.name != "${PREFS}.xml" }?.forEach { it.delete() }
                stagedPrefs.listFiles()?.forEach { it.copyTo(File(livePrefs, it.name), true) }

                // A full backup is a snapshot, so stale durable files must not survive restore.
                clearUserFiles(app.filesDir)
                val stagedFiles = File(stage, "files")
                if (stagedFiles.isDirectory) copyTree(stagedFiles, app.filesDir)

                settings.edit().remove("pending_full_restore").commit()
                stage.deleteRecursively()
                rollback.deleteRecursively()
                true
            } catch (_: Exception) {
                runCatching { replaceDatabaseFromStage(File(rollback, "qbank.db"), app.getDatabasePath("qbank.db")) }
                runCatching { replaceDatabaseFromStage(File(rollback, "flashcards.db"), app.getDatabasePath("flashcards.db")) }
                runCatching {
                    val prefsRollback = File(rollback, "shared_prefs")
                    val livePrefs = File(app.applicationInfo.dataDir, "shared_prefs").apply { mkdirs() }
                    livePrefs.listFiles()?.filter { it.isFile && it.name != "${PREFS}.xml" }?.forEach { it.delete() }
                    prefsRollback.listFiles()?.forEach { it.copyTo(File(livePrefs, it.name), true) }
                }
                runCatching {
                    clearUserFiles(app.filesDir)
                    val filesRollback = File(rollback, "files")
                    if (filesRollback.isDirectory) copyTree(filesRollback, app.filesDir)
                }
                false
            }
        }
    }

    fun fullBackupFileName(): String = FULL_BACKUP_NAME

    fun backupNow(): Boolean {
        val file = writeLocalSnapshot() ?: return false
        val uri = treeUri() ?: return true
        return upload(file, uri)
    }

    fun exportFile(): File? = writeLocalSnapshot()

    private fun writeLocalSnapshot(): File? {
        return try {
            val dir = File(app.filesDir, AUTO_LOCAL_DIR).apply { mkdirs() }
            val target = File(dir, BACKUP_NAME)
            val tmp = File(dir, ".tmp_latest.qbackup")
            val json = buildPayload()
            FileOutputStream(tmp).use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, true); tmp.delete() }
            settings.edit().putLong(LAST_BACKUP, System.currentTimeMillis()).apply()
            publishAutomaticRovexBackup(target)
            target
        } catch (_: Exception) { null }
    }

    private fun buildPayload(): JSONObject {
        val progressStore = ProgressStore(app)
        val progressEntries = try { progressStore.allEntries() } finally { progressStore.close() }
        val ui = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val progress = JSONObject()
        for ((k,v) in progressEntries) when(v) {
            is String -> progress.put(k,v); is Boolean -> progress.put(k,v); is Int -> progress.put(k,v)
            is Long -> progress.put(k,v); is Float -> progress.put(k,v.toDouble())
            is Double -> progress.put(k,v)
        }
        val uiJson=JSONObject()
        for ((k,v) in ui.all) when(v) { is String->uiJson.put(k,v); is Boolean->uiJson.put(k,v); is Int->uiJson.put(k,v); is Long->uiJson.put(k,v); is Float->uiJson.put(k,v.toDouble()) }
        val notesJson=org.json.JSONArray()
        runCatching {
            val noteDb=QBankDb(app)
            try {
                noteDb.notes().forEach { (questionId,note) ->
                    notesJson.put(JSONObject().put("questionId",questionId).put("note",note))
                }
            } finally { noteDb.close() }
        }
        val flashSettings=JSONObject()
        runCatching {
            val fdb=FlashcardDb(app)
            try {
                val keys=arrayOf("new_per_day","max_reviews_per_day","max_cards_per_day","today_new_limit","today_review_limit","today_total_limit","today_override_date","learning_again_min","initial_interval_days","easy_multiplier","hard_multiplier","leech_threshold","desired_retention")
                keys.forEach{key->flashSettings.put(key,fdb.setting(key,""))}
            } finally { fdb.close() }
        }
        val core=JSONObject().put("format",3).put("createdAt",System.currentTimeMillis()).put("progress",progress).put("ui",uiJson).put("notes",notesJson).put("flashSettings",flashSettings)
        val digest=sha256(core.toString())
        return JSONObject().put("qBackup",core).put("sha256",digest)
    }

    fun restoreFromFile(file: File): Boolean {
        return runCatching { restorePayload(file.readText(Charsets.UTF_8)) }.getOrDefault(false)
    }

    fun restoreFromUri(uri: Uri): Boolean {
        return runCatching {
            val input = app.contentResolver.openInputStream(uri) ?: return false
            input.use { restorePayload(it.readBytes().toString(Charsets.UTF_8)) }
        }.getOrDefault(false)
    }

    /**
     * Restore is intentionally two-phase. We validate the complete payload and all note
     * references before changing anything. If the final commit fails, the previous state is
     * restored from an in-memory snapshot. This prevents a bad backup from leaving the app in
     * the dangerous half-restored state that can make derived screens appear broken.
     */
    private fun restorePayload(text: String): Boolean {
        val wrapper = JSONObject(text)
        val core = wrapper.optJSONObject("qBackup") ?: return false
        require(wrapper.optString("sha256").equals(sha256(core.toString()), ignoreCase = true)) { "Backup checksum failed" }
        val format = core.optInt("format", -1)
        require(format in 1..3) { "Unsupported backup version" }
        val progress = core.optJSONObject("progress") ?: JSONObject()
        val ui = core.optJSONObject("ui")
        val notes = core.optJSONArray("notes")
        val flashSettings = core.optJSONObject("flashSettings")

        val progressValues = jsonToMap(progress)
        val uiValues = if (ui == null) null else jsonToMap(ui)
        val noteValues = ArrayList<Pair<Long, String>>()
        if (notes != null) {
            for (i in 0 until notes.length()) {
                val n = notes.optJSONObject(i) ?: continue
                val id = n.optLong("questionId", 0L)
                val note = n.optString("note", "").trim()
                if (id > 0L && note.isNotBlank()) noteValues.add(id to note)
            }
        }

        // Validate note foreign keys against the CURRENT QBank before touching preferences.
        // A progress backup is allowed to be imported into a different QBank; stale notes are
        // simply discarded rather than making the entire restore fail.
        val db = QBankDb(app)
        val validNotes = try {
            noteValues.filter { runCatching { db.questionExists(it.first) }.getOrDefault(false) }
        } finally { db.close() }

        val progressStore = ProgressStore(app)
        val uiPrefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val oldProgress = progressStore.allEntries()
        val oldUi = uiPrefs.all.toMap()
        val oldNotes = runCatching {
            val noteDb = QBankDb(app)
            try { noteDb.notes() } finally { noteDb.close() }
        }.getOrDefault(emptyList())
        val oldFlashSettings = runCatching {
            FlashcardDb(app).let { fdb -> try { fdb.settingsSnapshot() } finally { fdb.close() } }
        }.getOrDefault(emptyMap())

        try {
            progressStore.restoreSilently(progressValues)
            if (uiValues != null) replacePrefs(uiPrefs, uiValues)
            val noteDb = QBankDb(app)
            try { noteDb.replaceNotes(validNotes) } finally { noteDb.close() }
            if (flashSettings != null) {
                val fdb=FlashcardDb(app)
                try {
                    val keys=flashSettings.keys()
                    while(keys.hasNext()){val key=keys.next();fdb.setSetting(key,flashSettings.optString(key,""))}
                } finally { fdb.close() }
            }

            // Notify every manager/screen only AFTER all stores agree on the new state.
            AppState.changed()
            writeLocalSnapshot()
            progressStore.close()
            return true
        } catch (t: Exception) {
            // Best-effort rollback. The original state is retained if rollback itself fails;
            // importantly, no partially imported state is reported as successful.
            runCatching { progressStore.restoreSilently(oldProgress) }
            runCatching { replacePrefs(uiPrefs, oldUi) }
            runCatching {
                val noteDb = QBankDb(app)
                try { noteDb.replaceNotes(oldNotes) } finally { noteDb.close() }
            }
            progressStore.close()
            runCatching {
                val fdb=FlashcardDb(app)
                try {
                    fdb.settingsSnapshot().keys.forEach { fdb.setSetting(it, oldFlashSettings[it] ?: "") }
                    oldFlashSettings.forEach { (key,value) -> fdb.setSetting(key,value) }
                } finally { fdb.close() }
            }
            AppState.changed()
            throw t
        }
    }

    private fun snapshotDatabase(name: String, destination: File) {
        val source = app.getDatabasePath(name)
        if (!source.exists()) {
            // Opening through the normal helper creates the schema when necessary.
            if (name == "qbank.db") QBankDb(app).close() else FlashcardDb(app).close()
        }
        val escaped = destination.absolutePath.replace("'", "''")
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(source.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { }
            db.rawQuery("VACUUM INTO '$escaped'", null).use { }
        } finally { db.close() }
        check(destination.isFile && destination.length() > 0L) { "Database snapshot failed: $name" }
    }

    private fun copyTree(source: File, destination: File) {
        if (!source.exists()) return
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { child -> copyTree(child, File(destination, child.name)) }
        } else {
            destination.parentFile?.mkdirs()
            source.copyTo(destination, true)
        }
    }

    private fun copyUserFiles(root: File, rollbackRoot: File) {
        root.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name in setOf(LOCAL_DIR, AUTO_LOCAL_DIR, RESTORE_STAGING, "pre_restore_rollback", "flashcard_imports")) return@forEach
            if (file.isFile && file.name.startsWith(".tmp_")) return@forEach
            copyTree(file, File(rollbackRoot, file.name))
        }
    }

    private fun clearUserFiles(root: File) {
        root.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name in setOf(LOCAL_DIR, AUTO_LOCAL_DIR, RESTORE_STAGING, "pre_restore_rollback", "flashcard_imports")) return@forEach
            if (file.isFile && file.name.startsWith(".tmp_")) { file.delete(); return@forEach }
            file.deleteRecursively()
        }
    }

    private fun collectUserFiles(root: File, prefix: String, out: MutableList<Pair<String, File>>) {
        root.listFiles()?.forEach { file ->
            val rel = "$prefix/${file.relativeTo(root).path.replace(File.separatorChar, '/') }"
            if (file.isDirectory) {
                if (file.name in setOf(LOCAL_DIR, AUTO_LOCAL_DIR, RESTORE_STAGING, "flashcard_imports")) return@forEach
                collectUserFiles(file, rel, out)
            } else if (file.isFile) {
                if (file.name.startsWith(".tmp_")) return@forEach
                out += rel to file
            }
        }
    }

    private fun putZipText(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractAndValidateFullBackup(input: java.io.InputStream, stage: File) {
        var manifestText: String? = null
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input, 1024 * 1024)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                BackupArchivePolicy.validateEntryCount(entryCount)
                BackupArchivePolicy.validateEntryPath(entry.name)
                val safe = File(stage, entry.name).canonicalFile
                require(safe.path.startsWith(stage.canonicalPath + File.separator)) { "Unsafe backup path" }
                if (entry.isDirectory) { safe.mkdirs(); zip.closeEntry(); continue }
                safe.parentFile?.mkdirs()
                var entryBytes = 0L
                FileOutputStream(safe).use { out ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        entryBytes += n
                        totalBytes += n
                        BackupArchivePolicy.validateEntrySize(entryBytes)
                        BackupArchivePolicy.validateTotalSize(totalBytes)
                        out.write(buffer, 0, n)
                    }
                }
                if (entry.name == "manifest.json") manifestText = safe.readText(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(manifestText ?: error("Missing backup manifest"))
        require(manifest.optInt("format", -1) == FULL_FORMAT) { "Unsupported full backup format" }
        require(manifest.optString("type") == "full") { "Not a full Rovex backup" }
        require(manifest.optString("package") == app.packageName) { "Backup belongs to another app" }
        val files = manifest.optJSONArray("files") ?: error("Missing backup file list")
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i); val path = item.getString("path")
            val file = File(stage, path).canonicalFile
            require(file.path.startsWith(stage.canonicalPath + File.separator) && file.isFile) { "Missing backup file: $path" }
            require(file.length() == item.optLong("size", -1L)) { "Backup size check failed: $path" }
            require(sha256File(file).equals(item.getString("sha256"), true)) { "Backup checksum failed: $path" }
        }
        require(File(stage, "database/qbank.db").isFile) { "QBank database missing" }
        require(File(stage, "database/flashcards.db").isFile) { "Flashcard database missing" }
    }

    private fun replaceDatabaseFromStage(staged: File, live: File) {
        if (!staged.isFile) return
        live.parentFile?.mkdirs()
        File(live.path + "-wal").delete()
        File(live.path + "-shm").delete()
        val tmp = File(live.path + ".restore_tmp")
        staged.copyTo(tmp, true)
        if (live.exists() && !live.delete()) throw IllegalStateException("Unable to replace ${live.name}")
        if (!tmp.renameTo(live)) { tmp.copyTo(live, true); tmp.delete() }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val v = json.opt(key)) {
                is Boolean -> out[key] = v
                is Int -> out[key] = v
                is Long -> out[key] = v
                is Double -> out[key] = v.toFloat()
                is String -> out[key] = v
                JSONObject.NULL -> Unit
            }
        }
        return out
    }

    private fun replacePrefs(prefs: android.content.SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (k, v) ->
            when (v) {
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
                is Long -> editor.putLong(k, v)
                is Float -> editor.putFloat(k, v)
                is Double -> editor.putFloat(k, v.toFloat())
                is String -> editor.putString(k, v)
            }
        }
        check(editor.commit()) { "Preference restore commit failed" }
    }

    /**
     * Automatic user-visible backup. Android 10+ uses MediaStore so Rovex can create
     * Download/Rovex/Backups without requesting broad storage permission. Older Android
     * versions use the app-specific Documents/Rovex/Backups directory, which also needs
     * no storage permission. A stable latest file is maintained and older timestamped
     * snapshots are capped to eight.
     */
    private fun publishAutomaticRovexBackup(local:File) {
        val now=System.currentTimeMillis()
        val last=PrefsCompat.long(settings,LAST_PUBLIC_BACKUP,0L)
        if(now-last < 15*60*1000L) return
        runCatching {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) publishToMediaStore(local)
            else publishToAppExternal(local)
            settings.edit().putLong(LAST_PUBLIC_BACKUP,now).apply()
        }
    }

    private fun publishToAppExternal(local:File) {
        val base=File(app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),AUTO_FOLDER).apply{mkdirs()}
        val latest=File(base,AUTO_LATEST_NAME)
        local.inputStream().use{input->FileOutputStream(latest).use{out->input.copyTo(out,1024*1024);out.fd.sync()}}
        val stamp=java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
        val history=File(base,"${AUTO_PREFIX}${stamp}.qbackup")
        local.copyTo(history,true)
        base.listFiles()?.filter{it.isFile && it.name.startsWith(AUTO_PREFIX) && it.name != AUTO_LATEST_NAME && it.name.endsWith(".qbackup")}?.sortedByDescending{it.lastModified()}?.drop(8)?.forEach{it.delete()}
    }

    private fun publishToMediaStore(local:File) {
        val resolver=app.contentResolver
        val collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val latestUri=resolver.query(collection,arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(AUTO_LATEST_NAME, "${Environment.DIRECTORY_DOWNLOADS}$AUTO_RELATIVE_PATH_SUFFIX"),null)?.use{c->if(c.moveToFirst())Uri.withAppendedPath(collection,c.getLong(0).toString()) else null}
        val uri=latestUri ?: resolver.insert(collection,ContentValues().apply{
            put(MediaStore.Downloads.DISPLAY_NAME,AUTO_LATEST_NAME)
            put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}$AUTO_RELATIVE_PATH_SUFFIX")
        }) ?: return
        resolver.openOutputStream(uri,"wt")?.use{out->local.inputStream().use{input->input.copyTo(out,1024*1024)}} ?: return
        val stamp=java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
        val histName="${AUTO_PREFIX}${stamp}.qbackup"
        resolver.insert(collection,ContentValues().apply{
            put(MediaStore.Downloads.DISPLAY_NAME,histName)
            put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}$AUTO_RELATIVE_PATH_SUFFIX")
        })?.let{histUri->
            val ok=runCatching{resolver.openOutputStream(histUri,"wt")?.use{out->local.inputStream().use{input->input.copyTo(out,1024*1024)}};true}.getOrDefault(false)
            if(!ok) runCatching{resolver.delete(histUri,null,null)}
        }
        // Keep at most eight historical Rovex backups in the public folder.
        val rows=resolver.query(collection,arrayOf(MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME,MediaStore.Downloads.DATE_ADDED),
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME}!=?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}$AUTO_RELATIVE_PATH_SUFFIX","${AUTO_PREFIX}%",AUTO_LATEST_NAME),"${MediaStore.Downloads.DATE_ADDED} DESC")
        rows?.use{c->{if(c.count>8){var i=0;while(c.moveToNext()){if(i++>=8){val id=c.getLong(0);resolver.delete(Uri.withAppendedPath(collection,id.toString()),null,null)}}}}}
    }

    private fun scheduleAutomaticLocal() {
        val request=PeriodicWorkRequestBuilder<LocalAutomaticBackupWorker>(12,TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag("rovex_automatic_backup").build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("rovex_automatic_backup",ExistingPeriodicWorkPolicy.UPDATE,request)
    }

    fun automaticBackupLocation():String = if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) "Downloads/$AUTO_FOLDER" else "App documents/$AUTO_FOLDER"

    fun uploadFullBackup(file: File): Boolean {
        val tree = treeUri() ?: return false
        return try {
            val root = DocumentFile.fromTreeUri(app, tree) ?: return false
            val target = root.findFile(FULL_BACKUP_NAME) ?: root.createFile("application/octet-stream", FULL_BACKUP_NAME) ?: return false
            app.contentResolver.openOutputStream(target.uri, "wt")?.use { out -> file.inputStream().use { input -> input.copyTo(out, 1024 * 1024) } } ?: return false
            true
        } catch (_: Exception) { false }
    }

    fun backupToUri(tree: Uri): Boolean { val local=writeLocalSnapshot() ?: return false; return upload(local,tree) }

    private fun upload(local:File, tree:Uri): Boolean {
        return try {
            val root=DocumentFile.fromTreeUri(app,tree) ?: return false
            // Keep one stable cloud backup. Re-create only when necessary; this avoids a new
            // timestamped file on every progress change while remaining compatible with SAF.
            val latest=root.findFile(BACKUP_NAME) ?: root.createFile("application/octet-stream",BACKUP_NAME) ?: return false
            val ok=app.contentResolver.openOutputStream(latest.uri)?.use{out->local.inputStream().use{input->input.copyTo(out,1024*1024)}} != null
            if(!ok)return false
            settings.edit().putLong(LAST_BACKUP,System.currentTimeMillis()).apply();true
        } catch(_:Exception){false}
    }

    private fun schedulePeriodic() {
        val request=PeriodicWorkRequestBuilder<CloudBackupWorker>(12,TimeUnit.HOURS)
            .setInputData(workDataOf("treeUri" to (treeUri()?.toString() ?: ""))).addTag("q_cloud_backup_periodic").build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("q_cloud_backup_periodic",ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    private fun sha256(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString(""){String.format("%02x",it)}

    object IntentFlags { const val READ_WRITE = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION }
}

class LocalAutomaticBackupWorker(appContext: Context, params: WorkerParameters): Worker(appContext,params) {
    override fun doWork(): Result {
        return if(runCatching{BackupManager(applicationContext).backupNow()}.getOrDefault(false)) Result.success() else Result.retry()
    }
}

class CloudBackupWorker(appContext: Context, params: WorkerParameters): Worker(appContext,params) {
    override fun doWork(): Result {
        val uri=inputData.getString("treeUri")?.let { Uri.parse(it) } ?: BackupManager(applicationContext).treeUri() ?: return Result.success()
        val ok=BackupManager(applicationContext).backupToUri(uri)
        return if(ok) Result.success() else Result.retry()
    }
}
