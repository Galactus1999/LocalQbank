package com.localqbank.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.io.BufferedInputStream
import java.util.zip.ZipFile
import java.util.regex.Pattern
import com.github.luben.zstd.ZstdInputStream

/**
 * Resumable, batch-transactional Anki .apkg importer.
 *
 * Design goals for phone-sized devices:
 * - never hold the complete deck/media set in RAM
 * - commit cards in small batches so one late failure cannot roll back 9,000 cards
 * - checkpoint after every batch so interrupted imports can resume
 * - tolerate individual bad cards/media files
 * - rewrite media references in one regex pass instead of N*M string scans
 */
object ApkgImporter {
    data class Result(val decks:Int,val cards:Int,val skipped:Int=0)
    class Control { @Volatile var cancelled=false }
    private class ImportCancelled: RuntimeException()
    private const val SEP = "\u001f"
    private const val DEFAULT_BATCH_SIZE = 200
    /** Hard cap for compressed and decompressed media manifests. */
    private const val MAX_MEDIA_MANIFEST_BYTES = 16L * 1024L * 1024L
    private const val STATE_NAME = "flashcard_import_state.json"
    private const val APKG_NAME = "flashcard_import_current.apkg"

    private fun importDir(context:Context)=File(context.filesDir,"flashcard_imports").apply{mkdirs()}
    private fun stateFile(context:Context)=File(importDir(context),STATE_NAME)
    private fun apkgFile(context:Context)=File(importDir(context),APKG_NAME)

    fun hasPending(context:Context):Boolean=stateFile(context).exists() && apkgFile(context).exists()

    fun pendingDescription(context:Context):String? = runCatching {
        val o=JSONObject(stateFile(context).readText())
        "${o.optInt("cardIndex",0)} / ${o.optInt("totalCards",0)} cards"
    }.getOrNull()

    /** Starts a fresh import and leaves a durable source copy for crash recovery. */
    fun importFile(context:Context, uri:android.net.Uri, progress:(String)->Unit = {}, control:Control=Control()):Result {
        val dir=importDir(context); val work=apkgFile(context); val state=stateFile(context)
        state.delete(); work.delete()
        val declaredSize=runCatching {
            context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        if (declaredSize > 0L) checkFreeSpaceForApkg(context, declaredSize)
        progress("Copying APKG… 0 MB")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open APKG" }
            FileOutputStream(work).use { out ->
                val buf = ByteArray(1024 * 1024)
                var n = 0L
                var next = 16L * 1024 * 1024
                while (true) {
                    checkCancelled(control)
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    n += r
                    if (n >= next) {
                        progress("Copying APKG… ${n / 1048576} MB")
                        next += 16L * 1024 * 1024
                    }
                }
                out.fd.sync()
            }
        }
        writeState(state,uri.toString(),uri.lastPathSegment ?: "Imported APKG",0,0,0,0)
        return runImport(context,work,state,progress,control)
    }

    /** Resumes the last interrupted import from its last committed card batch. */
    fun resume(context:Context, progress:(String)->Unit = {}, control:Control=Control()):Result {
        val work=apkgFile(context); val state=stateFile(context)
        require(work.exists() && state.exists()){"No interrupted flashcard import is available."}
        return runImport(context,work,state,progress,control)
    }

    fun discardPending(context:Context){ stateFile(context).delete(); apkgFile(context).delete() }

    private fun runImport(context:Context,apkg:File,state:File,progress:(String)->Unit,control:Control):Result {
        val saved=runCatching{JSONObject(state.readText())}.getOrDefault(JSONObject())
        val resumeCards=saved.optInt("cardIndex",0).coerceAtLeast(0)
        return try { importZip(context,apkg,state,saved,resumeCards,progress,control).also { state.delete(); apkg.delete() } }
        catch(e:ImportCancelled){ throw e }
    }

    private fun importZip(context:Context,apkg:File,state:File,saved:JSONObject,resumeCards:Int,progress:(String)->Unit,control:Control):Result {
        ZipFile(apkg).use{zip->
            val modern=zip.getEntry("collection.anki21b") != null
            val collection=zip.getEntry(if(modern) "collection.anki21b" else if(zip.getEntry("collection.anki21")!=null) "collection.anki21" else "collection.anki2")
                ?: throw IllegalArgumentException("This APKG has no supported Anki collection database")
            val dbFile=File(context.cacheDir,"anki_collection_${System.currentTimeMillis()}.anki2")
            try {
                zip.getInputStream(collection).use{raw->
                    val input=if(modern) ZstdInputStream(raw) else raw
                    input.use { decoded -> FileOutputStream(dbFile).use{out->decoded.copyTo(out,1024*1024)} }
                }
                val anki=SQLiteDatabase.openDatabase(dbFile.path,null,SQLiteDatabase.OPEN_READONLY)
                try {
                    val modelsRoot=if(modern) JSONObject() else JSONObject(readJsonColumn(anki,"SELECT models FROM col LIMIT 1") ?: "{}")
                    val deckNames=readDeckNames(anki)
                    val media=readMedia(zip,modern)
                    val mediaDir=File(context.filesDir,"flashcard_media").apply{mkdirs()}
                    val mediaResolver=MediaResolver(zip,media,mediaDir,progress,control,modern)
                    val totalCards=anki.rawQuery("SELECT COUNT(*) FROM cards",null).use{it.moveToFirst();it.getInt(0)}
                    val flash=FlashcardDb(context)
                    val sourceName=saved.optString("sourceName",apkg.name)
                    val isResume=resumeCards>0 || saved.optInt("totalCards",0)>0
                    if(!isResume) flash.deleteSource(sourceName)
                    var deckCount=0; var skipped=saved.optInt("skipped",0).coerceAtLeast(0); var imported=saved.optInt("imported",0).coerceAtLeast(0); var cursorIndex=0
                    var currentDid=Long.MIN_VALUE; var currentDeckId=0L; var currentPosition=0
                    var batch=0; var transactionOpen=false
                    fun currentBatchLimit(): Int = runCatching { if (AppManagers.isReady()) AppManagers.battery.recommendedImportBatchSize() else DEFAULT_BATCH_SIZE }.getOrDefault(DEFAULT_BATCH_SIZE).coerceIn(40, 240)
                    val modelCache=HashMap<Long,JSONObject>(); val fieldNameCache=HashMap<Long,List<String>>(); val templateCache=HashMap<Pair<Long,Int>,JSONObject>()
                    fun beginBatch(){ if(!transactionOpen){flash.beginImport();transactionOpen=true;batch=0} }
                    fun commitBatch(){ if(transactionOpen){flash.finishImport(true,false);transactionOpen=false;writeState(state,saved.optString("uri",""),sourceName,cursorIndex,totalCards,skipped,imported)} }
                    try {
                        progress("Importing cards… $imported/$totalCards")
                        val sql="SELECT c.id,c.did,c.ord,n.flds,n.tags,n.mid FROM cards c JOIN notes n ON n.id=c.nid ORDER BY c.did,c.id"
                        anki.rawQuery(sql,null).use{c->
                            while(c.moveToNext()){
                                checkCancelled(control)
                                val did=c.getLong(1); val ord=c.getInt(2)
                                if(cursorIndex++ < resumeCards){
                                    // Reconstruct deck/position cheaply for the first skipped row.
                                    if(did!=currentDid){
                                        currentDid=did
                                        val name=deckNames[did] ?: "Deck $did"
                                        currentDeckId=flash.upsertDeckForImport(name,sourceName)
                                        currentPosition=flash.cardCount(currentDeckId)
                                        deckCount++
                                    }
                                    continue
                                }
                                if(did!=currentDid){
                                    commitBatch(); currentDid=did; currentPosition=0
                                    val name=deckNames[did] ?: "Deck $did"
                                    currentDeckId=flash.upsertDeckForImport(name,sourceName)
                                    deckCount++
                                }
                                if (batch >= currentBatchLimit()) commitBatch()
                                beginBatch()
                                try {
                                    val fields=c.getString(3).split(SEP); val tags=c.getString(4); val mid=c.getLong(5)
                                    val deckName=deckNames[did] ?: "Deck $did"
                                    val model=modelCache.getOrPut(mid){if(modern) readModernModel(anki,mid) else modelsRoot.optJSONObject(mid.toString()) ?: JSONObject()}
                                    val fieldNames=fieldNameCache.getOrPut(mid){
                                        val a=model.optJSONArray("flds")
                                        buildList {
                                            if(a!=null) for(i in 0 until a.length()) {
                                                val item=a.opt(i)
                                                val name=when(item) {
                                                    is JSONObject -> item.optString("name", item.optString("label", "Field${i+1}"))
                                                    is String -> item.trim().ifBlank { "Field${i+1}" }
                                                    else -> item?.toString()?.trim().orEmpty().ifBlank { "Field${i+1}" }
                                                }
                                                add(name)
                                            }
                                        }
                                    }
                                    val names=HashMap<String,String>(fieldNames.size+5)
                                    fieldNames.forEachIndexed{i,fn->if(i<fields.size)names[fn]=fields[i]}
                                    // Common Anki template pseudo-fields. Keeping these in the
                                    // normal field map lets the generic renderer handle them
                                    // without special-case HTML generation later.
                                    names["Tags"]=tags
                                    names["Deck"]=deckName
                                    names["Subdeck"]=deckName.substringAfterLast("::",deckName)
                                    names["Card"]=(ord+1).toString()
                                    names["Type"]=model.optString("name", "Anki card")
                                    val tpl=templateCache.getOrPut(mid to ord){findTemplate(model.optJSONArray("tmpls") ?: org.json.JSONArray(),ord)}
                                    var front=renderTemplate(tpl.optString("qfmt","{{Front}}"),names,null,ord+1)
                                    var back=renderTemplate(tpl.optString("afmt","{{FrontSide}}<hr id=answer>{{Back}}"),names,front,ord+1)
                                    front=mediaResolver.rewrite(front); back=mediaResolver.rewrite(back)
                                    flash.insertCard(currentDeckId,front,back,tags,currentPosition++)
                                    imported++; batch++
                                } catch(_:Exception){ skipped++; batch++ }
                                if(batch>=currentBatchLimit()){
                                    flash.setDeckCount(currentDeckId,currentPosition); commitBatch()
                                    progress("Imported $imported/$totalCards • skipped $skipped")
                                }
                            }
                        }
                        flash.setDeckCount(currentDeckId,currentPosition); commitBatch()
                        progress("Imported $imported/$totalCards • skipped $skipped")
                        return Result(deckCount,imported,skipped)
                    } finally {
                        if(transactionOpen) flash.finishImport(false,false)
                        flash.close()
                    }
                } finally { anki.close() }
            } finally { dbFile.delete() }
        }
    }

    private fun checkCancelled(control:Control){if(control.cancelled)throw ImportCancelled()}
    private fun writeState(file:File,uri:String,sourceName:String,cardIndex:Int,totalCards:Int,skipped:Int,imported:Int=0){
        runCatching{file.writeText(JSONObject().put("uri",uri).put("sourceName",sourceName).put("cardIndex",cardIndex).put("totalCards",totalCards).put("skipped",skipped).put("imported",imported).put("updatedAt",System.currentTimeMillis()).toString())}
    }

    private fun readDeckNames(db:SQLiteDatabase):HashMap<Long,String>{
        val out=HashMap<Long,String>()
        // Modern schema (Anki 2.1.50+): decks are normalized into their own table.
        runCatching { db.rawQuery("SELECT id,name FROM decks",null).use { c -> while(c.moveToNext()) out[c.getLong(0)] = c.getString(1) } }.getOrNull()
        if(out.isNotEmpty()) return out
        // Legacy schema: deck definitions live in col.decks JSON.
        val raw=readJsonColumn(db,"SELECT decks FROM col LIMIT 1") ?: return out
        runCatching{val o=JSONObject(raw);for(k in o.keys()){val d=o.optJSONObject(k);if(d!=null)out[k.toLongOrNull() ?: 0L]=d.optString("name","Deck $k")}}
        return out
    }
    private fun readJsonColumn(db:SQLiteDatabase,sql:String):String?=try{db.rawQuery(sql,null).use{c->if(c.moveToFirst())c.getString(0)else null}}catch(_:Exception){null}
    private fun findTemplate(a:org.json.JSONArray,ord:Int):JSONObject { for(i in 0 until a.length()){val t=a.optJSONObject(i) ?: continue;if(t.optInt("ord",i)==ord)return t};return a.optJSONObject(0) ?: JSONObject() }

    /** Render common Anki template syntax without assuming every field is JSON. */
    private fun renderTemplate(template:String,fields:Map<String,String>,front:String?,cloze:Int):String {
        var s=template
        val cond=Pattern.compile("\\{\\{([#^])([^}]+)\\}\\}(.*?)\\{\\{/\\2\\}\\}",Pattern.DOTALL).matcher(s)
        val sb=StringBuffer()
        while(cond.find()){
            val value=fields[cond.group(2).trim()].orEmpty()
            val keep=if(cond.group(1)=="#") value.isNotBlank() else value.isBlank()
            cond.appendReplacement(sb,java.util.regex.Matcher.quoteReplacement(if(keep) cond.group(3) else ""))
        }
        cond.appendTail(sb); s=sb.toString()
        s=s.replace("{{FrontSide}}",front ?: "")
        s=Regex("\\{\\{cloze:([^}]+)\\}\\}").replace(s){m->clozeField(fields[m.groupValues[1].trim()].orEmpty(),cloze,front!=null)}
        s=Regex("\\{\\{(?:text:)?([^}:]+)\\}\\}").replace(s){m->fields[m.groupValues[1].trim()].orEmpty()}
        return s.replace(Regex("\\{\\{[^}]+\\}\\}"),"")
    }

    private fun clozeField(value:String,n:Int,reveal:Boolean):String {
        val re=Regex("\\{\\{c(\\d+)::(.*?)(?:::(.*?))?\\}\\}")
        return re.replace(value){m->
            if(m.groupValues[1].toIntOrNull()==n){
                if(reveal) "<b><mark>${m.groupValues[2]}</mark></b>"
                else "<b>[${m.groupValues[3].ifBlank { "…" }}]</b>"
            } else m.groupValues[2]
        }
    }

    /** Build the same small model representation used by the legacy importer from Anki's
     * normalized schema (notetypes/fields/templates). The config columns are protobufs;
     * only the q/a format and notetype kind are needed for rendering. */
    private fun readModernModel(db:SQLiteDatabase,mid:Long):JSONObject {
        val model=JSONObject()
        db.rawQuery("SELECT name,config FROM notetypes WHERE id=? LIMIT 1",arrayOf(mid.toString())).use { c ->
            if(!c.moveToFirst()) return model
            model.put("name",c.getString(0))
            val config=if(c.isNull(1)) ByteArray(0) else c.getBlob(1)
            model.put("type", ProtoLite.fieldVarint(config,1,0)) // Notetype.Config.kind
        }
        val fields=org.json.JSONArray()
        db.rawQuery("SELECT ord,name FROM fields WHERE ntid=? ORDER BY ord",arrayOf(mid.toString())).use { c ->
            while(c.moveToNext()) fields.put(JSONObject().put("ord",c.getInt(0)).put("name",c.getString(1)))
        }
        model.put("flds",fields)
        val tmpls=org.json.JSONArray()
        db.rawQuery("SELECT ord,name,config FROM templates WHERE ntid=? ORDER BY ord",arrayOf(mid.toString())).use { c ->
            while(c.moveToNext()) {
                val cfg=if(c.isNull(2)) ByteArray(0) else c.getBlob(2)
                tmpls.put(JSONObject().put("ord",c.getInt(0)).put("name",c.getString(1))
                    .put("qfmt",ProtoLite.fieldString(cfg,1,"")).put("afmt",ProtoLite.fieldString(cfg,2,"")))
            }
        }
        model.put("tmpls",tmpls)
        return model
    }

    private fun readBytesLimited(input:java.io.InputStream, maxBytes:Long):ByteArray {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        val out=ByteArrayOutputStream(minOf(maxBytes, 1024L * 1024L).toInt())
        val buf=ByteArray(64 * 1024)
        var total=0L
        while(true){
            val n=input.read(buf)
            if(n < 0) break
            total += n
            if(total > maxBytes) throw IllegalArgumentException("Media manifest exceeds ${maxBytes} bytes")
            out.write(buf,0,n)
        }
        return out.toByteArray()
    }

    private fun readUtf8Limited(input:java.io.InputStream, maxBytes:Long):String =
        readBytesLimited(input,maxBytes).toString(StandardCharsets.UTF_8)

    private fun readMedia(zip:ZipFile,modern:Boolean):Map<String,String>{
        val e=zip.getEntry("media") ?: return emptyMap()
        if(!modern){
            val text=zip.getInputStream(e).use { readUtf8Limited(it, MAX_MEDIA_MANIFEST_BYTES) }
            val o=JSONObject(text)
            return buildMap{for(k in o.keys()){val name=o.optString(k);if(name.isNotBlank())put(name,k)}}
        }
        // Modern Anki stores MediaEntries as protobuf. Entry order maps to the numeric ZIP
        // member (0,1,2...), while legacy_zip_filename is retained when present in older maps.
        val rawBytes=zip.getInputStream(e).use { readBytesLimited(it, MAX_MEDIA_MANIFEST_BYTES) }
        val bytes=if(isZstd(rawBytes)) {
            ZstdInputStream(ByteArrayInputStream(rawBytes)).use { readBytesLimited(it, MAX_MEDIA_MANIFEST_BYTES) }
        } else rawBytes
        val out=LinkedHashMap<String,String>()
        var index=0
        ProtoLite.repeatedMessages(bytes,1).forEach { msg ->
            val name=ProtoLite.fieldString(msg,1,"")
            if(name.isNotBlank()){
                val legacy=ProtoLite.fieldVarint(msg,255,-1L)
                out[name]=if(legacy>=0) legacy.toString() else index.toString()
            }
            index++
        }
        return out
    }

    private object ProtoLite {
        private data class Field(val no:Int,val wire:Int,val value:Any)
        private fun fields(bytes:ByteArray):List<Field>{
            val out=ArrayList<Field>();var p=0
            while(p<bytes.size){
                val tag=readVarint(bytes,p).also{p=it.second}; if(tag.first==0L) break
                val no=(tag.first ushr 3).toInt();val wire=(tag.first and 7L).toInt()
                when(wire){
                    0->{val v=readVarint(bytes,p);p=v.second;out+=Field(no,wire,v.first)}
                    1->{p+=8}
                    2->{val len=readVarint(bytes,p);p=len.second;val n=len.first.toInt().coerceAtLeast(0);if(p+n>bytes.size) break;out+=Field(no,wire,bytes.copyOfRange(p,p+n));p+=n}
                    5->{p+=4}
                    else->break
                }
            };return out
        }
        private fun readVarint(b:ByteArray,start:Int):Pair<Long,Int>{var p=start;var shift=0;var out=0L;while(p<b.size&&shift<64){val x=b[p++].toInt() and 0xff;out=out or ((x and 0x7f).toLong() shl shift);if((x and 0x80)==0)return out to p;shift+=7};return 0L to b.size}
        fun fieldVarint(b:ByteArray,no:Int,default:Long):Long=fields(b).firstOrNull{it.no==no&&it.wire==0}?.value as? Long ?: default
        fun fieldString(b:ByteArray,no:Int,default:String):String=(fields(b).firstOrNull{it.no==no&&it.wire==2}?.value as? ByteArray)?.toString(Charsets.UTF_8) ?: default
        fun repeatedMessages(b:ByteArray,no:Int): List<ByteArray> =fields(b).filter{it.no==no&&it.wire==2}.mapNotNull{it.value as? ByteArray}
    }

    /**
     * Resolves only media actually referenced by imported cards. This is important for
     * multi-gigabyte APKGs: Anki packages can contain large media sets, and eagerly
     * extracting every member would temporarily require several times the package size.
     */
    private class MediaResolver(
        private val zip: ZipFile,
        private val manifest: Map<String,String>,
        private val dir: File,
        private val progress: (String)->Unit,
        private val control: Control,
        private val modern: Boolean
    ) {
        private val resolved=HashMap<String,String>(minOf(manifest.size*2, 8192))
        private val reverse=HashMap<String,String>(minOf(manifest.size*2, 8192)).apply {
            manifest.forEach { (name,zipKey) -> put(name,zipKey); put(name.substringAfterLast('/'),zipKey) }
        }
        private var extracted=0

        fun rewrite(html:String):String {
            if(html.isEmpty() || manifest.isEmpty()) return html
            val srcRx=Pattern.compile("""(?i)(src|href|poster)\s*=\s*([\"'])([^\"']+)\2""")
            val m=srcRx.matcher(html); val sb=StringBuffer()
            while(m.find()) {
                val reference=m.group(3) ?: continue
                val replacement=resolveReference(reference)
                val whole=m.group() ?: continue
                val attr=m.group(1) ?: continue
                val quote=m.group(2) ?: continue
                if(replacement==null) m.appendReplacement(sb,java.util.regex.Matcher.quoteReplacement(whole))
                else m.appendReplacement(sb,java.util.regex.Matcher.quoteReplacement(attr+"="+quote+replacement+quote))
            }
            m.appendTail(sb)
            val css=Pattern.compile("""(?i)url\(\s*([\"']?)([^\"')]+)\1\s*\)""")
            val c=css.matcher(sb.toString()); val out=StringBuffer()
            while(c.find()) {
                val reference=c.group(2) ?: continue
                val replacement=resolveReference(reference)
                val whole=c.group() ?: continue
                val quote=c.group(1) ?: ""
                if(replacement==null) c.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(whole))
                else c.appendReplacement(out,java.util.regex.Matcher.quoteReplacement("url("+quote+replacement+quote+")"))
            }
            c.appendTail(out)
            return out.toString()
        }

        private fun resolveReference(raw:String):String? {
            val ref=android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                .replace("\\/","/").trim()
            if(ref.isBlank() || ref.startsWith("http://",true) || ref.startsWith("https://",true) || ref.startsWith("data:",true) || ref.startsWith("file:",true) || ref.startsWith("content:",true) || ref.startsWith("mailto:",true) || ref.startsWith("#")) return null
            val decoded=runCatching{java.net.URLDecoder.decode(ref,Charsets.UTF_8.name())}.getOrDefault(ref)
            val key=reverse[decoded] ?: reverse[ref] ?: reverse[decoded.substringAfterLast('/')] ?: return null
            val originalName=decoded.substringAfterLast('/').let { leaf -> manifest.keys.firstOrNull { it==decoded || it.substringAfterLast('/')==leaf } ?: decoded }
            return resolved[key] ?: extract(key,originalName)?.also { resolved[key]=it }
        }

        @Synchronized private fun extract(key:String,name:String):String? {
            ApkgImporter.checkCancelled(control)
            if(name.isBlank()) return null
            val safeName=name.substringAfterLast('/').replace("..","_").ifBlank { "media_$key" }
            val safe=File(dir,safeName)
            if(!safe.exists()) {
                val entry=zip.getEntry(key) ?: zip.getEntry(name) ?: return null
                runCatching {
                    zip.getInputStream(entry).use { raw ->
                        val buffered=BufferedInputStream(raw,64*1024)
                        buffered.mark(4)
                        val head=ByteArray(4)
                        val n=buffered.read(head)
                        buffered.reset()
                        val compressed=modern && n==4 && isZstd(head)
                        val input=if(compressed) ZstdInputStream(buffered) else buffered
                        input.use { decoded -> FileOutputStream(safe).use { output -> decoded.copyTo(output,512*1024) } }
                    }
                }.onFailure { safe.delete() }.getOrNull() ?: return null
            }
            extracted++
            if(extracted%25==0) progress("Preparing referenced media… $extracted")
            return "file://${safe.absolutePath}"
        }
    }

    private fun isZstd(bytes:ByteArray):Boolean = bytes.size>=4 &&
        (bytes[0].toInt() and 0xff)==0x28 && (bytes[1].toInt() and 0xff)==0xB5 &&
        (bytes[2].toInt() and 0xff)==0x2F && (bytes[3].toInt() and 0xff)==0xFD

    private fun checkFreeSpaceForApkg(context:Context,bytes:Long) {
        val stat=android.os.StatFs(context.filesDir.absolutePath)
        val free=stat.availableBlocksLong*stat.blockSizeLong
        // The source package itself must fit. Referenced media is extracted lazily, so do not
        // reserve another full package-sized block of space here.
        require(free >= bytes + 64L*1024L*1024L) {
            "Not enough free storage for this APKG. Need at least ${((bytes+64L*1024L*1024L)/1048576L)} MB free."
        }
    }

}


