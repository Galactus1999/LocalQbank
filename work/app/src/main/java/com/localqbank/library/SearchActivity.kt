package com.localqbank.library

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class SearchActivity: AppCompatActivity() {
    private lateinit var db: QBankDb
    private lateinit var store: ProgressStore
    private lateinit var searchBox: EditText
    private lateinit var results: RecyclerView
    private lateinit var empty: TextView
    private lateinit var countText: TextView
    private val rows = mutableListOf<SearchRow>()
    private val executor = LifecycleExecutor(Executors.newSingleThreadExecutor { r -> Thread(r, "qbank-search") })
    private val generation = AtomicLong(0)
    private var filter = "all"
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        SystemUi.immersive(this)
        db=QBankDb(this); store=ProgressStore(this)
        setContentView(build())
        TransitionCoordinator.install(this)
        searchBox.requestFocus()
        searchBox.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchBox,InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun build():View {
        val dark=ThemeManager.isDark(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=ThemeManager.backgroundDrawable(this@SearchActivity)}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(Color.rgb(26,70,99))}
        val back=TextView(this).apply{
            text="←";textSize=23f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            background=rounded(if(dark)Color.rgb(31,43,58) else Color.rgb(28,70,103),12f)
            setOnClickListener{finish()}
            contentDescription="Back"
        }
        bar.addView(back,LinearLayout.LayoutParams(dp(56),dp(56)).apply{setMargins(0,0,dp(8),0)})
        val title=TextView(this).apply{text="Search QBank";textSize=20f;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE)}
        bar.addView(title,LinearLayout.LayoutParams(0,-2,1f));root.addView(bar)

        val input=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(5))}
        searchBox=EditText(this).apply{
            hint="Search questions, sections, QBanks…";textSize=16f;setSingleLine(true)
            setTextColor(ThemeManager.text(this@SearchActivity));setHintTextColor(ThemeManager.muted(this@SearchActivity))
            setBackground(rounded(ThemeManager.elevated(this@SearchActivity),16f));setPadding(dp(14),0,dp(10),0)
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        input.addView(searchBox,LinearLayout.LayoutParams(0,dp(52),1f));root.addView(input)
        val hint=TextView(this).apply{text="Type a topic, subsection, question, option, explanation or QBank name";textSize=12f;setTextColor(ThemeManager.muted(this@SearchActivity));setPadding(dp(16),0,dp(16),dp(5))};root.addView(hint)

        val chipScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;setPadding(dp(10),dp(3),dp(10),dp(5))}
        val chips=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val filters=listOf("all" to "All","correct" to "Solved","unsolved" to "Unsolved","wrong" to "Wrong","bookmark" to "Bookmarked","important" to "Important","revise" to "Revise","doubt" to "Doubt","favorite" to "Favourite")
        filters.forEach{(key,label)->
            val chip=TextView(this).apply{text=label;textSize=12f;gravity=Gravity.CENTER;setPadding(dp(14),0,dp(14),0);minHeight=dp(38);setTypeface(null,Typeface.BOLD);isClickable=true}
            chip.setOnClickListener{filter=key;styleChipRow(chips);runSearch(searchBox.text.toString())}
            chip.tag=key;chips.addView(chip,LinearLayout.LayoutParams(-2,dp(38)).apply{setMargins(0,0,dp(6),0)})
        }
        chipScroll.addView(chips);root.addView(chipScroll);styleChipRow(chips)

        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(2),dp(16),dp(4))}
        countText=TextView(this).apply{text="";textSize=12f;setTextColor(ThemeManager.muted(this@SearchActivity))};row.addView(countText,LinearLayout.LayoutParams(0,-2,1f))
        val clear=TextView(this).apply{text="Clear";textSize=12f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@SearchActivity));setOnClickListener{searchBox.text.clear();searchBox.requestFocus()}}
        row.addView(clear);root.addView(row)

        results=RecyclerView(this).apply{AppManagers.runtime.configureRecyclerView(this, fixedSize=false);layoutManager=LinearLayoutManager(this@SearchActivity);adapter=SearchAdapter(rows){open(it)};itemAnimator=null;setHasFixedSize(false);overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
        root.addView(results,LinearLayout.LayoutParams(-1,0,1f))
        empty=TextView(this).apply{text="Start typing to search";textSize=16f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@SearchActivity))};root.addView(empty,LinearLayout.LayoutParams(-1,0,1f));results.visibility=View.GONE

        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;weightSum=2f;setPadding(dp(7),dp(7),dp(7),dp(7));setBackgroundColor(Color.rgb(26,70,99))}
        val dash=navButton("⌂  Dashboard",false);dash.setOnClickListener{finish()}
        val search=navButton("⌕  Search",true)
        nav.addView(dash,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(0,0,dp(4),0)});nav.addView(search,LinearLayout.LayoutParams(0,dp(50),1f).apply{setMargins(dp(4),0,0,0)});root.addView(nav)

        searchBox.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
            override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){scheduleSearch(s?.toString() ?: "")}
            override fun afterTextChanged(e:android.text.Editable?){}
        })
        return root
    }

    private fun navButton(text:String,selected:Boolean)=TextView(this).apply{
        this.text=text;textSize=13f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setTextColor(if(selected)ThemeManager.accent(this@SearchActivity) else Color.WHITE)
        background=rounded(if(selected)ThemeManager.elevated(this@SearchActivity) else Color.rgb(43,82,111),12f)
    }

    private fun styleChipRow(chips:LinearLayout){
        for(i in 0 until chips.childCount){val v=chips.getChildAt(i) as TextView;val selected=v.tag==filter;v.setTextColor(if(selected)Color.WHITE else ThemeManager.text(this));v.background=rounded(if(selected)ThemeManager.accent(this) else ThemeManager.elevated(this),18f)}
    }

    private fun scheduleSearch(q:String){
        val serial=generation.incrementAndGet()
        android.os.Handler(mainLooper).postDelayed({if(generation.get()==serial) runSearch(q)},230)
    }

    private fun runSearch(q:String){
        val text=q.trim();val serial=generation.get()
        if(text.isBlank()){rows.clear();results.visibility=View.GONE;empty.visibility=View.VISIBLE;empty.text="Start typing to search";countText.text="";return}
        executor.execute {
            try {
                db.ensureSearchIndex()
                val found=mutableListOf<SearchRow>()
                val progress = PerformanceManager.progress(applicationContext)
                // Search the hierarchy first so QBank and sub-QBank destinations are discoverable,
                // not just questions. A section row is a test/sub-test in the imported corpus.
                db.searchSections(text).take(40).forEach{section->found.add(SearchRow.Section(section))}
                db.search(text, 250).forEach{hit->if(matchesFilter(hit, progress))found.add(SearchRow.Question(hit))}
                runOnUiThread{
                    if(isFinishing || serial!=generation.get())return@runOnUiThread
                    rows.clear();rows.addAll(found);results.visibility=if(rows.isEmpty())View.GONE else View.VISIBLE;empty.visibility=if(rows.isEmpty())View.VISIBLE else View.GONE;empty.text=if(rows.isEmpty())"No matching results" else "";countText.text=if(rows.isEmpty())"" else "${rows.size} results";results.adapter?.notifyDataSetChanged()
                }
            }catch(_:Exception){
                runOnUiThread{if(!isFinishing&&serial==generation.get()){rows.clear();results.visibility=View.GONE;empty.visibility=View.VISIBLE;empty.text="Search unavailable for this query. Try fewer words.";countText.text=""}}
            }
        }
    }

    private fun matchesFilter(h:SearchHit, progress: ProgressSnapshot):Boolean {
        val p = progress.record(h.stableKey)
        return when(filter){
            "all"->true
            "correct"->p?.status=="correct"
            "wrong"->p?.status=="wrong"
            "unsolved"->p?.status.isNullOrBlank()
            "bookmark"->!p?.bookmark.isNullOrBlank()
            else->p?.bookmark==filter
        }
    }

    private fun open(row:SearchRow){
        when(row){
            is SearchRow.Section -> if(row.x.testId.isNotBlank() && db.testById(row.x.testId)!=null){
                startActivity(Intent(this,QuizActivity::class.java)
                    .putExtra("testId",row.x.testId)
                    .putExtra("title",row.x.title)
                    .putExtra("position",0)
                    .putExtra("sectionLabel",row.x.path.ifBlank{row.x.title})
                    .putExtra("practiceMode",true))
            }
            is SearchRow.Question -> {
                val hit=row.x
                if(hit.testId.isNotBlank() && db.testById(hit.testId)!=null) startActivity(Intent(this,QuizActivity::class.java)
                    .putExtra("testId",hit.testId).putExtra("questionId",hit.id).putExtra("title",hit.testTitle)
                    .putExtra("position",hit.position).putExtra("sectionLabel",hit.path ?: hit.testTitle).putExtra("practiceMode",true))
            }
        }
    }
    private fun rounded(c:Int,r:Float)=GradientDrawable().apply{setColor(c);cornerRadius=r*resources.displayMetrics.density}
    override fun onResume(){super.onResume();if(::searchBox.isInitialized)runSearch(searchBox.text.toString())}
    override fun onDestroy(){executor.close();db.close();super.onDestroy()}
}

sealed class SearchRow{data class Section(val x:SectionHit):SearchRow();data class Question(val x:SearchHit):SearchRow()}

class SearchAdapter(private val items:List<SearchRow>,private val click:(SearchRow)->Unit):RecyclerView.Adapter<SearchAdapter.VH>(){
    class VH(v:LinearLayout):RecyclerView.ViewHolder(v){val tag=TextView(v.context);val title=TextView(v.context);val sub=TextView(v.context);init{v.orientation=LinearLayout.VERTICAL;v.setPadding(16,14,16,14);v.addView(tag);v.addView(title);v.addView(sub)}}
    override fun onCreateViewHolder(p:ViewGroup,t:Int):VH{val v=LinearLayout(p.context);v.layoutParams=RecyclerView.LayoutParams(-1,-2).apply{setMargins(10,5,10,5)};return VH(v)}
    override fun onBindViewHolder(h:VH,i:Int){val row=items[i];val c=h.itemView.context;val dark=ThemeManager.isDark(c);h.itemView.background=GradientDrawable().apply{setColor(if(dark)ThemeManager.elevated(c) else Color.rgb(247,250,253));cornerRadius=16f*c.resources.displayMetrics.density};when(row){is SearchRow.Section->{h.tag.text=if(row.x.path.isBlank())"QBANK" else "SUB-QBANK";h.title.text=row.x.title;h.sub.text="${row.x.sourceName}  •  ${row.x.count} questions${if(row.x.path.isBlank())"" else "  •  "+row.x.path}"};is SearchRow.Question->{h.tag.text="QUESTION";h.title.text="Q${row.x.position+1}. "+Html.fromHtml(row.x.text,Html.FROM_HTML_MODE_LEGACY).toString().trim().take(220);h.sub.text="${row.x.sourceName}  •  ${row.x.testTitle}${if(row.x.path.isNullOrBlank())"" else "  •  "+row.x.path}"}};h.tag.textSize=11f;h.tag.setTypeface(null,Typeface.BOLD);h.tag.setTextColor(ThemeManager.accent(c));h.title.textSize=15f;h.title.setTextColor(ThemeManager.text(c));h.title.ellipsize=TextUtils.TruncateAt.END;h.sub.textSize=12f;h.sub.setTextColor(ThemeManager.muted(c));h.title.setPadding(0,4,0,0);h.sub.setPadding(0,5,0,0);h.itemView.setOnClickListener{click(row)}}
    override fun getItemCount()=items.size
}
