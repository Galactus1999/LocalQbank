package com.localqbank.library

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File

/** Scalable local knowledge library: Notes, Tables and Images are separated and searchable. */
class KnowledgeVaultActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var filter = "ALL"
    private val dp get() = resources.displayMetrics.density
    private fun d(v:Int)=(v*dp).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        AppManagers.initialize(applicationContext)
        SystemUi.immersive(this)
        setContentView(build())
        TransitionCoordinator.install(this)
        AdaptiveLayoutManager.install(this, findViewById(android.R.id.content), topExtraDp=8, bottomExtraDp=4)
        render()
    }

    private fun build(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background=ThemeManager.backgroundDrawable(this@KnowledgeVaultActivity); setPadding(d(14),d(10),d(14),d(8)) }
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        bar.addView(TextView(this).apply{text="‹";textSize=30f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@KnowledgeVaultActivity));background=rounded(ThemeManager.elevated(this@KnowledgeVaultActivity),14f);setOnClickListener{finish()}},LinearLayout.LayoutParams(d(44),d(44)))
        bar.addView(TextView(this).apply{text="Knowledge Vault";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@KnowledgeVaultActivity));setPadding(d(12),0,0,0)},LinearLayout.LayoutParams(0,d(44),1f))
        root.addView(bar)
        root.addView(TextView(this).apply{text="A searchable library of generated notes, saved tables and learning images. Original QBank content remains unchanged.";textSize=12.5f;setTextColor(ThemeManager.muted(this@KnowledgeVaultActivity));setPadding(d(4),d(10),d(4),d(10))})
        search=EditText(this).apply{hint="Search subject, question or content";textSize=14f;setSingleLine(true);setTextColor(ThemeManager.text(this@KnowledgeVaultActivity));setHintTextColor(ThemeManager.muted(this@KnowledgeVaultActivity));background=rounded(ThemeManager.elevated(this@KnowledgeVaultActivity),14f);setPadding(d(14),0,d(14),0);addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){render()};override fun afterTextChanged(e:android.text.Editable?){} })}
        root.addView(search,LinearLayout.LayoutParams(-1,d(48)).apply{setMargins(0,0,0,d(8))})
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        listOf("ALL","NOTES","TABLES","IMAGES").forEach{f->tabs.addView(TextView(this).apply{text=f;textSize=11f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(if(filter==f)Color.WHITE else ThemeManager.text(this@KnowledgeVaultActivity));background=rounded(if(filter==f)ThemeManager.accent(this@KnowledgeVaultActivity) else ThemeManager.elevated(this@KnowledgeVaultActivity),12f);setOnClickListener{filter=f;render()}},LinearLayout.LayoutParams(0,d(38),1f).apply{setMargins(d(2),0,d(2),0)})}
        root.addView(tabs)
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,d(8),0,d(20))}
        root.addView(ScrollView(this).apply{isFillViewport=true;addView(list)},LinearLayout.LayoutParams(-1,0,1f))
        return root
    }

    private data class Artifact(val type:String,val file:File,val qid:Long,val title:String,val subject:String)
    private fun artifacts():List<Artifact>{
        val root=File(filesDir,"knowledge"); val qdb=QBankDb(this); val out=mutableListOf<Artifact>()
        fun add(type:String,dir:String){File(root,dir).listFiles()?.filter{it.isFile}?.forEach{f->
            val id=Regex("(?:q_|explanation_)(\\d+)").find(f.name)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val ctx=if(id>0)runCatching{qdb.questionContext(id)}.getOrDefault("Unlinked" to "Question $id") else "Unlinked" to "Saved artifact"
            out.add(Artifact(type,f,id,ctx.second,ctx.first))
        }}
        try{add("NOTES","notes");add("TABLES","tables");add("IMAGES","images")}finally{qdb.close()}
        return out.sortedByDescending{it.file.lastModified()}
    }

    private fun render(){if(!::list.isInitialized)return;list.removeAllViews();val query=search.text.toString().trim().lowercase();val all=runCatching{artifacts()}.getOrDefault(emptyList());val filtered=all.filter{(filter=="ALL"||it.type==filter)&&(query.isBlank()||it.title.lowercase().contains(query)||it.subject.lowercase().contains(query)||it.type.lowercase().contains(query))}
        val grouped=filtered.groupBy{it.subject.ifBlank{"Unlinked"}}.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        if(filtered.isEmpty()){list.addView(TextView(this).apply{text="No saved ${if(filter=="ALL")"knowledge" else filter.lowercase()} found.";textSize=15f;setTextColor(ThemeManager.muted(this@KnowledgeVaultActivity));setPadding(d(10),d(24),d(10),d(24))});return}
        grouped.forEach{(subject,items)->
            list.addView(TextView(this).apply{text=subject.ifBlank{"Unlinked"};textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(this@KnowledgeVaultActivity));setPadding(d(4),d(12),d(4),d(4))})
            items.forEach{addRow(it)}
        }
    }
    private fun addRow(a:Artifact){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(d(12),d(10),d(8),d(10));background=rounded(ThemeManager.elevated(this@KnowledgeVaultActivity),14f)}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        body.addView(TextView(this).apply{text=a.title.ifBlank{"Untitled artifact"};textSize=14.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@KnowledgeVaultActivity));maxLines=2})
        body.addView(TextView(this).apply{text="${a.type}  •  Q${a.qid.takeIf{it>0}?:"—"}";textSize=11f;setTextColor(ThemeManager.muted(this@KnowledgeVaultActivity));setPadding(0,d(3),0,0)})
        row.addView(body,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(TextView(this).apply{text="⌫";textSize=19f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@KnowledgeVaultActivity));contentDescription="Delete";setOnClickListener{confirmDelete(a)}},LinearLayout.LayoutParams(d(44),d(44)))
        row.setOnClickListener{open(a)}
        list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(4),0,d(4))})
    }
    private fun confirmDelete(a:Artifact){AlertDialog.Builder(this).setTitle("Delete ${a.type.lowercase()}?").setMessage("This removes only the saved derived artifact. The original QBank question is untouched.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->if(AppManagers.knowledge.deleteDerivedFile(a.file))render()}.show()}
    private fun open(a:Artifact){if(!a.file.exists())return;if(a.type=="IMAGES"){val bm=BitmapFactory.decodeFile(a.file.absolutePath);AlertDialog.Builder(this).setTitle(a.subject).setView(ImageView(this).apply{adjustViewBounds=true;setImageBitmap(bm);setPadding(d(8),d(8),d(8),d(8))}).setPositiveButton("Close",null).show()}else{val raw=runCatching{a.file.readText().take(20000)}.getOrDefault("Unable to read artifact.");val msg=if(a.type=="TABLES")android.text.Html.fromHtml(raw,android.text.Html.FROM_HTML_MODE_LEGACY) else raw;AlertDialog.Builder(this).setTitle(a.subject).setMessage(msg).setPositiveButton("Close",null).show()}}
    private fun rounded(fill:Int,r:Float)=GradientDrawable().apply{setColor(fill);cornerRadius=r}
    override fun onResume(){super.onResume();if(::list.isInitialized)render()}
}
