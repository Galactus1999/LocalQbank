package com.localqbank.library

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class FlashcardActivity: AppCompatActivity() {
    private val flashcardViewModel: FlashcardViewModel by viewModels { (application as LocalQBankApplication).appContainer.flashcardViewModelFactory() }
    private var latestState = FlashcardUiState()
    private val executor=LifecycleExecutor(Executors.newSingleThreadExecutor { r -> Thread(r, "flashcard-activity") })
    private val mainHandler=Handler(Looper.getMainLooper())
    private var importControl: ApkgImporter.Control?=null
    private lateinit var list:LinearLayout
    private lateinit var reviewButton:TextView
    private lateinit var bookmarkButton:TextView
    private lateinit var srsSummary:TextView
    private lateinit var deckFilterRow:LinearLayout
    private val deckFilterButtons=LinkedHashMap<String,TextView>()
    private val expanded=mutableSetOf<String>()
    private var deckFilter="ALL"
    private val stateListener:()->Unit={if(!isFinishing && !isDestroyed) flashcardViewModel.refresh()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun rounded(fill:Int,r:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat()}

    // Flashcard surfaces deliberately reuse the soft pastel language already used by
    // the Home/Collections experience. Dark themes receive the same hues at a
    // lower luminance so the palette remains theme-aware without becoming neon.
    private fun pastel(index:Int):Int {
        val dark=ThemeManager.isDark(this)
        val i=((index%5)+5)%5
        return if(dark) when(i){
            0->Color.rgb(31,55,76)   // sky blue
            1->Color.rgb(61,42,52)   // light pink family
            2->Color.rgb(67,48,42)   // light red family
            3->Color.rgb(55,49,73)   // lavender
            else->Color.rgb(65,57,38) // soft yellow
        } else when(i){
            0->Color.rgb(235,245,255) // sky blue
            1->Color.rgb(255,237,241) // pink
            2->Color.rgb(255,232,229) // red
            3->Color.rgb(246,239,255) // lavender
            else->Color.rgb(255,245,228) // yellow
        }
    }
    private fun pastelText(fill:Int=0):Int=if(ThemeManager.isDark(this))ThemeManager.text(this) else Color.rgb(35,52,70)
    private fun pastelAccent(index:Int):Int {
        val dark=ThemeManager.isDark(this)
        val i=((index%5)+5)%5
        return if(dark) when(i){
            0->Color.rgb(150,205,255);1->Color.rgb(255,171,193);2->Color.rgb(255,171,159);3->Color.rgb(201,181,255);else->Color.rgb(255,218,125)
        } else when(i){
            0->Color.rgb(35,102,145);1->Color.rgb(139,38,65);2->Color.rgb(150,52,45);3->Color.rgb(92,68,135);else->Color.rgb(116,82,18)
        }
    }
    private fun headerGradient():IntArray = if(ThemeManager.isDark(this))
        intArrayOf(Color.rgb(28,43,61),Color.rgb(50,39,62))
    else intArrayOf(Color.rgb(235,245,255),Color.rgb(246,239,255))

    private fun button(text:String,fill:Int=pastel(0),click:()->Unit)=TextView(this).apply{
        this.text=text;textSize=12.5f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)
        setTextColor(pastelText())
        background=GradientDrawable().apply{setColor(fill);cornerRadius=14f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))65 else 50,100,120,140))}
        setOnClickListener{click()}
    }

    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        try {
            SystemUi.immersive(this)
            setContentView(build())
        TransitionCoordinator.install(this)
            AppState.register(stateListener)
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    flashcardViewModel.uiState.collect { state ->
                        latestState = state
                        if (::list.isInitialized) render(state)
                    }
                }
            }
            flashcardViewModel.refresh()
            intent.data?.let{importUri(it)}
            if (intent.data==null && ApkgImporter.hasPending(this)) showPendingImport()
        } catch (t:Exception) {
            val msg=t.message ?: t.javaClass.simpleName
            Toast.makeText(this,"Unable to open Flashcards: $msg",Toast.LENGTH_LONG).show()
            finish()
        }
    }
    override fun onResume(){super.onResume();flashcardViewModel.refresh()}

    private fun build():View {
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            background=ThemeManager.backgroundDrawable(this@FlashcardActivity)
            setPadding(0,0,0,dp(2))
        }

        // Compact modern header: keep the navigation chrome, but give the flashcard page
        // the same restrained visual hierarchy as Home instead of stacking large buttons.
        val bar=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(8),dp(5),dp(8),dp(5))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,headerGradient()).apply{cornerRadius=18f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))70 else 45,100,120,140))}
        }
        val back=TextView(this).apply{
            text="‹";textSize=30f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardActivity))
            setOnClickListener{finish()}
        }
        bar.addView(back,LinearLayout.LayoutParams(dp(42),dp(40)))
        val titleBlock=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL}
        titleBlock.addView(RovexWaveTextView(this).apply{text="Flashcards";textSize=21f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@FlashcardActivity))})
        titleBlock.addView(TextView(this).apply{text="Recall • review • repeat";textSize=10.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(1),0,0)})
        bar.addView(titleBlock,LinearLayout.LayoutParams(0,dp(40),1f))
        root.addView(bar,LinearLayout.LayoutParams(-1,dp(50)).apply{setMargins(dp(8),dp(6),dp(8),dp(4))})
        AdaptiveTypographyManager.apply(root)

        // The action board deliberately keeps only the high-frequency actions visible.
        val board=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(9),dp(10),dp(9))
            background=GradientDrawable().apply{
                setColor(ThemeManager.panel(this@FlashcardActivity))
                cornerRadius=20f*resources.displayMetrics.density
                setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))55 else 35,100,125,145))
            }
        }
        val primary=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        reviewButton=actionTile("REVIEW DUE","0 due",pastel(2)){startReview(false)}
        bookmarkButton=actionTile("BOOKMARKED","0 saved",pastel(4)){startReview(true)}
        val study=actionTile("STUDY","Choose mode",pastel(0)){showStudyMenu()}
        primary.addView(reviewButton,LinearLayout.LayoutParams(0,dp(58),1f).apply{setMargins(dp(2),dp(1),dp(3),dp(3))})
        primary.addView(bookmarkButton,LinearLayout.LayoutParams(0,dp(58),1f).apply{setMargins(dp(3),dp(1),dp(3),dp(3))})
        primary.addView(study,LinearLayout.LayoutParams(0,dp(58),1f).apply{setMargins(dp(3),dp(1),dp(2),dp(3))})
        board.addView(primary)

        val secondary=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val more=compactAction("•••  More tools",pastel(3)){showMoreMenu()}
        secondary.addView(more,LinearLayout.LayoutParams(-1,dp(40)).apply{setMargins(dp(2),dp(2),dp(2),dp(1))})
        board.addView(secondary)
        root.addView(board,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(10),dp(4),dp(10),dp(5))})

        srsSummary=TextView(this).apply{
            textSize=11.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.muted(this@FlashcardActivity))
            setPadding(dp(14),0,dp(14),dp(5));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        }
        root.addView(srsSummary)

        val sectionHeader=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(3),dp(14),dp(3))}
        sectionHeader.addView(TextView(this).apply{text="DECK LIBRARY";textSize=12f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.muted(this@FlashcardActivity));letterSpacing=.07f},LinearLayout.LayoutParams(0,-2,1f))
        sectionHeader.addView(TextView(this).apply{text="Tap a deck to study";textSize=10.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity))})
        root.addView(sectionHeader)

        deckFilterRow=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(10),dp(1),dp(10),dp(5))
        }
        listOf("ALL","MANUAL","IMPORTED").forEach{f->
            val chip=filterChip(f)
            deckFilterButtons[f]=chip
            deckFilterRow.addView(chip,LinearLayout.LayoutParams(0,dp(34),1f).apply{setMargins(dp(2),0,dp(2),0)})
        }
        root.addView(deckFilterRow)
        updateDeckFilterVisuals()

        val scroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(1),dp(10),dp(20))}
        scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        return root
    }

    private fun actionTile(title:String,subtitle:String,fill:Int,click:()->Unit):TextView{
        val dark=ThemeManager.isDark(this)
        return TextView(this).apply{
            text="$title\n$subtitle";textSize=11.5f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD);setLineSpacing(0f,.92f)
            setTextColor(pastelText())
            background=GradientDrawable().apply{
                setColor(fill);cornerRadius=15f*resources.displayMetrics.density
                setStroke(dp(1),Color.argb(if(dark)75 else 52,100,125,145))
            }
            isClickable=true;isFocusable=true;setOnClickListener{click()}
        }
    }

    private fun compactAction(label:String,fill:Int=pastel(3),click:()->Unit):TextView{
        return TextView(this).apply{
            text=label;textSize=11.5f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)
            setTextColor(pastelText())
            background=GradientDrawable().apply{setColor(fill);cornerRadius=13f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))65 else 50,100,125,145))}
            isClickable=true;isFocusable=true;setOnClickListener{click()}
        }
    }

    private fun filterChip(filter:String):TextView{
        return TextView(this).apply{
            text=filter;textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER
            isClickable=true;isFocusable=true;minHeight=dp(34)
            setOnClickListener{selectDeckFilter(filter)}
        }
    }

    private fun updateDeckFilterVisuals(){
        if(!::deckFilterRow.isInitialized)return
        deckFilterButtons.forEach{(filter,chip)->
            val selected=deckFilter==filter
            val paletteIndex=when(filter){"ALL"->0;"MANUAL"->4;else->1}
            chip.setTextColor(if(selected)pastelText() else ThemeManager.muted(this))
            chip.background=GradientDrawable().apply{
                setColor(if(selected)pastel(paletteIndex) else ThemeManager.elevated(this@FlashcardActivity))
                cornerRadius=12f*resources.displayMetrics.density
                setStroke(dp(1),if(selected)pastelAccent(paletteIndex) else Color.argb(45,100,125,145))
            }
            chip.alpha=if(selected)1f else .76f
        }
    }

    private fun selectDeckFilter(filter:String){
        if(deckFilter==filter)return
        deckFilter=filter
        expanded.clear()
        updateDeckFilterVisuals()
        render()
    }

    private fun refreshTop(state: FlashcardUiState = latestState){if(!::reviewButton.isInitialized)return;reviewButton.text="REVIEW DUE\n${state.reviewCount} due";bookmarkButton.text="BOOKMARKED\n${state.bookmarkCount} saved";if(::srsSummary.isInitialized){val s=state.dueStats;srsSummary.text="PENDING  •  Learning ${s.learning}  •  Hard ${s.hard}  •  Good ${s.good}  •  Easy ${s.easy}"}}
    private fun pickApkg(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/octet-stream";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/octet-stream","application/zip","application/x-zip-compressed"))},91)}
    override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data);if(req==91&&res==RESULT_OK)data?.data?.let{importUri(it)}}
    private fun showPendingImport(){
        AlertDialog.Builder(this).setTitle("Interrupted import found")
            .setMessage(richImportText("A previous flashcard import did not finish.\n\nProgress: ${ApkgImporter.pendingDescription(this) ?: "checkpoint available"}\n\nYou can safely resume it, or discard the partial import and start again."))
            .setNegativeButton("Discard"){_,_->ApkgImporter.discardPending(this);Toast.makeText(this,"Interrupted import discarded",Toast.LENGTH_SHORT).show()}
            .setPositiveButton("Resume"){_,_->resumeImport()}.create().also{it.setOnShowListener{styleAlertDialog(it as AlertDialog)}}.show()
    }
    private fun resumeImport(){runImport { control -> ApkgImporter.resume(this,{msg->mainHandler.post{currentImportDialog?.setMessage(richImportText(msg))}},control) }}
    private var currentImportDialog:AlertDialog?=null

    private fun richImportText(message:String):android.text.Spanned {
        val clean=message.trim()
        val out=android.text.SpannableStringBuilder()
        val lines=clean.split("\n")
        lines.forEachIndexed{index,line->
            val start=out.length
            val normalized=line.trim()
            out.append(if(normalized.startsWith("•")) normalized else normalized)
            val end=out.length
            val color=when {
                index==0 -> ThemeManager.accent(this)
                normalized.contains("progress",true) || normalized.contains("imported",true) || normalized.contains("cards",true) -> ThemeManager.pastelBlueText(this)
                normalized.contains("error",true) || normalized.contains("damaged",true) || normalized.contains("stopping",true) -> ThemeManager.pastelRedText(this)
                normalized.contains("resume",true) || normalized.contains("checkpoint",true) -> ThemeManager.pastelYellowText(this)
                else -> ThemeManager.text(this)
            }
            out.setSpan(android.text.style.ForegroundColorSpan(color),start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(index==0) out.setSpan(android.text.style.StyleSpan(Typeface.BOLD),start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(normalized.contains("progress",true) || normalized.contains("imported",true)) out.setSpan(android.text.style.StyleSpan(Typeface.BOLD),start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(index<lines.lastIndex) out.append("\n")
        }
        return out
    }

    private fun styleAlertDialog(dialog:AlertDialog){
        dialog.findViewById<TextView>(android.R.id.message)?.apply{
            setTextColor(ThemeManager.text(this@FlashcardActivity))
            setLineSpacing(0f,1.12f)
        }
        dialog.findViewById<TextView>(resources.getIdentifier("alertTitle","id","android"))?.setTextColor(ThemeManager.text(this))
        listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL).forEach{dialog.getButton(it)?.setTextColor(ThemeManager.accent(this))}
    }
    private fun importUri(uri:android.net.Uri){
        try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
        runImport { control -> ApkgImporter.importFile(this,uri,{msg->mainHandler.post{currentImportDialog?.setMessage(richImportText(msg))}},control) }
    }
    private fun runImport(job:(ApkgImporter.Control)->ApkgImporter.Result){
        val control=ApkgImporter.Control()
        importControl=control
        val dialog=AlertDialog.Builder(this).setTitle("Importing flashcards")
            .setMessage("Starting…\nThe import can resume if interrupted.").setCancelable(false)
            .setNegativeButton("Cancel import",null).create()
        currentImportDialog=dialog
        dialog.setOnShowListener { d ->
            val alert=d as AlertDialog
            styleAlertDialog(alert)
            alert.setMessage(richImportText("Starting…\nThe import can resume if interrupted."))
            alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                control.cancelled=true
                alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled=false
                alert.setMessage(richImportText("Stopping safely at the last checkpoint…"))
            }
        }
        dialog.show()
        executor.execute {
            try {
                val r=job(control)
                runOnUiThread {
                    if(!isFinishing){
                        dialog.dismiss(); currentImportDialog=null; importControl=null
                        Toast.makeText(this,"Imported ${r.cards} cards from ${r.decks} deck(s)${if(r.skipped>0)" • skipped ${r.skipped} damaged item(s)" else ""}",Toast.LENGTH_LONG).show()
                        flashcardViewModel.refresh()
                    }
                }
            } catch(e:Exception) {
                runOnUiThread {
                    if(!isFinishing){
                        dialog.dismiss(); currentImportDialog=null; importControl=null
                        val pending=ApkgImporter.hasPending(this)
                        AlertDialog.Builder(this)
                            .setTitle(if(control.cancelled)"Import paused safely" else "Import needs attention")
                            .setMessage(richImportText(if(pending){
                                "The completed batches are safe.\n\n${if(control.cancelled)"The import was cancelled at a checkpoint." else (e.message ?: "An import error occurred.")}\n\nYou can resume from the last checkpoint without starting over."
                            } else (e.message ?: "unsupported or damaged deck")))
                            .setNegativeButton("Close",null)
                            .setPositiveButton("Resume"){_,_->resumeImport()}
                            .create().also{it.setOnShowListener{styleAlertDialog(it as AlertDialog)}}
                            .show()
                    }
                }
            }
        }
    }

    private data class Node(val name:String,val full:String,val depth:Int,val deck:FlashcardDb.Deck?,val children:MutableList<Node>)
    private fun safeRender(){
        if(!::list.isInitialized)return
        runCatching { render() }.onFailure {
            list.removeAllViews()
            list.addView(TextView(this).apply {
                text="Flashcard library could not be loaded. Your QBank data is unaffected.\n\n${it.javaClass.simpleName}: ${it.message ?: "unknown error"}"
                textSize=14f; setTextColor(ThemeManager.text(this@FlashcardActivity)); setPadding(dp(16),dp(24),dp(16),dp(24))
            })
        }
    }

    private fun render(state: FlashcardUiState = latestState){if(!::list.isInitialized)return;list.removeAllViews();refreshTop(state);val decks=state.decks;if(decks.isEmpty()){list.addView(TextView(this).apply{text="No flashcard decks yet. Import an .apkg deck to begin.";textSize=15f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(dp(12),dp(24),dp(12),dp(24))});return};val visibleDecks=decks.filter{
            when(deckFilter){
                "ALL" -> true
                // "Adaptive" is retained only as a compatibility alias for decks created by older Rovex builds.
                "MANUAL" -> it.source?.contains("Manual",true)==true || it.source?.contains("Adaptive",true)==true
                "IMPORTED" -> it.source?.contains("Manual",true)!=true && it.source?.contains("Adaptive",true)!=true
                else -> true
            }
        };if(visibleDecks.isEmpty()){list.addView(TextView(this).apply{text="No decks in this section yet.";textSize=14f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(dp(12),dp(24),dp(12),dp(24))});return};val byFull=visibleDecks.associateBy{it.name};val roots=mutableListOf<Node>();val nodes=HashMap<String,Node>();fun nodeFor(path:String):Node{nodes[path]?.let{return it};val parts=path.split("::");val node=Node(parts.last(),parts.joinToString("::"),parts.size-1,byFull[parts.joinToString("::")],mutableListOf());nodes[node.full]=node;if(parts.size>1){val parent=nodeFor(parts.dropLast(1).joinToString("::"));parent.children.add(node)}else roots.add(node);return node};visibleDecks.forEach{nodeFor(it.name)}
        fun countTree(n:Node):Int=(n.deck?.count?:0)+n.children.sumOf{countTree(it)}
        var rootIndex=0
        fun addNode(n:Node){val own=n.deck;val has=n.children.isNotEmpty();val total=countTree(n);val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10+n.depth*20),dp(9),dp(10),dp(9));background=GradientDrawable().apply{setColor(if(n.depth==0) pastel(rootIndex++) else ThemeManager.elevated(this@FlashcardActivity));cornerRadius=14f;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))55 else 35,100,120,140))}};val arrow=TextView(this).apply{text=if(has){if(expanded.contains(n.full))"▼" else "▶"}else"•";textSize=14f;setTextColor(ThemeManager.accent(this@FlashcardActivity));gravity=Gravity.CENTER};row.addView(arrow,LinearLayout.LayoutParams(dp(28),dp(48)));val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL};body.addView(TextView(this).apply{text=n.name;textSize=if(n.depth==0)17f else 15.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@FlashcardActivity));maxLines=2});body.addView(TextView(this).apply{
                    if(own!=null){
                        val ds=state.deckDueStats[own.id] ?: FlashcardDb.DueStats(0,0,0,0,0)
                        text=when {
                            ds.due>0 -> "${own.count} cards  •  ${ds.due} due now  •  Learning ${ds.learning}  •  Hard ${ds.hard}  •  Good ${ds.good}  •  Easy ${ds.easy}"
                            else -> "${own.count} cards  •  0 due now  •  Next reviews are scheduled automatically"
                        }
                    } else text=if(has)"$total cards  •  subdecks" else "Category"
                    textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(3),0,0);maxLines=3
                });row.addView(body,LinearLayout.LayoutParams(0,-2,1f));if(own!=null&&own.count>0){val study=button("STUDY",pastel(0)){startStudy(own.id)};row.addView(study,LinearLayout.LayoutParams(dp(62),dp(38)).apply{setMargins(dp(6),0,0,0)})};val more=TextView(this).apply{text="⋮";textSize=22f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardActivity));setOnClickListener{showDeckActions(n)}};row.addView(more,LinearLayout.LayoutParams(dp(42),dp(42)).apply{setMargins(dp(4),0,0,0)});row.setOnClickListener{if(has){if(expanded.contains(n.full))expanded.remove(n.full)else expanded.add(n.full);render(latestState)}else if(own!=null)startStudy(own.id)};row.setOnLongClickListener{showDeckActions(n);true};list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(4),dp(4),dp(4),dp(4))});if(expanded.contains(n.full))n.children.sortedBy{it.name.lowercase()}.forEach{addNode(it)}};roots.sortedBy{it.name.lowercase()}.forEach{addNode(it)}
        AdaptiveTypographyManager.apply(list)
    }

    private fun popupShell(title:String, subtitle:String):LinearLayout{
        return LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(12),dp(18),dp(18))
            setBackgroundColor(ThemeManager.dialogBg(this@FlashcardActivity))
            addView(TextView(this@FlashcardActivity).apply{text=title;textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@FlashcardActivity));setPadding(0,dp(2),0,dp(2))})
            addView(TextView(this@FlashcardActivity).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(3),0,dp(10))})
        }
    }
    private fun popupSection(parent:LinearLayout,title:String,subtitle:String,fill:Int=ThemeManager.elevated(this),accent:Int=ThemeManager.accent(this)):LinearLayout{
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(11),dp(14),dp(11));background=GradientDrawable().apply{setColor(fill);cornerRadius=14f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(40,110,130,145))}}
        card.addView(TextView(this).apply{text=title;textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(accent)})
        card.addView(TextView(this).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(4),0,0)})
        parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(5),0,dp(5))})
        return card
    }
    private fun showSheet(title:String, subtitle:String, buildBody:(LinearLayout, Dialog)->Unit){
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(14));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@FlashcardActivity));cornerRadius=24f*resources.displayMetrics.density}}
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val titles=LinearLayout(this@FlashcardActivity).apply{orientation=LinearLayout.VERTICAL}
        titles.addView(TextView(this@FlashcardActivity).apply{text=title;textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@FlashcardActivity))})
        titles.addView(TextView(this@FlashcardActivity).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(3),0,0)})
        head.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        head.addView(TextView(this).apply{text="×";textSize=26f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardActivity));background=rounded(ThemeManager.elevated(this@FlashcardActivity),14);setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(44),dp(44)))
        root.addView(head)
        val scroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
        val body=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(0,dp(10),0,dp(4))
            isFocusableInTouchMode=true
        }
        buildBody(body,dialog)
        scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root)
        dialog.setOnShowListener{dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.92f).toInt(),(resources.displayMetrics.heightPixels*0.78f).toInt())}
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.92f).toInt(),(resources.displayMetrics.heightPixels*0.78f).toInt())
        body.requestFocus()
        scroll.post { scroll.scrollTo(0,0) }
        AdaptiveTypographyManager.apply(root)
    }

    private fun sheetCard(parent:LinearLayout,title:String,subtitle:String,fill:Int=pastel(0),click:(Dialog)->Unit){
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12));isClickable=true;isFocusable=true;background=GradientDrawable().apply{setColor(fill);cornerRadius=16f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))65 else 45,100,120,140))};setOnClickListener{click(parent.getTag() as? Dialog ?: return@setOnClickListener)}}
        card.addView(TextView(this).apply{text=title;textSize=14.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@FlashcardActivity))})
        card.addView(TextView(this).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(4),0,0)})
        parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
    }

    private fun showStudyMenu(){
        val specs=arrayOf(
            Triple("Due now","Questions currently scheduled for review • ${latestState.modeCounts["due"] ?: 0}","due"),
            Triple("All cards","Review the complete deck library • ${latestState.modeCounts["all"] ?: 0}","all"),
            Triple("New / unseen","Cards you have not studied yet • ${latestState.modeCounts["unseen"] ?: 0}","unseen"),
            Triple("Hard","Cards repeatedly rated difficult • ${latestState.modeCounts["hard"] ?: 0}","hard"),
            Triple("Again / failed","Cards that need another pass • ${latestState.modeCounts["again"] ?: 0}","again"),
            Triple("Bookmarked","Your saved flashcard bookmarks • ${latestState.modeCounts["bookmarked"] ?: 0}","bookmarked"),
            Triple("Marked","Cards explicitly marked for study • ${latestState.modeCounts["marked"] ?: 0}","marked")
        )
        val limits=intArrayOf(0,0,0,0,0,0,0)
        showSheet("Study menu","A visual study launcher instead of a plain text dialog"){body,dialog->
            body.setTag(dialog)
            specs.forEachIndexed{i,(title,sub,mode)->
                sheetCard(body,title,sub,pastel(i)){d->startActivity(Intent(this,FlashcardStudyActivity::class.java).putExtra("deckId",-1L).putExtra("mode",mode).putExtra("limit",limits[i]));d.dismiss()}
            }
            listOf(10 to "10-card sprint",25 to "25-card sprint",50 to "50-card sprint",100 to "100-card sprint").forEachIndexed{i, item -> val (limit,label)=item
                sheetCard(body,label,"Short focused review block",pastel(i+2)){d->startActivity(Intent(this,FlashcardStudyActivity::class.java).putExtra("deckId",-1L).putExtra("mode","all").putExtra("limit",limit));d.dismiss()}
            }
        }
    }

    private fun showMoreMenu(){
        showSheet("Flashcard tools","Less-used controls live here so the study board stays clean"){body,dialog->
            body.setTag(dialog)
            sheetCard(body,"SRS settings","Daily limits, scheduling intervals, ease and leech controls",pastel(4)){d->d.dismiss();showSrsSettings()}
            sheetCard(body,"Statistics","Today’s reviews, library totals, bookmarks and limits",pastel(1)){d->d.dismiss();showStats()}
            sheetCard(body,"Import .apkg","Add another Anki/flashcard deck to the local library",pastel(0)){d->d.dismiss();pickApkg()}
        }
    }

    private fun showSrsSettings(){
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(14),dp(18),dp(14))
            background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@FlashcardActivity));cornerRadius=24f*resources.displayMetrics.density}
        }
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val titles=LinearLayout(this@FlashcardActivity).apply{orientation=LinearLayout.VERTICAL}
        titles.addView(TextView(this@FlashcardActivity).apply{text="SRS settings";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@FlashcardActivity))})
        titles.addView(TextView(this@FlashcardActivity).apply{text="Daily targets, limits and scheduling • saved locally";textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(3),0,0)})
        header.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(TextView(this).apply{text="×";textSize=26f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardActivity));background=rounded(ThemeManager.elevated(this@FlashcardActivity),14);setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(44),dp(44)))
        root.addView(header)

        val scroll=ScrollView(this).apply{isFillViewport=false;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(10),0,dp(10))}
        val inputs=LinkedHashMap<String,EditText>()
        fun addSection(title:String, subtitle:String){
            body.addView(TextView(this).apply{text=title;textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(this@FlashcardActivity));setPadding(dp(2),dp(10),dp(2),dp(3))})
            body.addView(TextView(this).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(dp(2),0,dp(2),dp(6))})
        }
        fun addField(key:String,label:String,description:String,default:String,decimal:Boolean=false){
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10));background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@FlashcardActivity));cornerRadius=15f*resources.displayMetrics.density}}
            card.addView(TextView(this).apply{text=label;textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@FlashcardActivity))})
            card.addView(TextView(this).apply{text=description;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardActivity));setPadding(0,dp(3),0,dp(6))})
            val input=EditText(this).apply{
                inputType=if(decimal) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else android.text.InputType.TYPE_CLASS_NUMBER
                val todayKey=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date())
                val stored=if(key.startsWith("today_") && latestState.settings["today_override_date"].orEmpty()!=todayKey) default else latestState.settings[key].takeUnless { it.isNullOrBlank() } ?: default
                setText(stored);setSelectAllOnFocus(true);setSingleLine(true);textSize=16f
                isFocusable=true;isFocusableInTouchMode=true;isEnabled=true;isClickable=true;isCursorVisible=true;showSoftInputOnFocus=true
                setTextColor(ThemeManager.text(this@FlashcardActivity));setHintTextColor(ThemeManager.muted(this@FlashcardActivity));background=rounded(ThemeManager.bg(this@FlashcardActivity),10);setPadding(dp(12),0,dp(12),0)
            }
            card.addView(input,LinearLayout.LayoutParams(-1,dp(44)));inputs[key]=input
            body.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
        addSection("DAILY LIMITS","Anki-style controls: set the normal daily workload, plus optional temporary limits for today only.")
        addField("new_per_day","New cards / day","Maximum unseen cards that can enter the learning/review queue each day.","30")
        addField("max_reviews_per_day","Reviews / day","Maximum scheduled review cards shown each day.","200")
        addField("max_cards_per_day","Total cards / day","Hard safety cap across new + review cards for the day.","300")
        addSection("TODAY ONLY","Leave these at 0 to use your normal daily limits. These overrides reset automatically at the next calendar day.")
        addField("today_new_limit","Today's new-card limit","Temporary new-card limit for today only. 0 = use normal limit.","0")
        addField("today_review_limit","Today's review limit","Temporary review limit for today only. 0 = use normal limit.","0")
        addField("today_total_limit","Today's total limit","Temporary total-card cap for today only. 0 = use normal limit.","0")
        addSection("SCHEDULING","These controls affect how cards move through the SRS intervals.")
        addField("learning_again_min","Again delay (minutes)","Delay before an Again-rated card becomes available again.","10")
        addField("initial_interval_days","Initial interval (days)","Starting interval after the first successful recall.","1")
        addField("easy_multiplier","Easy interval multiplier","Multiplier applied when a card is rated Easy.","1.30",true)
        addField("hard_multiplier","Hard interval multiplier","Multiplier applied when a card is rated Hard.","1.20",true)
        addField("leech_threshold","Leech threshold","Number of failures before a card is marked as a leech.","8")
        scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))

        val footer=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(6),0,0)}
        val reset=TextView(this).apply{text="RESET";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardActivity));background=rounded(ThemeManager.elevated(this@FlashcardActivity),14);setOnClickListener{
            inputs.forEach{(key,v)->v.setText(when(key){"new_per_day"->"30";"max_reviews_per_day"->"200";"max_cards_per_day"->"300";"today_new_limit","today_review_limit","today_total_limit"->"0";"learning_again_min"->"10";"initial_interval_days"->"1";"easy_multiplier"->"1.30";"hard_multiplier"->"1.20";else->"8"})}
        }}
        val save=TextView(this).apply{text="SAVE SETTINGS";textSize=12.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardActivity));background=rounded(pastel(0),14);setOnClickListener{
            val intKeys=setOf("new_per_day","max_reviews_per_day","max_cards_per_day","today_new_limit","today_review_limit","today_total_limit","learning_again_min","initial_interval_days","leech_threshold")
            val ranges=mapOf("new_per_day" to (1L..10000L),"max_reviews_per_day" to (1L..10000L),"max_cards_per_day" to (1L..20000L),"today_new_limit" to (0L..10000L),"today_review_limit" to (0L..10000L),"today_total_limit" to (0L..20000L),"learning_again_min" to (1L..1440L),"initial_interval_days" to (1L..3650L),"leech_threshold" to (1L..100L))
            var anyTodayOverride=false
            val values=LinkedHashMap<String,String>()
            inputs.forEach{(key,v)->
                val raw=v.text.toString().trim()
                val value=if(key in intKeys){raw.toLongOrNull()?.coerceIn(ranges[key]!!.first,ranges[key]!!.last)?.toString() ?: latestState.settings[key].takeUnless { it.isNullOrBlank() } ?: "0"}else{raw.toDoubleOrNull()?.coerceIn(0.1,5.0)?.let{"%.2f".format(java.util.Locale.US,it)} ?: latestState.settings[key].takeUnless { it.isNullOrBlank() } ?: "1.20"}
                values[key]=value
                if(key.startsWith("today_") && value.toLongOrNull()?.let{it>0}==true) anyTodayOverride=true
            }
            values["today_override_date"]=if(anyTodayOverride)java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date()) else ""
            flashcardViewModel.saveSettings(values)
            Toast.makeText(this@FlashcardActivity,"SRS settings saved",Toast.LENGTH_SHORT).show();dialog.dismiss()
        }}
        footer.addView(reset,LinearLayout.LayoutParams(dp(92),dp(48)).apply{setMargins(0,0,dp(8),0)})
        footer.addView(save,LinearLayout.LayoutParams(0,dp(48),1f))
        root.addView(footer)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener{dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.92f).toInt(),(resources.displayMetrics.heightPixels*0.86f).toInt())}
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.92f).toInt(),(resources.displayMetrics.heightPixels*0.86f).toInt())
        scroll.post{scroll.scrollTo(0,0)}
    }

    private fun showStats(){
        val today=latestState.reviewedToday;val total=latestState.totalReviews;val due=latestState.reviewCount;val all=latestState.allCardCount
        showSheet("Flashcard statistics","A quick view of your review engine"){body,_->
            sheetStat(body,"TODAY","Reviewed: $today • Remaining due: $due",ThemeManager.statsFill(this),ThemeManager.statsText(this))
            sheetStat(body,"LIBRARY","Total cards: $all • Total reviews: $total",ThemeManager.elevated(this),ThemeManager.accent(this))
            sheetStat(body,"BOOKMARKS","Bookmarked cards: ${latestState.bookmarkCount}",ThemeManager.peacockFill(this),ThemeManager.peacockText(this))
            sheetStat(body,"DAILY LIMITS","New: ${latestState.settings["new_per_day"]?.toIntOrNull() ?: 30} • Reviews: ${latestState.settings["max_reviews_per_day"]?.toIntOrNull() ?: 200}",ThemeManager.elevated(this),ThemeManager.accent(this))
        }
    }

    private fun sheetStat(parent:LinearLayout,title:String,text:String,fill:Int,accent:Int){
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12));background=GradientDrawable().apply{setColor(fill);cornerRadius=15f*resources.displayMetrics.density}}
        card.addView(TextView(this).apply{this.text=title;textSize=11f;typeface=Typeface.DEFAULT_BOLD;setTextColor(accent);letterSpacing=.06f})
        card.addView(TextView(this).apply{this.text=text;textSize=13.5f;setTextColor(ThemeManager.text(this@FlashcardActivity));setPadding(0,dp(5),0,0)})
        parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
    }

    private fun startStudy(id:Long){startActivity(Intent(this,FlashcardStudyActivity::class.java).putExtra("deckId",id))}
    private fun startReview(bookmarks:Boolean){startActivity(Intent(this,FlashcardStudyActivity::class.java).putExtra("deckId",-1L).putExtra("bookmarksOnly",bookmarks))}
    private fun showDeckActions(n:Node){
        val own=n.deck
        val hasChildren=n.children.isNotEmpty()
        showSheet(n.name,"Deck actions • ${if(own!=null) "${own.count} cards" else "category"}"){body,dialog->
            body.setTag(dialog)
            if(own!=null) sheetCard(body,"Study deck","Open this deck in the focused reviewer"){d->if(own.count>0)startStudy(own.id)else Toast.makeText(this,"This deck has no cards.",Toast.LENGTH_SHORT).show();d.dismiss()}
            if(hasChildren) sheetCard(body,"Expand / collapse","Show or hide subdecks"){d->if(expanded.contains(n.full))expanded.remove(n.full)else expanded.add(n.full);d.dismiss();render(latestState)}
            if(own!=null) sheetCard(body,"Unsuspend cards","Make suspended cards available again"){d->flashcardViewModel.unsuspendDeck(own.id,false){n2->Toast.makeText(this,"Unsuspended $n2 card(s)",Toast.LENGTH_SHORT).show();d.dismiss();render(latestState)}}
            if(hasChildren && own==null) sheetCard(body,"Unsuspend subdecks","Restore cards across this category"){d->val count=n.children.sumOf{it.deck?.count?:0};n.children.forEach{it.deck?.let{x->flashcardViewModel.unsuspendDeck(x.id,false){}}};Toast.makeText(this,"Unsuspended cards in $count-card category",Toast.LENGTH_SHORT).show();d.dismiss();flashcardViewModel.refresh()}
            if(own!=null) sheetCard(body,if(hasChildren)"Delete deck" else "Delete deck","Remove this deck while keeping subdecks if any"){d->confirmDelete(n,false,d)}
            if(hasChildren) sheetCard(body,"Delete category + subdecks","Permanently remove this branch of the flashcard library"){d->confirmDelete(n,true,d)}
        }
    }

    private fun confirmDelete(n:Node,all:Boolean,parent:Dialog){
        val msg=if(all)"Delete ${n.full} and every subdeck? This permanently removes those flashcards from Rovex." else "Delete ${n.full}? Its subdecks will remain."
        AlertDialog.Builder(this).setTitle("Confirm deletion").setMessage(richImportText(msg)).setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->
            val finish: (Int)->Unit={removed->Toast.makeText(this,if(removed>0)"Deleted $removed deck(s)" else "Nothing was deleted",Toast.LENGTH_SHORT).show();parent.dismiss()}
            if(all) flashcardViewModel.deleteDeckTree(n.full, finish) else n.deck?.let { deck -> flashcardViewModel.deleteDeck(deck.id,false,finish) }
        }.create().also{it.setOnShowListener{styleAlertDialog(it as AlertDialog)}}.show()
    }

    override fun onDestroy(){AppState.unregister(stateListener);executor.close();super.onDestroy()}
}
