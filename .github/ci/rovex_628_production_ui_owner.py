#!/usr/bin/env python3
from pathlib import Path
import re,sys

OLD='versionName = "8.3.627"'
OLD_CODE='versionCode = 713'
NEW='versionName = "8.3.628"'
NEW_CODE='versionCode = 714'

SUBJECT=r'''private fun subjectCategoryBox(label:String,allItems:List<Row>,index:Int){
    val total=allItems.sumOf{it.total};val solved=allItems.sumOf{it.solved};val correct=allItems.sumOf{it.correct}
    val accuracy=if(solved==0)0 else correct*100/solved
    val mastery=if(total==0)0 else solved*100/total
    val fg=ThemeManager.pastelAccentText(this,index)
    val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(d(7),d(8),d(7),d(3))}
    val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14),d(14),d(12),d(12));background=RovexVisualSurfaceStyle.glass(this@RovexSectionDashboardActivity,20f,true);isClickable=true;isFocusable=true;RovexTouchFeedback.bind(this)}
    val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    head.addView(View(this).apply{background=android.graphics.drawable.GradientDrawable().apply{setColor(fg);cornerRadius=d(3).toFloat()}},LinearLayout.LayoutParams(d(4),d(42)).apply{rightMargin=d(11)})
    val identity=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    identity.addView(TextView(this@RovexSectionDashboardActivity).apply{text=label;textSize=16.5f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
    identity.addView(TextView(this@RovexSectionDashboardActivity).apply{text=allItems.size.toString()+" QBanks";textSize=9.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(3),0,0)})
    head.addView(identity,LinearLayout.LayoutParams(0,-2,1f))
    head.addView(TextView(this).apply{text=mastery.toString()+"%";textSize=13f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setTextColor(fg);background=android.graphics.drawable.GradientDrawable().apply{setColor(if(ThemeManager.isDark(this@RovexSectionDashboardActivity))0x20FFFFFF else 0x12000000);cornerRadius=d(12).toFloat()};setPadding(d(8),0,d(8),0)},LinearLayout.LayoutParams(d(58),d(34)).apply{leftMargin=d(8)})
    val arrow=TextView(this).apply{text="⌄";textSize=18f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))}
    head.addView(arrow,LinearLayout.LayoutParams(d(28),d(34)))
    shell.addView(head)
    val metrics=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(d(15),d(10),0,0)}
    fun metric(k:String,v:String){val m=LinearLayout(this@RovexSectionDashboardActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(d(8),d(6),d(8),d(6));background=android.graphics.drawable.GradientDrawable().apply{setColor(if(ThemeManager.isDark(this@RovexSectionDashboardActivity))0x18FFFFFF else 0x0F000000);cornerRadius=d(10).toFloat()}};m.addView(TextView(this@RovexSectionDashboardActivity).apply{text=v;textSize=12f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))});m.addView(TextView(this@RovexSectionDashboardActivity).apply{text=k;textSize=7.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))});metrics.addView(m,LinearLayout.LayoutParams(0,d(42),1f).apply{rightMargin=d(5)})}
    metric("QUESTIONS",total.toString());metric("SOLVED",solved.toString());metric("ACCURACY",accuracy.toString()+"%")
    shell.addView(metrics)
    shell.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=mastery;progressTintList=android.content.res.ColorStateList.valueOf(fg)},LinearLayout.LayoutParams(-1,d(5)).apply{topMargin=d(10);leftMargin=d(15);rightMargin=d(3)})
    shell.addView(TextView(this).apply{text="Tap to explore subject banks";textSize=9.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(d(15),d(7),0,0)})
    var populated=false
    head.setOnClickListener{
        val open=wrap.visibility!=View.VISIBLE
        if(open&&!populated){
            val kinds=focusKind?.let{listOf(it)}?:listOf("QBank","PYQ","Custom","Subject Test","Image Test","Other Test")
            kinds.forEachIndexed{ki,kind->
                val items=allItems.filter{it.kind==kind}
                wrap.addView(TextView(this).apply{text=kind.uppercase()+" • "+items.size+" QBANKS";textSize=9f;letterSpacing=.08f;setTypeface(null,Typeface.BOLD);setTextColor(fg);setPadding(d(5),d(9),0,d(5))})
                if(items.isEmpty())wrap.addView(TextView(this).apply{text="No imported items in this category";textSize=10f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(d(5),0,0,d(5))})
                else items.sortedWith(compareBy<Row>{it.position}.thenBy{it.name.lowercase()}).forEachIndexed{ci,r->progressInto(wrap,r,ci+ki)}
            }
            populated=true
        }
        wrap.visibility=if(open)View.VISIBLE else View.GONE
        arrow.text=if(open)"⌃" else "⌄"
    }
    shell.addView(wrap)
    renderTargetOrContent().addView(shell,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(8)})
}
'''

HERO=r'''private fun analyticsHero(rows:List<Row>){
    val total=rows.sumOf{it.total};val solved=rows.sumOf{it.solved};val correct=rows.sumOf{it.correct}
    val accuracy=if(solved==0)0 else correct*100/solved
    val mastery=if(total==0)0 else solved*100/total
    val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16),d(16),d(16),d(14));background=RovexVisualSurfaceStyle.glass(this@RovexSectionDashboardActivity,24f,true)}
    hero.addView(TextView(this).apply{text="MAIN QBANK";textSize=9f;letterSpacing=.16f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@RovexSectionDashboardActivity))})
    hero.addView(TextView(this).apply{text="Your study command center";textSize=21f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity));setPadding(0,d(4),0,0)})
    hero.addView(TextView(this).apply{text=total.toString()+" questions • live local progress";textSize=10.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(3),0,d(10))})
    val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    fun metric(k:String,v:String){val m=LinearLayout(this@RovexSectionDashboardActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(d(8),d(7),d(8),d(7));background=android.graphics.drawable.GradientDrawable().apply{setColor(if(ThemeManager.isDark(this@RovexSectionDashboardActivity))0x18FFFFFF else 0x0F000000);cornerRadius=d(11).toFloat()}};m.addView(TextView(this@RovexSectionDashboardActivity).apply{text=v;textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))});m.addView(TextView(this@RovexSectionDashboardActivity).apply{text=k;textSize=7.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))});row.addView(m,LinearLayout.LayoutParams(0,d(50),1f).apply{rightMargin=d(5)})}
    metric("SOLVED",solved.toString());metric("ACCURACY",accuracy.toString()+"%");metric("COVERAGE",mastery.toString()+"%")
    hero.addView(row);hero.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=mastery;progressTintList=android.content.res.ColorStateList.valueOf(ThemeManager.accent(this@RovexSectionDashboardActivity))},LinearLayout.LayoutParams(-1,d(5)).apply{topMargin=d(10)})
    hero.addView(TextView(this).apply{text="Coverage grows as you solve questions";textSize=9f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity));setPadding(0,d(6),0,0)})
    renderTargetOrContent().addView(hero,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
}
'''

def replace_fun(s,name,next_name,replacement):
    a=s.find("private fun "+name)
    b=s.find("private fun "+next_name,a)
    if a<0 or b<0: raise SystemExit("v8.3.628 missing "+name+" boundary")
    return s[:a]+replacement+s[b:]

def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_628_production_ui_owner.py <project>")
    root=Path(sys.argv[1]);gpath=root/"app/build.gradle.kts"
    g=gpath.read_text()
    if OLD not in g or OLD_CODE not in g: raise SystemExit("v8.3.628 wrong baseline")
    p=next(root.rglob("RovexSectionDashboardActivity.kt"))
    s=p.read_text()
    s=replace_fun(s,"subjectCategoryBox","unassignedCollectionBox",SUBJECT)
    s=replace_fun(s,"analyticsHero","tests",HERO)

    p.write_text(s)
    p=next(root.rglob("RenActivity.kt"));s=p.read_text()
    old='''val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(5))
            background = ThemeManager.backgroundDrawable(this@RenActivity)
        }'''
    new='''val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(8))
            background = ThemeManager.backgroundDrawable(this@RenActivity)
        }'''
    if old not in s: raise SystemExit("v8.3.628 Ren root anchor missing")
    s=s.replace(old,new,1)
    s=s.replace("topChrome.addView(header, LinearLayout.LayoutParams(-1, dp(32)))\n        root.addView(topChrome, LinearLayout.LayoutParams(-1, dp(32)))","header.background=RovexVisualSurfaceStyle.glass(this@RenActivity,18f,true)\n        header.setPadding(dp(7),dp(4),dp(5),dp(4))\n        topChrome.addView(header, LinearLayout.LayoutParams(-1, dp(44)).apply{bottomMargin=dp(4)})\n        root.addView(topChrome, LinearLayout.LayoutParams(-1, dp(48)))",1)
    s=s.replace("background = RovexVisualSurfaceStyle.glass(this@RenActivity, 18f, true)","background = RovexVisualSurfaceStyle.glass(this@RenActivity, 22f, true)\n            elevation=dp(2).toFloat()\n            setPadding(dp(4),dp(4),dp(4),dp(4))",1)
    s=s.replace("val bottomChrome = LinearLayout(this).apply {\n            orientation = LinearLayout.VERTICAL\n        }","val bottomChrome = LinearLayout(this).apply {\n            orientation = LinearLayout.VERTICAL\n            setPadding(dp(7),dp(7),dp(7),dp(6))\n            background=RovexVisualSurfaceStyle.glass(this@RenActivity,20f,true)\n        }",1)
    p.write_text(s)
    p=next(root.rglob("RovexVisualTruthCaptureTest.kt"));s=p.read_text()
    s=s.replace('''ActivityScenario.launch<MainQBankLibraryActivity>(
            Intent(context, MainQBankLibraryActivity::class.java)
        ).use {''','''ActivityScenario.launch<RovexSectionDashboardActivity>(
            Intent(context, RovexSectionDashboardActivity::class.java).putExtra("section","qbank")
        ).use {''',1)
    old_capture='''        shell("screencap -p /sdcard/RovexVisualTruth/$name.png")
        // The host-side collector validates the file after instrumentation.
        // Avoid making the test depend on shell-output timing for the screenshot file.'''
    new_capture='''        shell("screencap -p /sdcard/RovexVisualTruth/$name.png")
        shell("uiautomator dump /sdcard/RovexVisualTruth/$name-window.xml")
        // The host-side collector pulls these exact production-owner artifacts after instrumentation.'''
    if old_capture not in s: raise SystemExit("v8.3.628: capture function anchor missing")
    s=s.replace(old_capture,new_capture,1)
    p.write_text(s)
    g=g.replace(OLD,NEW,1).replace(OLD_CODE,NEW_CODE,1);gpath.write_text(g)
    print("v8.3.628 production UI owner structural repair: APPLIED")
if __name__=="__main__":main()
