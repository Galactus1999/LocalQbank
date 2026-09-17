package com.localqbank.library

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicBoolean

class CollectionActivity : AppCompatActivity() {
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()

    private var filterType = "status"
    private var filterValue = "unsolved"
    private val refreshInFlight = AtomicBoolean(false)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        SystemUi.immersive(this)
        filterType = intent.getStringExtra("filterType") ?: "status"
        filterValue = intent.getStringExtra("filterValue") ?: "unsolved"
        setContentView(buildLoading(filterType, filterValue))
        TransitionCoordinator.install(this)
        refreshCollection()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && !isDestroyed) refreshCollection()
    }

    private fun refreshCollection() {
        if (!refreshInFlight.compareAndSet(false, true)) return
        PerformanceManager.submit {
            try {
                val progress=PerformanceManager.progress(applicationContext)
                val db=QBankDb(this)
                val refs=try {
                    val targetedKeys = progress.all().asSequence().filter { (_, record) ->
                        when (filterType) {
                            "bookmark" -> if (filterValue == "all") !record.bookmark.isNullOrBlank() else record.bookmark == filterValue
                            "status" -> filterValue != "unsolved" && record.status == filterValue
                            else -> false
                        }
                    }.map { it.key }.toList()
                    when {
                        targetedKeys.isNotEmpty() -> db.questionRefsForStableKeys(targetedKeys)
                        filterType == "status" && filterValue == "unsolved" -> PerformanceManager.lightRefs(applicationContext).filter { progress.record(it.stableKey)?.status == null }.let { light -> db.questionRefsForStableKeys(light.map { it.stableKey }) }
                        else -> emptyList()
                    }
                } finally { db.close() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) { refreshInFlight.set(false); return@runOnUiThread }
                    val root=makeRoot(title(filterType, filterValue), refs, filterType, filterValue)
                    setContentView(root)
                    applyThemeText(root, this@CollectionActivity)
                    refreshInFlight.set(false)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    refreshInFlight.set(false)
                    if (!isFinishing && !isDestroyed) Toast.makeText(this, "Could not load this collection. Your QBank data is safe.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildLoading(type:String,value:String):LinearLayout {
        return LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=android.view.Gravity.CENTER
            background=ThemeManager.backgroundDrawable(this@CollectionActivity)
            addView(TextView(this@CollectionActivity).apply {
                text="Loading ${title(type,value)}…"
                textSize=16f
                setTextColor(ThemeManager.text(this@CollectionActivity))
            })
        }
    }

    private fun applyThemeText(v: View, context: android.content.Context){
        if(v is TextView && v !is Button) v.setTextColor(ThemeManager.text(context))
        if(v is ViewGroup) for(i in 0 until v.childCount) applyThemeText(v.getChildAt(i), context)
    }

    private fun title(type: String, v: String) = if (type == "bookmark") {
        if (v == "all") "All Bookmarked" else v.replaceFirstChar { it.uppercase() }
    } else v.replaceFirstChar { it.uppercase() }

    private fun makeRoot(t: String, items: List<QuestionRef>, filterType: String, filterValue: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background=ThemeManager.backgroundDrawable(this@CollectionActivity)
        }
        val barRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(5), dp(8), dp(5)); background=ThemeManager.backgroundDrawable(this@CollectionActivity) }
        val back = TextView(this).apply { text="←"; textSize=22f; setTextColor(ThemeManager.text(this@CollectionActivity)); gravity=android.view.Gravity.CENTER; background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@CollectionActivity));cornerRadius=12f*resources.displayMetrics.density}; setOnClickListener{finish()} }
        barRow.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply{setMargins(0,0,dp(8),0)})
        val bar = RovexWaveTextView(this).apply {
            text = "$t  •  ${items.size}"
            textSize = 18f
            setTextColor(ThemeManager.text(this@CollectionActivity))
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER_VERTICAL
            background=ThemeManager.backgroundDrawable(this@CollectionActivity)
        }

        barRow.addView(bar, LinearLayout.LayoutParams(0,-1,1f)); root.addView(barRow)
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@CollectionActivity)
            AppManagers.runtime.configureRecyclerView(this, fixedSize=false)
            adapter = RefAdapter(grouped(items)) { r ->
                if (r.id > 0L) startActivity(Intent(this@CollectionActivity, QuizActivity::class.java)
                    .putExtra("testId", r.testId)
                    .putExtra("questionId", r.id)
                    .putExtra("title", t)
                    .putExtra("position", r.position)
                    .putExtra("sectionLabel", t)
                    .putExtra("practiceMode", true)
                    .putExtra("collectionMode", true)
                    .putExtra("collectionFilterType", filterType)
                    .putExtra("collectionFilterValue", filterValue))
            }
        }
        root.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))
        AdaptiveTypographyManager.apply(root)
        return root
    }

    private fun grouped(items: List<QuestionRef>): List<CollectionRow> {
        val out = mutableListOf<CollectionRow>()
        var last = ""
        items.forEach { item ->
            if (item.category != last) {
                last = item.category
                out.add(CollectionRow.Header(item.category))
            }
            out.add(CollectionRow.Question(item))
        }
        return out
    }

    override fun onDestroy() { super.onDestroy() }
}

sealed class CollectionRow {
    data class Header(val name: String) : CollectionRow()
    data class Question(val ref: QuestionRef) : CollectionRow()
}

class RefAdapter(private val items: List<CollectionRow>, private val click: (QuestionRef) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private companion object { const val HEADER = 0; const val QUESTION = 1 }

    override fun getItemViewType(position: Int) = if (items[position] is CollectionRow.Header) HEADER else QUESTION

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        if (type == HEADER) {
            val v = TextView(parent.context).apply {
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ThemeManager.accent(parent.context))
                setPadding(13, 8, 13, 8)
                background = GradientDrawable().apply {
                    setColor(if(ThemeManager.get(parent.context)==ThemeManager.AMOLED) Color.BLACK else if(ThemeManager.isDark(parent.context)) Color.rgb(25,43,61) else Color.rgb(224,237,248))
                    cornerRadius = 18f
                }
            }
            return HeaderVH(v)
        }
        val v = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundColor(ThemeManager.elevated(parent.context))
        }
        val t = TextView(parent.context).apply { textSize = 15f; setTextColor(ThemeManager.text(parent.context)) }
        val s = TextView(parent.context).apply { textSize = 12f; setTextColor(ThemeManager.muted(parent.context)); setPadding(0, 6, 0, 0) }
        v.addView(t); v.addView(s)
        return QuestionVH(v, t, s)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is CollectionRow.Header -> (holder as HeaderVH).v.text = row.name
            is CollectionRow.Question -> {
                val h = holder as QuestionVH
                val x = row.ref
                h.t.text = "Q${x.position + 1}. ${android.text.Html.fromHtml(x.text, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim().take(180)}"
                h.s.text = "${x.testTitle} • ${x.sourceName}"
                h.itemView.setOnClickListener { click(x) }
            }
        }
    }

    override fun getItemCount() = items.size
    class HeaderVH(val v: TextView) : RecyclerView.ViewHolder(v)
    class QuestionVH(v: LinearLayout, val t: TextView, val s: TextView) : RecyclerView.ViewHolder(v) {
        init { v.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(10, 6, 10, 6) } }
    }

}
