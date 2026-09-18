package com.localqbank.library

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Knowledge Vault notes surface. Notes are grouped by subject, searchable, and split into
 * machine-derived key points vs the user's own note so long imported explanations stay readable.
 */
class NotesActivity : Activity() {
    private lateinit var db: QBankDb
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var filter = "all"
    private var selectionMode = false
    private val selectedIds = linkedSetOf<Long>()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        SystemUi.immersive(this)
        db=QBankDb(this)
        val root=build()
        setContentView(root)
        AdaptiveTypographyManager.apply(root)
        render()
    }

    private fun build():ScrollView {
        val scroll=ScrollView(this).apply{background=ThemeManager.backgroundDrawable(this@NotesActivity);isFillViewport=true}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(28))}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val back=TextView(this).apply{text="←";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@NotesActivity));background=rounded(ThemeManager.elevated(this@NotesActivity),16f);setOnClickListener{finish()}}
        bar.addView(back,LinearLayout.LayoutParams(dp(52),dp(48)))
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)}
        title.addView(RovexWaveTextView(this@NotesActivity).apply{text="My Notes";textSize=24f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@NotesActivity))})
        title.addView(TextView(this@NotesActivity).apply{text="Organized knowledge, not a wall of text";textSize=11.5f;setTextColor(ThemeManager.muted(this@NotesActivity));setPadding(0,dp(2),0,0)})
        bar.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        val select=TextView(this).apply{
            text="SELECT";textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setPadding(dp(10),0,dp(10),0);
            background=rounded(ThemeManager.elevated(this@NotesActivity),12f);setTextColor(ThemeManager.text(this@NotesActivity));
            setOnClickListener{selectionMode=!selectionMode;if(!selectionMode)selectedIds.clear();render()}
        }
        bar.addView(select,LinearLayout.LayoutParams(dp(72),dp(40)))
        root.addView(bar)

        search=EditText(this).apply{
            hint="Search notes, subjects, questions…";textSize=14f;isSingleLine=true;setTextColor(ThemeManager.text(this@NotesActivity));setHintTextColor(ThemeManager.muted(this@NotesActivity));setPadding(dp(14),0,dp(14),0);background=rounded(ThemeManager.elevated(this@NotesActivity),16f)
            addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){render()};override fun afterTextChanged(s:Editable?) {}})
        }
        root.addView(search,LinearLayout.LayoutParams(-1,dp(48)).apply{setMargins(0,dp(14),0,dp(9))})

        val filters=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;weightSum=4f}
        listOf("all" to "All","recent" to "Recent","key" to "Key points","mine" to "My notes").forEach{(key,label)->
            val chip=TextView(this).apply{text=label;textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setPadding(dp(5),0,dp(5),0);isClickable=true;isFocusable=true;setOnClickListener{filter=key;render()};background=rounded(if(filter==key)ThemeManager.accent(this@NotesActivity) else ThemeManager.elevated(this@NotesActivity),12f);setTextColor(if(filter==key)Color.WHITE else ThemeManager.text(this@NotesActivity))}
            filters.addView(chip,LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(dp(2),0,dp(2),0)})
        }
        root.addView(filters)
        val selectionBar=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(8),dp(4),0);visibility=if(selectionMode)View.VISIBLE else View.GONE
        }
        selectionBar.addView(TextView(this@NotesActivity).apply{text="${selectedIds.size} selected";textSize=12.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@NotesActivity))},LinearLayout.LayoutParams(0,dp(42),1f))
        selectionBar.addView(TextView(this@NotesActivity).apply{text="SELECT ALL";textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(this@NotesActivity));setPadding(dp(8),0,dp(8),0);setOnClickListener{val ids=db.noteRecords().map{it.questionId};if(ids.all{it in selectedIds})selectedIds.clear() else selectedIds.addAll(ids);render()}},LinearLayout.LayoutParams(dp(86),dp(42)))
        selectionBar.addView(TextView(this@NotesActivity).apply{text="DELETE";textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.wrongText(this@NotesActivity));background=rounded(ThemeManager.wrongBg(this@NotesActivity),12f);setPadding(dp(8),0,dp(8),0);setOnClickListener{deleteSelected()}},LinearLayout.LayoutParams(dp(78),dp(42)).apply{setMargins(dp(6),0,0,0)})
        root.addView(selectionBar)
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(list,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(10),0,0)})
        scroll.addView(root);return scroll
    }

    private fun render(){
        if(!::list.isInitialized) return
        list.removeAllViews()
        val query=search.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val now=System.currentTimeMillis()
        val week=now-7L*86_400_000L
        val records=runCatching{db.noteRecords()}.getOrDefault(emptyList()).filter{r->
            val match=query.isBlank() || r.note.lowercase().contains(query) || r.subject.lowercase().contains(query) || r.testTitle.lowercase().contains(query) || r.sourceName.lowercase().contains(query)
            val time=filter!="recent" || r.updatedAt>=week
            val isAuto=r.note.contains("[Rovex Auto Summary]",true) || r.note.contains("[LocalQBank Auto Summary]",true)
            val key=filter!="key" || isAuto
            val mine=filter!="mine" || !isAuto
            match && time && key && mine
        }
        if(records.isEmpty()){
            list.addView(TextView(this).apply{text=if(query.isBlank())"No notes saved yet." else "No notes match your search.";textSize=15f;setTextColor(ThemeManager.muted(this@NotesActivity));gravity=Gravity.CENTER;setPadding(dp(12),dp(36),dp(12),dp(36))});return
        }
        val groups=records.sortedByDescending{it.updatedAt}.groupBy{it.subject.ifBlank{"General"}}
        groups.entries.sortedByDescending{entry -> entry.value.maxOfOrNull{it.updatedAt} ?: 0L}.forEach{(subject,items)->
            val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(12),dp(4),dp(7))}
            header.addView(TextView(this@NotesActivity).apply{text=subject.uppercase(Locale.getDefault());textSize=11f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.muted(this@NotesActivity));letterSpacing=.08f},LinearLayout.LayoutParams(0,-2,1f))
            header.addView(TextView(this@NotesActivity).apply{text="${items.size} note${if(items.size==1)"" else "s"}";textSize=10.5f;setTextColor(ThemeManager.muted(this@NotesActivity))})
            list.addView(header)
            items.forEach{addNoteCard(it)}
        }
    }

    private fun addNoteCard(r:QBankDb.NoteRecord){
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(13),dp(15),dp(12));
            background=rounded(if(selectedIds.contains(r.questionId)) ThemeManager.peacockFill(this@NotesActivity) else if(ThemeManager.isDark(this@NotesActivity))ThemeManager.elevated(this@NotesActivity) else Color.WHITE,18f)
            if(selectionMode) setOnClickListener{if(!selectedIds.add(r.questionId))selectedIds.remove(r.questionId);render()}
        }
        // Keep provenance metadata out of the normal reading surface. It remains
        // available through the existing View Question action, while the note itself
        // stays focused on the user's saved content and generated key points.

        val parsed=parseNote(r.note)
        if(parsed.keyPoints.isNotEmpty()){
            val keyBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(11),dp(9),dp(11),dp(9));background=rounded(ThemeManager.peacockFill(this@NotesActivity),13f);setMargins(card,0,dp(10),0,0)}
            keyBox.addView(TextView(this@NotesActivity).apply{text="KEY POINTS";textSize=10f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.peacockText(this@NotesActivity));letterSpacing=.06f})
            parsed.keyPoints.take(6).forEach{point->keyBox.addView(TextView(this@NotesActivity).apply{text="• $point";textSize=13.5f;setTextColor(ThemeManager.text(this@NotesActivity));setLineSpacing(0f,1.15f);setPadding(0,dp(4),0,0)})}
            card.addView(keyBox)
        }
        val attachedImages = parsedAttachedImages(r.imagePaths)
        if(attachedImages.isNotEmpty()){
            card.addView(TextView(this).apply{text="SAVED IMAGE";textSize=10f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.muted(this@NotesActivity));letterSpacing=.06f;setPadding(0,dp(11),0,dp(5))})
            attachedImages.forEach { path ->
                val iv=ImageView(this@NotesActivity).apply{
                    adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(0,dp(3),0,dp(3));setImageBitmap(decodeNoteImage(path));contentDescription="Saved image with note"
                }
                card.addView(iv,LinearLayout.LayoutParams(-1,dp(240)).apply{setMargins(0,0,0,dp(5))})
            }
        }
        val personalText = parsed.personal.replace("[Image note]", "").trim()
        if(personalText.isNotBlank()){
            card.addView(TextView(this).apply{text="MY NOTE";textSize=10f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.muted(this@NotesActivity));letterSpacing=.06f;setPadding(0,dp(11),0,dp(3))})
            card.addView(TextView(this).apply{text=personalText;textSize=15f;setTextColor(ThemeManager.text(this@NotesActivity));setLineSpacing(0f,1.18f)})
        }
        // Keep destructive/navigation controls hidden during normal reading. A long
        // press exposes a compact action sheet, matching the user's requested Notes UX.
        card.setOnLongClickListener {
            if (selectionMode) {
                if (!selectedIds.add(r.questionId)) selectedIds.remove(r.questionId)
                render()
            } else showNoteActions(r)
            true
        }
        if (selectionMode) {
            card.setOnClickListener { if (!selectedIds.add(r.questionId)) selectedIds.remove(r.questionId); render() }
        }
        list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(9))})
    }

    private fun showNoteActions(r: QBankDb.NoteRecord) {
        val dialog = android.app.Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = rounded(ThemeManager.dialogBg(this@NotesActivity), 22f)
        }
        box.addView(RovexWaveTextView(this).apply {
            text = "Note actions"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(this@NotesActivity))
        })
        box.addView(TextView(this).apply {
            text = "Choose an action for this note"
            textSize = 12f
            setTextColor(ThemeManager.muted(this@NotesActivity))
            setPadding(0, dp(3), 0, dp(12))
        })
        fun action(label: String, fill: Int, textColor: Int, click: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            background = rounded(fill, 14f)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { click(); dialog.dismiss() }
        }
        box.addView(action("VIEW QUESTION", ThemeManager.accent(this), Color.WHITE) {
            val q = runCatching { db.questionById(r.questionId) }.getOrNull()
            if (q != null && r.testId.isNotBlank()) {
                startActivity(Intent(this, QuizActivity::class.java)
                    .putExtra("testId", r.testId)
                    .putExtra("questionId", r.questionId)
                    .putExtra("position", r.position)
                    .putExtra("title", "My Notes"))
            } else Toast.makeText(this, "Question no longer exists", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(44)))
        box.addView(action("DELETE NOTE", ThemeManager.wrongBg(this), ThemeManager.wrongText(this)) {
            confirmDeleteNote(r)
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(8), 0, 0) })
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(.32f)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .88f).toInt(), -2)
    }

    private fun confirmDeleteNote(r: QBankDb.NoteRecord) {
        val dialog = android.app.Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = rounded(ThemeManager.dialogBg(this@NotesActivity), 22f)
        }
        box.addView(RovexWaveTextView(this).apply { text = "Delete note?"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(this@NotesActivity)) })
        box.addView(TextView(this).apply { text = "Only the saved note will be removed. The original question remains safe."; textSize = 13f; setTextColor(ThemeManager.muted(this@NotesActivity)); setPadding(0, dp(6), 0, dp(14)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val cancel = TextView(this).apply { text = "CANCEL"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(ThemeManager.text(this@NotesActivity)); background = rounded(ThemeManager.elevated(this@NotesActivity), 13f) }
        val delete = TextView(this).apply { text = "DELETE"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(ThemeManager.wrongText(this@NotesActivity)); background = rounded(ThemeManager.wrongBg(this@NotesActivity), 13f); setOnClickListener { db.deleteNote(r.questionId); dialog.dismiss(); render() } }
        row.addView(cancel, LinearLayout.LayoutParams(0, dp(44), 1f)); row.addView(delete, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(8), 0, 0, 0) })
        cancel.setOnClickListener { dialog.dismiss() }
        box.addView(row); dialog.setContentView(box); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * .88f).toInt(), -2)
    }

    private fun deleteSelected(){
        if(selectedIds.isEmpty()){Toast.makeText(this,"Select at least one note",Toast.LENGTH_SHORT).show();return}
        val count=selectedIds.size
        AlertDialog.Builder(this).setTitle("Delete $count notes?").setMessage("Only the selected saved notes will be removed. Original questions remain safe.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->db.deleteNotes(selectedIds.toLongArray());selectedIds.clear();selectionMode=false;render()}.show()
    }

    private data class ParsedNote(val keyPoints:List<String>,val personal:String)
    private fun parseNote(raw:String):ParsedNote{
        val marker = when {
            raw.contains("[Rovex Auto Summary]", true) -> "[Rovex Auto Summary]"
            raw.contains("[LocalQBank Auto Summary]", true) -> "[LocalQBank Auto Summary]"
            else -> return ParsedNote(emptyList(), raw.trim())
        }
        val split=raw.split(marker,limit=2)
        if(split.size<2) return ParsedNote(emptyList(),raw.trim())
        val personal=split[0].trim().removeSuffix("—").trim()
        val auto=split[1].trim()
        val points=auto.lines().map{it.trim()}.filter{it.isNotBlank() && !it.equals("Summary",true)}
            .map{it.removePrefix("-").removePrefix("•").trim()}.filter{it.length>2}
        return ParsedNote(points,personal)
    }


    private fun parsedAttachedImages(paths: List<String>): List<String> = paths.filter { it.isNotBlank() && java.io.File(it).exists() }.take(6)

    private fun decodeNoteImage(path:String): android.graphics.Bitmap? = runCatching {
        val opts=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
        android.graphics.BitmapFactory.decodeFile(path,opts)
        if(opts.outWidth<=0 || opts.outHeight<=0) return@runCatching null
        var sample=1
        val maxW=(resources.displayMetrics.widthPixels*resources.displayMetrics.density*0.84f).toInt().coerceAtLeast(720)
        while(opts.outWidth/sample>maxW || opts.outHeight/sample>900) sample*=2
        val decode=android.graphics.BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=android.graphics.Bitmap.Config.RGB_565}
        android.graphics.BitmapFactory.decodeFile(path,decode)
    }.getOrNull()

    private fun rounded(fill:Int,r:Float)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r.toInt()).toFloat()}
    private fun setMargins(v:View,l:Int,t:Int,r:Int,b:Int){v.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(l,t,r,b)}}
    override fun onResume(){super.onResume();if(::list.isInitialized)render()}
    override fun onDestroy(){if(::db.isInitialized)db.close();super.onDestroy()}
}
