package com.localqbank.library

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class StudyToolsActivity: AppCompatActivity(){
    private lateinit var db:QBankDb
    private lateinit var store:ProgressStore
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
    private fun rounded(fill:Int,r:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat()}
    override fun onCreate(b:Bundle?){super.onCreate(b);AppManagers.initialize(applicationContext);SystemUi.immersive(this);db=QBankDb(this);store=ProgressStore(this);setContentView(build());if(intent.getBooleanExtra("openSubjectFocus",false))window.decorView.post{subjectFocusDialog()
        TransitionCoordinator.install(this)}}
    private fun build():ScrollView{
        val scroll=ScrollView(this).apply{background=ThemeManager.backgroundDrawable(this@StudyToolsActivity);isFillViewport=true}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(12),dp(18),dp(24))}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val back=button("←",14f){finish()};bar.addView(back,LinearLayout.LayoutParams(dp(48),dp(44)))
        val title=TextView(this).apply{text="Study Tools";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@StudyToolsActivity));setPadding(dp(12),0,0,0)}
        bar.addView(title,LinearLayout.LayoutParams(0,dp(44),1f));root.addView(bar)
        AdaptiveTypographyManager.apply(root)
        val intro=TextView(this).apply{text="Turn your imported QBank into a revision and exam system. All tools use the same question database and progress.";textSize=14f;setTextColor(ThemeManager.muted(this@StudyToolsActivity));setPadding(0,dp(10),0,dp(16))};root.addView(intro)
        addTool(root,"Today's Revision","Due questions • spaced revision", "REVISION"){launchCollection(dueIds(),"Today's Revision",true)}
        addTool(root,"Adaptive Smart Mix","A personalised mix from your current learning state", "SMART"){val ids=AppManagers.studyIntelligence.smartMix(100);launchCollection(ids,"Adaptive Smart Mix",true)}
        addTool(root,"Weakest 50","Wrong + low-performing questions first", "FOCUS"){launchCollection(weakIds(),"Weakest 50",true)}
        addTool(root,"Unsolved Sprint","Fresh questions you have not attempted", "BUILD"){launchCollection(unsolvedIds(),"Unsolved Sprint",true)}
        addTool(root,"Wrong Questions","Reattempt your mistakes", "CORRECT"){launchCollection(wrongIds(),"Wrong Questions",true)}
        addTool(root,"Why I Missed This","Tagged mistake reasons • build targeted revision collections", "MISSED"){mistakeReviewDialog()}
        addTool(root,"PYQ Mode","Questions labelled as previous-year questions", "EXAM"){pyqIds().let{launchCollection(it,"PYQ",true)}}
        addTool(root,"Subject Focus","Type a subject or topic and build a focused question set", "FOCUS"){subjectFocusDialog()}
        addTool(root,"Saved Subject Sets","Your saved Subject Focus sets, grouped by subject", "SETS"){savedSubjectSetsDialog()}
        addTool(root,"Exam Mode","Timed block • submit once at the end", "TIMED"){examDialog()}
        addTool(root,"Custom Test","Choose size and question pool", "CUSTOM"){customDialog()}
        addTool(root,"My Notes","Saved notes and memory cues", "NOTES"){startActivity(Intent(this,NotesActivity::class.java))}
        addTool(root,"Knowledge Vault","Auto-notes, copied tables and learning images", "VAULT"){startActivity(Intent(this,KnowledgeVaultActivity::class.java))}
        addTool(root,"Flashcards","Create and review cards you choose", "CARDS"){
            startActivity(Intent(this,FlashcardActivity::class.java))
        }
        val hint=TextView(this).apply{text="Tip: use Today's Revision daily, then Weakest 50 when your accuracy plateaus.";textSize=13f;setTextColor(ThemeManager.muted(this@StudyToolsActivity));setPadding(dp(4),dp(18),dp(4),0)};root.addView(hint)
        scroll.addView(root);return scroll
    }
    private fun addTool(root:LinearLayout,title:String,sub:String,tag:String,click:()->Unit){
        val dark=ThemeManager.isDark(this)
        val card=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(12),dp(12),dp(12));background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@StudyToolsActivity));cornerRadius=dp(15).toFloat();setStroke(dp(1),if(dark)Color.rgb(45,57,70) else Color.rgb(222,227,231))};setOnClickListener{click()}}
        AliveMotion.install(card)
        val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        copy.addView(RovexWaveTextView(this).apply{text=title;textSize=18f;maxLines=1;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@StudyToolsActivity))})
        copy.addView(TextView(this).apply{text=sub;textSize=12.5f;setTextColor(ThemeManager.muted(this@StudyToolsActivity));setPadding(0,dp(4),0,0)})
        card.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        card.addView(TextView(this).apply{text=tag;textSize=10f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(this@StudyToolsActivity));background=GradientDrawable().apply{setColor(ThemeManager.explanationBg(this@StudyToolsActivity));cornerRadius=dp(9).toFloat()};setPadding(dp(8),0,dp(8),0)},LinearLayout.LayoutParams(dp(70),dp(28)))
        card.addView(TextView(this).apply{text="›";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@StudyToolsActivity))},LinearLayout.LayoutParams(dp(28),dp(40)))
        root.addView(card,LinearLayout.LayoutParams(-1,dp(78)).apply{setMargins(0,dp(5),0,dp(5))})
    }
    private fun button(text:String,size:Float,click:()->Unit)=TextView(this).apply{this.text=text;textSize=size;gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@StudyToolsActivity));background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@StudyToolsActivity));cornerRadius=dp(14).toFloat()};setOnClickListener{click()}}
    private fun launchCollection(ids:LongArray,label:String,practice:Boolean){if(ids.isEmpty()){Toast.makeText(this,"No questions available",Toast.LENGTH_SHORT).show();return};startActivity(Intent(this,QuizActivity::class.java).putExtra("collectionMode",true).putExtra("sessionIds",ids.joinToString(",")).putExtra("sessionLabel",label).putExtra("practiceMode",practice).putExtra("title",label))}
    private fun dueIds():LongArray {
        val refs=PerformanceManager.refs(applicationContext); val p=PerformanceManager.progress(applicationContext); val now=System.currentTimeMillis()
        return refs.asSequence().filter { r -> val x=p.record(r.stableKey); x?.status != null && (x.nextDue>0L && now>=x.nextDue || x.nextDue==0L && x.lastAttempted>0L && now-x.lastAttempted>=when(x.attempts){1->86_400_000L;2->3*86_400_000L;3->7*86_400_000L;4->14*86_400_000L;else->30*86_400_000L}) }.take(100).map{it.id}.toList().toLongArray()
    }
    private fun weakIds():LongArray {
        val refs=PerformanceManager.refs(applicationContext); val p=PerformanceManager.progress(applicationContext)
        return refs.sortedWith(compareBy<QuestionRef>{when(p.record(it.stableKey)?.status){"wrong"->0;null->1;else->2}}.thenByDescending{p.record(it.stableKey)?.attempts ?: 0}).take(50).map{it.id}.toLongArray()
    }
    private fun unsolvedIds():LongArray { val p=PerformanceManager.progress(applicationContext); return PerformanceManager.refs(applicationContext).filter{p.record(it.stableKey)?.status==null}.map{it.id}.toLongArray() }
    private fun wrongIds():LongArray { val p=PerformanceManager.progress(applicationContext); return PerformanceManager.refs(applicationContext).filter{p.record(it.stableKey)?.status=="wrong"}.sortedByDescending{p.record(it.stableKey)?.attempts ?: 0}.map{it.id}.toLongArray() }
    private fun pyqIds():LongArray{return PerformanceManager.refs(applicationContext).filter{it.sourceName.contains("pyq",true)||it.testTitle.contains("pyq",true)||it.category.contains("pyq",true)}.map{it.id}.toLongArray()}
    private fun mistakeChoices():List<Pair<String,String>> = listOf(
        "Didn't know" to "Concept or fact was not in recall.",
        "Concept confusion" to "Related concepts were mixed up.",
        "Misread question" to "Stem, qualifier, image or wording was misread.",
        "Silly mistake" to "An avoidable error despite knowing the concept.",
        "Changed correct answer" to "Correct first choice was changed incorrectly.",
        "Time pressure" to "Time pressure caused the wrong response."
    )

    private fun mistakeIds(reason:String?=null):LongArray {
        val p=PerformanceManager.progress(applicationContext)
        return PerformanceManager.lightRefs(applicationContext).asSequence().filter { ref ->
            val m=store.mistakeType(ref.stableKey)
            if(reason==null) !m.isNullOrBlank() else m.equals(reason,true)
        }.map{it.id}.toList().toLongArray()
    }

    private fun mistakeReviewDialog(){
        val counts=mistakeChoices().associate { it.first to mistakeIds(it.first).size }
        val choices=mutableListOf(Choice("All missed-reason questions","${mistakeIds().size} questions tagged with a reason"){launchCollection(mistakeIds(),"Why I Missed This",true)})
        mistakeChoices().forEach { (reason,desc) -> choices.add(Choice("$reason  •  ${counts[reason] ?: 0}",desc){val ids=mistakeIds(reason);if(ids.isEmpty())Toast.makeText(this,"No questions tagged '$reason'",Toast.LENGTH_SHORT).show()else launchCollection(ids,"Missed • $reason",true)}) }
        choices.add(Choice("Manage from a question","Open any wrong question and tap Why did I miss this? to retag it."){val ids=wrongIds().take(1).toLongArray();if(ids.isEmpty())Toast.makeText(this,"No wrong questions yet",Toast.LENGTH_SHORT).show()else startActivity(Intent(this,QuizActivity::class.java).putExtra("collectionMode",true).putExtra("sessionIds",ids.joinToString(",")).putExtra("practiceMode",true).putExtra("title","Why I Missed This"))})
        visualSheet("Why I Missed This","Turn error patterns into focused collections. Tags are stored with your existing study progress.",choices)
    }

    private fun noteIds():LongArray=db.notes().map{it.first}.toLongArray()

    private data class Choice(val title:String,val subtitle:String,val action:()->Unit)

    private fun visualSheet(title:String, subtitle:String, choices:List<Choice>){
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(14));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@StudyToolsActivity));cornerRadius=dp(24).toFloat()}}
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(RovexWaveTextView(this@StudyToolsActivity).apply{text=title;textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@StudyToolsActivity))},LinearLayout.LayoutParams(0,dp(44),1f))
        head.addView(TextView(this).apply{text="×";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@StudyToolsActivity));background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@StudyToolsActivity));cornerRadius=dp(14).toFloat()};setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(44),dp(44)))
        root.addView(head)
        root.addView(TextView(this).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@StudyToolsActivity));setPadding(0,dp(2),0,dp(9))})
        val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        choices.forEachIndexed{i,c->
            val fill=ThemeManager.pastelAccentFill(this@StudyToolsActivity,i)
            val fg=ThemeManager.pastelAccentText(this@StudyToolsActivity,i)
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12));isClickable=true;isFocusable=true;background=GradientDrawable().apply{setColor(fill);cornerRadius=dp(16).toFloat();setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@StudyToolsActivity))70 else 45,100,120,140))};setOnClickListener{dialog.dismiss();c.action()}}
            card.addView(TextView(this@StudyToolsActivity).apply{text=c.title;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(fg)})
            card.addView(TextView(this@StudyToolsActivity).apply{text=c.subtitle;textSize=11.5f;setTextColor(if(ThemeManager.isDark(this@StudyToolsActivity))fg else ThemeManager.text(this@StudyToolsActivity));setPadding(0,dp(4),0,0)})
            AliveMotion.install(card);body.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((resources.displayMetrics.widthPixels*.92f).toInt(),(resources.displayMetrics.heightPixels*.78f).toInt());AdaptiveTypographyManager.apply(root)
    }

    private fun subjectFocusDialog(){
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(16));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@StudyToolsActivity));cornerRadius=dp(24).toFloat()}}
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(RovexWaveTextView(this@StudyToolsActivity).apply{text="Subject Focus";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@StudyToolsActivity))},LinearLayout.LayoutParams(0,dp(44),1f))
        head.addView(TextView(this).apply{text="×";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@StudyToolsActivity));background=rounded(ThemeManager.elevated(this@StudyToolsActivity),14);setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(44),dp(44)))
        root.addView(head)
        root.addView(TextView(this).apply{text="Search the current local QBank by subject or topic.";textSize=11.5f;setTextColor(ThemeManager.muted(this@StudyToolsActivity));setPadding(0,dp(2),0,dp(10))})
        val input=EditText(this).apply{hint="e.g. Pharmacology, Cardiology, Dermatology";textSize=16f;setSingleLine(true);setTextColor(ThemeManager.text(this@StudyToolsActivity));setHintTextColor(ThemeManager.muted(this@StudyToolsActivity));background=rounded(ThemeManager.elevated(this@StudyToolsActivity),14);setPadding(dp(14),dp(10),dp(14),dp(10))}
        root.addView(input,LinearLayout.LayoutParams(-1,dp(56)))
        val status=TextView(this).apply{text="Ready";textSize=11.5f;setTextColor(ThemeManager.muted(this@StudyToolsActivity));gravity=Gravity.CENTER_HORIZONTAL;setPadding(0,dp(5),0,0)}
        root.addView(status,LinearLayout.LayoutParams(-1,dp(28)))
        val find=TextView(this).apply{text="FIND QUESTIONS";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(ThemeManager.accent(this@StudyToolsActivity),14);setOnClickListener{
            val subject=input.text.toString().trim();if(subject.isBlank()){Toast.makeText(this@StudyToolsActivity,"Enter a subject or topic",Toast.LENGTH_SHORT).show();return@setOnClickListener}
            isEnabled=false;alpha=.72f;status.text="Searching local QBank index…"
            AppManagers.subjectFocus.search(subject,500){result -> runOnUiThread {
                if (dialog.window == null || !dialog.isShowing) return@runOnUiThread
                isEnabled=true;alpha=1f
                if(result.ids.isEmpty()){
                    status.text="No strong matches found"
                    Toast.makeText(this@StudyToolsActivity,AppManagers.studyIntelligence.explain(subject,0),Toast.LENGTH_LONG).show()
                } else {
                    status.text="Found ${result.matched} matching questions"
                    dialog.dismiss();chooseSubjectSize(subject,result.matched,result.ids)
                }
            }}
        }}
        root.addView(find,LinearLayout.LayoutParams(-1,dp(48)).apply{setMargins(0,dp(6),0,dp(4))})
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((resources.displayMetrics.widthPixels*.92f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT);input.requestFocus();dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);AdaptiveTypographyManager.apply(root)
    }

    private fun chooseSubjectSize(subject:String,matched:Int,ids:LongArray){
        val choices=listOf(20,50,100).filter{it<=ids.size}.map{n->Choice("$n questions","Build a focused set from $matched matching questions"){launchCollection(ids.toList().take(n).toLongArray(),"Subject • $subject",true)}}.toMutableList()
        choices.add(Choice("All $matched questions","Use every locally matched question"){launchCollection(ids,"Subject • $subject",true)})
        visualSheet("$matched questions matched","Choose the size of your focused set.",choices)
        saveSubjectSet(subject,ids)
    }

    private data class SavedSet(val subject:String,val ids:LongArray)
    private fun savedSets():List<SavedSet>{
        val raw=getSharedPreferences("study_sets",MODE_PRIVATE).getStringSet("sets",emptySet()) ?: emptySet()
        return raw.mapNotNull { line -> val p=line.indexOf('|'); if(p<=0)return@mapNotNull null; val subject=line.substring(0,p); val ids=line.substring(p+1).split(',').mapNotNull{it.toLongOrNull()}.distinct().toLongArray(); if(ids.isEmpty())null else SavedSet(subject,ids) }.sortedBy{it.subject.lowercase()}
    }
    private fun saveSubjectSet(subject:String,ids:LongArray){
        val prefs=getSharedPreferences("study_sets",MODE_PRIVATE); val set=prefs.getStringSet("sets",emptySet())?.toMutableSet() ?: mutableSetOf(); val prefix="$subject|"; set.removeAll{it.startsWith(prefix)}; set.add("$subject|${ids.joinToString(",")}"); prefs.edit().putStringSet("sets",set).apply()
    }
    private fun savedSubjectSetsDialog(){
        val sets=savedSets()
        if(sets.isEmpty()){visualSheet("Saved Subject Sets","Nothing saved yet. Create a set from Subject Focus.",listOf(Choice("Open Subject Focus","Create your first saved subject set"){subjectFocusDialog()}));return}
        visualSheet("Saved Subject Sets","Your locally saved focused question pools.",sets.map{set->Choice("${set.subject}  •  ${set.ids.size} questions","Open this saved set"){launchCollection(set.ids,"Subject • ${set.subject}",true)}}+listOf(Choice("Manage saved sets","Remove an old subject set"){manageSavedSets()}))
    }
    private fun manageSavedSets(){
        val sets=savedSets();visualSheet("Manage saved sets","Choose a set to remove.",sets.map{target->Choice(target.subject,"${target.ids.size} questions"){val prefs=getSharedPreferences("study_sets",MODE_PRIVATE);val raw=prefs.getStringSet("sets",emptySet())?.toMutableSet()?:mutableSetOf();raw.removeAll{it.startsWith(target.subject+"|")};prefs.edit().putStringSet("sets",raw).apply();Toast.makeText(this,"Removed ${target.subject}",Toast.LENGTH_SHORT).show()}})
    }

    private fun customDialog(){
        val options=arrayOf("Saved Subject Sets","All questions","Unsolved","Wrong","Solved","Bookmarked","Important","Revise","Doubt","Favourite")
        visualSheet("Custom Test","Choose the question pool before selecting its size.",options.mapIndexed{i,label->Choice(label,if(i==0)"Use one of your saved subject pools"else "Build a custom test from this pool"){if(i==0)savedSubjectSetsDialog()else chooseCustomSize(i-1)}})
    }
    private fun chooseCustomSize(poolIndex:Int){
        val counts=arrayOf(10,20,50,100,200);visualSheet("Number of questions","Select the size of this custom test.",counts.map{n->Choice("$n questions","Randomly sample from the selected pool"){val ids=pool(poolIndex).toList().shuffled().take(n).toLongArray();if(ids.isEmpty())Toast.makeText(this,"No questions in this pool",Toast.LENGTH_SHORT).show()else launchCollection(ids,"Custom Test • $n",true)}})
    }
    private fun pool(which:Int):List<Long>{
        val p=PerformanceManager.progress(applicationContext)
        return PerformanceManager.lightRefs(applicationContext).filter{r->when(which){0->true;1->p.record(r.stableKey)?.status==null;2->p.record(r.stableKey)?.status=="wrong";3->p.record(r.stableKey)?.status=="correct";4->!p.record(r.stableKey)?.bookmark.isNullOrBlank();5->p.record(r.stableKey)?.bookmark=="important";6->p.record(r.stableKey)?.bookmark=="revise";7->p.record(r.stableKey)?.bookmark=="doubt";8->{val b=p.record(r.stableKey)?.bookmark;b=="favorite"||b=="favourite"};else->true}}.map{it.id}
    }
    private fun examDialog(){
        val counts=arrayOf(20,50,100,200);visualSheet("Exam Mode","Timed blocks use the current local QBank and submit once at the end.",counts.map{n->Choice("$n questions","Approximately ${(n*1.2).roundToInt().coerceAtLeast(10)} minutes"){val ids=PerformanceManager.lightRefs(applicationContext).shuffled().take(n).map{it.id};val mins=(n*1.2).roundToInt().coerceAtLeast(10);startActivity(Intent(this,QuizActivity::class.java).putExtra("collectionMode",true).putExtra("sessionIds",ids.joinToString(",")).putExtra("sessionLabel","Exam • $n Q").putExtra("examMode",true).putExtra("examDurationMinutes",mins).putExtra("title","Exam Mode"))}})
    }

    override fun onDestroy(){db.close();super.onDestroy()}
}
