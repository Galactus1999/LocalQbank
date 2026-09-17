package com.localqbank.library

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TestListActivity:AppCompatActivity(){
    private lateinit var db:QBankDb
    private lateinit var store:ProgressStore
    private var sourceId=0L
    private lateinit var titleView:TextView
    private lateinit var metaView:TextView
    private lateinit var recycler:RecyclerView
    private var source:Source?=null

    override fun onCreate(b:Bundle?){
        super.onCreate(b);AppManagers.initialize(applicationContext);SystemUi.immersive(this);setContentView(makeRoot());db=QBankDb(this);store=ProgressStore(this)
        sourceId=intent.getLongExtra("sourceId",0)
        TransitionCoordinator.install(this)
        loadSections()
    }

    private fun loadSections(){
        titleView.text=intent.getStringExtra("sourceName")?:"Sections"
        metaView.text="Loading sections…"
        if (recycler.adapter == null || recycler.adapter is LoadingAdapter) recycler.adapter=LoadingAdapter(this)
        // Metadata + progress are loaded off the UI thread and cached together.
        AppManagers.qbankLoading.loadSectionBundle(sourceId) { bundle ->
            val tests=bundle.tests
            val summaries=bundle.summaries
            val src=bundle.source
            runOnUiThread {
                if(isFinishing||isDestroyed)return@runOnUiThread
                source=src
                titleView.text=src?.fileName ?: intent.getStringExtra("sourceName") ?: "Sections"
                val totalQuestions=tests.sumOf{it.count.coerceAtLeast(0)}
                metaView.text="${tests.size} subquestion bank${if(tests.size==1)""else"s"} • $totalQuestions questions${if(src?.seriesNumber.isNullOrBlank())""else" • Series ${src?.seriesNumber}"}"
                recycler.adapter=TestAdapter(tests,summaries,store,{t,pos,practice->
                    if(t.id.isNotBlank()) startActivity(Intent(this@TestListActivity,QuizActivity::class.java)
                        .putExtra("testId",t.id).putExtra("title",t.title).putExtra("position",pos)
                        .putExtra("practiceMode",practice))
                }, {t->showEditTestDialog(t)})
            }
        }
    }

    private fun showEditTestDialog(test:Test){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(4),dp(20),0)}
        val name=EditText(this).apply{setText(test.title);setSingleLine(true);hint="Subquestion bank name";setSelectAllOnFocus(false)}
        val series=EditText(this).apply{setText(test.seriesNumber);setSingleLine(true);hint="Series number (optional)";inputType=android.text.InputType.TYPE_CLASS_TEXT}
        box.addView(name,LinearLayout.LayoutParams(-1,dp(56)))
        box.addView(series,LinearLayout.LayoutParams(-1,dp(56)))
        val d=AlertDialog.Builder(this).setTitle("Edit subquestion bank").setView(box)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",null).create()
        d.setOnShowListener{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val ok=db.updateTestMetadata(test.id,name.text.toString(),series.text.toString())
                if(ok){AppManagers.qbankLoading.invalidate(sourceId);Toast.makeText(this,"Subquestion bank updated",Toast.LENGTH_SHORT).show();d.dismiss();loadSections()}
                else Toast.makeText(this,"Could not save the name. Use a non-empty name.",Toast.LENGTH_SHORT).show()
            }
        }
        d.show()
    }

    override fun onResume(){super.onResume();if(::db.isInitialized && ::recycler.isInitialized) loadSections()}
    override fun onDestroy(){if(::db.isInitialized) runCatching{db.close()};super.onDestroy()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun makeRoot():LinearLayout{
        val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.background=ThemeManager.backgroundDrawable(this)
        val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(5),dp(8),dp(2));background=ThemeManager.backgroundDrawable(this@TestListActivity)}
        val back=TextView(this);back.text="←";back.textSize=22f;back.setTextColor(ThemeManager.text(this@TestListActivity));back.gravity=Gravity.CENTER
        back.background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@TestListActivity));cornerRadius=12f*resources.displayMetrics.density};back.setOnClickListener{finish()}
        bar.addView(back,LinearLayout.LayoutParams(dp(44),dp(44)).apply{setMargins(0,0,dp(8),0)})
        titleView=TextView(this);titleView.setTextColor(ThemeManager.text(this));titleView.textSize=18f;titleView.setTypeface(null,1);titleView.maxLines=2;titleView.ellipsize=android.text.TextUtils.TruncateAt.END
        bar.addView(titleView,LinearLayout.LayoutParams(0,dp(44),1f));root.addView(bar)
        metaView=TextView(this);metaView.setTextColor(ThemeManager.muted(this));metaView.textSize=11.5f;metaView.setPadding(dp(14),0,dp(14),dp(8));root.addView(metaView)
        AdaptiveTypographyManager.apply(root)
        recycler=RecyclerView(this);recycler.layoutManager=LinearLayoutManager(this);AppManagers.runtime.configureRecyclerView(recycler, fixedSize=false);recycler.setPadding(dp(14),dp(2),dp(14),dp(20));recycler.clipToPadding=false;recycler.isNestedScrollingEnabled=true;recycler.itemAnimator=null;recycler.setHasFixedSize(false)
        root.addView(recycler,LinearLayout.LayoutParams(-1,0,1f));return root
    }
}

private class LoadingAdapter(private val context:android.content.Context):RecyclerView.Adapter<LoadingAdapter.VH>(){
    class VH(v:TextView):RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(p:ViewGroup,t:Int)=VH(TextView(context).apply{ text="Loading subquestion banks…\nReading section metadata only — question content is not being loaded yet.";textSize=14f;setTextColor(ThemeManager.muted(context));setPadding(0,dp(context,26),0,dp(context,26)) })
    override fun onBindViewHolder(h:VH,i:Int){}
    override fun getItemCount()=1
    private fun dp(c:android.content.Context,v:Int)=(v*c.resources.displayMetrics.density).toInt()
}

private fun dpForTest(context:android.content.Context,v:Int)=(v*context.resources.displayMetrics.density).toInt()

class TestAdapter(
    private val items:List<Test>,
    private val summaries:Map<String,ProgressSummary>,
    private val store:ProgressStore,
    private val click:(Test,Int,Boolean)->Unit,
    private val edit:(Test)->Unit
):RecyclerView.Adapter<TestAdapter.VH>(){
    class VH(v:LinearLayout):RecyclerView.ViewHolder(v){
        val t=TextView(v.context);val s=TextView(v.context);val actions=LinearLayout(v.context);val resume=TextView(v.context);val practice=TextView(v.context)
        init{
            v.orientation=LinearLayout.HORIZONTAL;v.setPadding(16,12,12,12)
            val body=LinearLayout(v.context).apply{orientation=LinearLayout.VERTICAL}
            t.textSize=16f;t.setTextColor(ThemeManager.text(v.context));t.setTypeface(null,1);t.maxLines=2;t.ellipsize=android.text.TextUtils.TruncateAt.END
            s.textSize=12f;s.setTextColor(ThemeManager.muted(v.context));s.setPadding(0,6,0,0);body.addView(t);body.addView(s)
            v.addView(body,LinearLayout.LayoutParams(0,-2,1f))
            actions.orientation=LinearLayout.VERTICAL;actions.gravity=Gravity.CENTER_HORIZONTAL
            fun style(b:TextView,label:String){b.text=label;b.textSize=10.5f;b.setTypeface(null,1);b.gravity=Gravity.CENTER;b.setPadding(8,0,8,0);b.minHeight=dp(v.context,38);b.background=GradientDrawable().apply{setColor(if(ThemeManager.get(v.context)==ThemeManager.AMOLED) Color.BLACK else if(ThemeManager.isDark(v.context)) Color.rgb(24,52,72) else Color.rgb(225,243,246));cornerRadius=11f*v.resources.displayMetrics.density}}
            style(resume,"RESUME");style(practice,"SOLVE AGAIN")
            actions.addView(resume,LinearLayout.LayoutParams(92,38));actions.addView(practice,LinearLayout.LayoutParams(92,38).apply{setMargins(0,6,0,0)})
            v.addView(actions,LinearLayout.LayoutParams(92,-2).apply{gravity=Gravity.CENTER_VERTICAL;setMargins(8,0,0,0)})
        }
        companion object{private fun dp(c:android.content.Context,v:Int)=(v*c.resources.displayMetrics.density).toInt()}
    }
    override fun onCreateViewHolder(p:ViewGroup,t:Int):VH{val v=LinearLayout(p.context);v.layoutParams=RecyclerView.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)};return VH(v)}
    override fun onBindViewHolder(h:VH,i:Int){
        val x=items[i]
        val pr=summaries[x.id] ?: ProgressSummary(x.count,0,0,0)
        val presentation=TestListPresentation.present(i,x,pr,store.position(x.id))
        h.t.text=presentation.title;h.t.setTextColor(ThemeManager.text(h.itemView.context));h.s.setTextColor(ThemeManager.muted(h.itemView.context))
        h.s.text=presentation.subtitle
        h.resume.visibility=if(presentation.showActions)View.VISIBLE else View.GONE;h.practice.visibility=if(presentation.showActions)View.VISIBLE else View.GONE
        val resumePos=presentation.resumePosition
        h.resume.setOnClickListener{click(x,resumePos,false)};h.practice.setOnClickListener{click(x,0,true)};h.itemView.setOnClickListener{click(x,resumePos,false)}
        h.itemView.setOnLongClickListener{edit(x);true}
        val ctx=h.itemView.context
        val dark=ThemeManager.isDark(ctx)
        val inProgress=presentation.inProgress
        val bg=when {
            ThemeManager.get(ctx)==ThemeManager.AMOLED -> if(inProgress) Color.rgb(49,43,20) else Color.BLACK
            inProgress -> if(dark) ThemeManager.pastelYellowFill(ctx) else Color.rgb(255,245,205)
            dark -> Color.rgb(24,32,42)
            else -> when(i%5){0->Color.rgb(235,245,255);1->Color.rgb(255,237,241);2->Color.rgb(255,232,229);3->Color.rgb(246,239,255);else->Color.rgb(255,245,228)}
        }
        h.itemView.background=GradientDrawable().apply{
            setColor(bg);cornerRadius=18f*ctx.resources.displayMetrics.density
            setStroke(dpForTest(ctx,1),if(inProgress) ThemeManager.pastelYellowText(ctx) else Color.argb(if(dark)55 else 35,100,120,140))
        }
        if(inProgress){
            h.t.setTextColor(if(dark)ThemeManager.pastelYellowText(ctx) else Color.rgb(91,68,0))
            h.s.setTextColor(if(dark)ThemeManager.pastelYellowText(ctx) else Color.rgb(111,80,0))
        }
    }
    override fun getItemCount()=items.size
}
