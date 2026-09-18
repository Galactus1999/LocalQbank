package com.localqbank.library

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.app.Dialog
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

class SourceAdapter(
    private var items:List<Source>,
    private var resumeBySource:Map<Long, MainRepository.ResumeTarget>,
    private var progressBySource:Map<Long, ProgressSummary>,
    private val click:(Source)->Unit,
    private val edit:(Source)->Unit,
    private val delete:(Long)->Unit
):RecyclerView.Adapter<SourceAdapter.VH>(){
    class VH(v:LinearLayout):RecyclerView.ViewHolder(v){
        val title=TextView(v.context); val sub=TextView(v.context); val resume=TextView(v.context)
        init{
            v.orientation=LinearLayout.HORIZONTAL;v.setPadding(16,11,12,11)
            val body=LinearLayout(v.context).apply{orientation=LinearLayout.VERTICAL}
            title.textSize=16f; title.setTextColor(ThemeManager.text(v.context)); title.setTypeface(null,1); title.maxLines=2; title.ellipsize=android.text.TextUtils.TruncateAt.END
            sub.textSize=12f; sub.setTextColor(ThemeManager.muted(v.context)); sub.setPadding(0,5,0,0); body.addView(title); body.addView(sub)
            v.addView(body,LinearLayout.LayoutParams(0,-2,1f))
            resume.text="RESUME";resume.textSize=11f;resume.setTypeface(null,1);resume.setTextColor(Color.rgb(25,91,111));resume.gravity=Gravity.CENTER;resume.background=GradientDrawable().apply{setColor(Color.rgb(225,243,246));cornerRadius=12f*v.resources.displayMetrics.density};resume.setPadding(10,0,10,0);v.addView(resume,LinearLayout.LayoutParams(82,42).apply{gravity=Gravity.CENTER_VERTICAL;setMargins(8,0,0,0)})
        }
    }
    override fun onCreateViewHolder(p:ViewGroup,t:Int):VH{
        val v=LinearLayout(p.context);
        v.layoutParams=RecyclerView.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)}
        return VH(v)
    }
    override fun onBindViewHolder(h:VH,i:Int){
        val x=items[i]
        val pr=progressBySource[x.id]
        h.title.text="${i + 1}. ${x.fileName}"
        h.sub.text=when {
            pr == null -> "Loading progress…"
            pr.total==0 -> "No questions"
            pr.solved==0 -> "Not started  •  ${pr.total} questions"
            pr.solved>=pr.total -> "✓ Completed  •  ${pr.solved}/${pr.total} solved"
            else -> "↻ In progress  •  ${pr.solved}/${pr.total} solved"
        }
        val resumeTarget=resumeBySource[x.id]
        val resumeTestId=resumeTarget?.testId.orEmpty()
        val resumePosition=resumeTarget?.position ?: 0
        h.resume.visibility=if(resumeTestId.isNotBlank())View.VISIBLE else View.GONE
        h.resume.setOnClickListener{
            if(resumeTestId.isNotBlank()) clickSourceResume(h.itemView.context,resumeTestId,resumePosition,x.fileName)
        }
        val ctx=h.itemView.context
        val dark=ThemeManager.isDark(ctx)
        val bg=if(ThemeManager.get(ctx)==ThemeManager.AMOLED) Color.BLACK else if(dark) when(i%5){0->Color.rgb(24,52,78);1->Color.rgb(62,37,51);2->Color.rgb(68,31,38);3->Color.rgb(52,43,72);else->Color.rgb(63,52,22)} else when(i%5){0->Color.rgb(235,245,255);1->Color.rgb(255,237,241);2->Color.rgb(255,232,229);3->Color.rgb(246,239,255);else->Color.rgb(255,245,228)}
        h.title.setTextColor(if(dark) ThemeManager.text(ctx) else Color.rgb(35,52,70))
        h.sub.setTextColor(if(dark) ThemeManager.muted(ctx) else Color.rgb(80,94,110))
        h.resume.setTextColor(ThemeManager.pastelBlueText(ctx))
        h.resume.background=GradientDrawable().apply{setColor(ThemeManager.pastelBlueFill(ctx));cornerRadius=12f*h.itemView.resources.displayMetrics.density}
        h.itemView.background=UiDrawableUtils.roundedDrawable(h.itemView.context,bg,18f)
        h.itemView.setOnClickListener{click(x)}
        h.itemView.setOnLongClickListener{
            val ctx=h.itemView.context
            val dialog=Dialog(ctx)
            val box=LinearLayout(ctx).apply{orientation=LinearLayout.VERTICAL;setPadding(18.dp(ctx),16.dp(ctx),18.dp(ctx),16.dp(ctx));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(ctx));cornerRadius=24f*ctx.resources.displayMetrics.density}}
            box.addView(TextView(ctx).apply{text=x.fileName;textSize=19f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(ctx));maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
            box.addView(TextView(ctx).apply{text="QBank actions";textSize=11.5f;setTextColor(ThemeManager.muted(ctx));setPadding(0,5.dp(ctx),0,12.dp(ctx))})
            fun action(label:String,fill:Int,fg:Int,click:()->Unit)=TextView(ctx).apply{text=label;textSize=13f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(fg);background=GradientDrawable().apply{setColor(fill);cornerRadius=14f*ctx.resources.displayMetrics.density};setOnClickListener{click();dialog.dismiss()}}
            box.addView(action("EDIT QBANK / SERIES",ThemeManager.pastelBlueFill(ctx),ThemeManager.pastelBlueText(ctx)){edit(x)},LinearLayout.LayoutParams(-1,46.dp(ctx)))
            box.addView(action("DELETE QBANK",ThemeManager.pastelRedFill(ctx),ThemeManager.pastelRedText(ctx)){delete(x.id)},LinearLayout.LayoutParams(-1,46.dp(ctx)).apply{setMargins(0,8.dp(ctx),0,0)})
            dialog.setContentView(box);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((ctx.resources.displayMetrics.widthPixels*.90f).toInt(),-2); true
        }
    }
    private fun clickSourceResume(c:android.content.Context,testId:String,pos:Int,title:String){c.startActivity(Intent(c,QuizActivity::class.java).putExtra("testId",testId).putExtra("title",title).putExtra("position",pos).putExtra("sessionLabel",title))}
    fun updateItems(newItems: List<Source>, newProgress: Map<Long, ProgressSummary> = progressBySource, newResumeBySource: Map<Long, MainRepository.ResumeTarget> = resumeBySource) {
        items = newItems
        progressBySource = newProgress
        resumeBySource = newResumeBySource
        notifyDataSetChanged()
    }
    fun refresh(){notifyDataSetChanged()}
    override fun getItemCount()=items.size
}
