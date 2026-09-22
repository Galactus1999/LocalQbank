from pathlib import Path
import sys
root=Path(sys.argv[1]); j=root/"app/src/main/java/com/localqbank/library"; m=root/"app/src/main/AndroidManifest.xml"
(j/"MainQBankLibraryActivity.kt").write_text(r'''package com.localqbank.library
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
class MainQBankLibraryActivity:Activity(){
 private lateinit var list:LinearLayout
 private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun bg(c:Int,r:Float=18f)=GradientDrawable().apply{setColor(c);cornerRadius=r*resources.displayMetrics.density}
 override fun onCreate(b:Bundle?){super.onCreate(b);SystemUi.immersive(this);shell();load()}
 private fun shell(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14),d(12),d(14),d(12));background=ThemeManager.backgroundDrawable(this@MainQBankLibraryActivity)}
  val h=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  h.addView(TextView(this).apply{text="Imported QBanks";textSize=24f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@MainQBankLibraryActivity))},LinearLayout.LayoutParams(0,d(52),1f))
  h.addView(TextView(this).apply{text="＋ IMPORT";textSize=11f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=bg(ThemeManager.accent(this@MainQBankLibraryActivity),14f);setPadding(d(12),0,d(12),0);setOnClickListener{startActivity(Intent(this@MainQBankLibraryActivity,HtmlImportActivity::class.java))}},LinearLayout.LayoutParams(d(100),d(44)))
  root.addView(h)
  root.addView(TextView(this).apply{text="Imported main QBanks • tap a bank to open its tests";textSize=12.5f;setTextColor(ThemeManager.muted(this@MainQBankLibraryActivity));setPadding(0,0,0,d(12))})
  val scroll=ScrollView(this).apply{overScrollMode=View.OVER_SCROLL_NEVER};list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(TextView(this).apply{text="← BACK";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@MainQBankLibraryActivity));background=bg(ThemeManager.elevated(this@MainQBankLibraryActivity),14f);setOnClickListener{finish()}},LinearLayout.LayoutParams(-1,d(46)).apply{topMargin=d(10)})
  setContentView(root)
 }
 private fun load(){
  list.removeAllViews();list.addView(TextView(this).apply{text="Loading imported QBanks…";textSize=14f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@MainQBankLibraryActivity));setPadding(0,d(24),0,d(24))})
  AppManagers.qbankLoading.loadSources{sources->runOnUiThread{
   if(isFinishing||isDestroyed)return@runOnUiThread;list.removeAllViews()
   if(sources.isEmpty()){list.addView(TextView(this).apply{text="No imported QBank yet.\\nUse IMPORT to add one.";textSize=16f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(this@MainQBankLibraryActivity));setPadding(d(20),d(50),d(20),d(50))});return@runOnUiThread}
   sources.forEachIndexed{i,s->
    val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16),d(14),d(16),d(14));background=bg(if(ThemeManager.isDark(this@MainQBankLibraryActivity))ThemeManager.elevated(this@MainQBankLibraryActivity) else ThemeManager.pastelBlueFill(this@MainQBankLibraryActivity));isClickable=true;isFocusable=true}
    card.addView(TextView(this).apply{text=(i+1).toString()+". "+s.fileName;textSize=17f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@MainQBankLibraryActivity));maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
    card.addView(TextView(this).apply{text=if(s.seriesNumber.isBlank())s.provider.ifBlank{"Imported QBank"} else s.provider.ifBlank{"Imported QBank"}+" • Series "+s.seriesNumber;textSize=12f;setTextColor(ThemeManager.muted(this@MainQBankLibraryActivity));setPadding(0,d(5),0,0)})
    card.addView(TextView(this).apply{text="OPEN QBANK  ›";textSize=11f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(this@MainQBankLibraryActivity));setPadding(0,d(10),0,0)})
    card.setOnClickListener{startActivity(Intent(this,TestListActivity::class.java).putExtra("sourceId",s.id).putExtra("sourceName",s.fileName))};list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(9)})
   }
  }}
 }
 override fun onResume(){super.onResume();if(::list.isInitialized&&!isFinishing&&!isDestroyed)load()}
}
''')

if ".MainQBankLibraryActivity" not in m.read_text():
    m.write_text(m.read_text().replace('<activity android:name=".RovexSectionDashboardActivity" android:exported="false" />','<activity android:name=".RovexSectionDashboardActivity" android:exported="false" />\n<activity android:name=".MainQBankLibraryActivity" android:exported="false" />'))

p=j/"RovexHomeRevolution.kt";s=p.read_text()
s=s.replace("1->a.openImportedQBankLibraryFromNav()","1->a.startActivity(Intent(a,MainQBankLibraryActivity::class.java))")
s=s.replace("val shell=FrameLayout(a);shell.background=RovexLiveBackdrop(a)","val shell=FrameLayout(a);shell.tag=\"ROVEX_HOME_SHELL\";shell.background=ThemeManager.backgroundDrawable(a)")
s=s.replace('val menu=tv(a,"☰",27f,Color.WHITE).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=1;setColor(Color.argb(60,255,255,255))}}\n  top.addView(menu,LinearLayout.LayoutParams(d(48,a),d(48,a)))\n  top.addView(tv(a,"Rovex",30f,Color.WHITE,true),LinearLayout.LayoutParams(0,d(48,a),1f).apply{leftMargin=d(10,a)})','top.addView(RovexHeaderCosmicView(a),LinearLayout.LayoutParams(d(54,a),d(44,a)).apply{rightMargin=d(6,a)})\n  top.addView(RovexWaveTextView(a).apply{text="Rovex";textSize=30f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(a));gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,d(48,a),1f).apply{leftMargin=d(4,a)})')
s=s.replace('g.addView(tv(a,"Good Evening,",16f,Color.argb(225,255,255,255),false))','g.addView(tv(a,"Good Evening,",16f,ThemeManager.muted(a),false))').replace('g.addView(tv(a,"Future Doctor 👋",31f,Color.WHITE,true)','g.addView(tv(a,"Future Doctor 👋",31f,ThemeManager.text(a),true)').replace('g.addView(tv(a,"Small steps. Big Doctor.",14f,Color.argb(220,255,255,255),false)','g.addView(tv(a,"Small steps. Big Doctor.",14f,ThemeManager.muted(a),false)').replace('val search=tv(a,"⌕   Search questions, topics, anything…     ✦",15f,Color.WHITE,false)','val search=tv(a,"⌕   Search questions, topics, anything…     ✦",15f,ThemeManager.text(a),false)')
if "fun refreshTheme(a:MainActivity)" not in s:s=s.replace("\n fun apply(a:MainActivity){","\n fun refreshTheme(a:MainActivity){val root=a.findViewById<ViewGroup>(R.id.dashboardRoot)?:return;val shell=root.findViewWithTag<FrameLayout>(\"ROVEX_HOME_SHELL\")?:return;shell.background=ThemeManager.backgroundDrawable(a);shell.invalidate()}\n\n fun apply(a:MainActivity){",1)
s=s.replace("scheduleSelf({invalidateSelf()},SystemClock.uptimeMillis()+55)","scheduleSelf({invalidateSelf()},SystemClock.uptimeMillis()+110)");p.write_text(s)

p=j/"MainActivity.kt";s=p.read_text().replace("        applyDashboardTheme()\n        setupRovexBottomNav()","        applyDashboardTheme()\n        RovexHomeRevolution.refreshTheme(this)\n        setupRovexBottomNav()",1);p.write_text(s)

p=j/"QuizViewModel.kt";s=p.read_text().replace("fun applyResolution(resolution: QuizNavigationUseCase.SessionResolution) {","fun applyResolution(resolution: QuizNavigationUseCase.SessionResolution, preferResolutionPosition: Boolean = false) {").replace("state.position = if (restoredPosition != null && savedState?.get<String>(SavedKeys.TEST_ID) == resolution.testId) {","state.position = if (!preferResolutionPosition && restoredPosition != null && savedState?.get<String>(SavedKeys.TEST_ID) == resolution.testId) {");p.write_text(s)
p=j/"QuizActivity.kt";s=p.read_text().replace("quizViewModel.applyResolution(resolution)","quizViewModel.applyResolution(resolution, preferResolutionPosition = exactQuestionId > 0L)",1);p.write_text(s)

p=j/"BenQuestionAiContextDialog.kt";s=p.read_text().replace(".table-wrap{width:100%;max-width:100%;overflow-x:auto;overflow-y:hidden;",".table-wrap{width:100%;max-width:100%;overflow-x:auto;overflow-y:hidden;touch-action:pan-x pan-y;").replace(".table-wrap table{width:auto;min-width:100%;max-width:none;",".table-wrap table{width:max-content;min-width:100%;max-width:none;");p.write_text(s)
# Home UX: dynamic daily progress, resume action, responsive quick tools, and theme refresh.
p=j/"RovexHomeRevolution.kt";s=p.read_text()
s=s.replace('setOnClickListener{a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","qbank").addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))}}','setOnClickListener{a.startActivity(Intent(a,MainQBankLibraryActivity::class.java))}}')
s=s.replace('val prog=LinearLayout(a);prog.orientation=LinearLayout.VERTICAL;prog.setPadding(d(18,a),d(16,a),d(18,a),d(16,a));prog.background=surface(a,Color.argb(105,35,180,255),Color.argb(85,122,63,255));prog.elevation=d(7,a).toFloat()','val prog=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16,a),d(13,a),d(16,a),d(13,a));background=surface(a,ThemeManager.peacockFill(a),ThemeManager.elevated(a));elevation=d(4,a).toFloat()}')
s=s.replace('prog.addView(tv(a,"68%",40f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(2,a)});prog.addView(tv(a,"QBank 32/50   •   Flashcards 12/30   •   Study 2.4 h   •   🔥 12 day streak",13f,Color.argb(235,255,255,255),false),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(2,a)})','val progPct=tv(a,"0%",36f,ThemeManager.text(a),true);val progDetail=tv(a,"Loading study data...",12.5f,ThemeManager.muted(a),false);prog.addView(progPct,LinearLayout.LayoutParams(-1,-2));prog.addView(progDetail,LinearLayout.LayoutParams(-1,-2));val dailyBar=ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=0;progressTintList=ColorStateList.valueOf(ThemeManager.accent(a));progressBackgroundTintList=ColorStateList.valueOf(ThemeManager.elevated(a))};prog.addView(dailyBar,LinearLayout.LayoutParams(-1,d(7,a)).apply{topMargin=d(8,a)});val continueBtn=tv(a,"CONTINUE  >",11f,ThemeManager.accent(a),true).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{setColor(ThemeManager.explanationBg(a));cornerRadius=d(12,a).toFloat()}};prog.addView(continueBtn,LinearLayout.LayoutParams(-1,d(40,a)).apply{topMargin=d(9,a)})')
s=s.replace('content.addView(prog,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(15,a)})','content.addView(prog,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(15,a)});Thread{val result=runCatching{val repo=MainRepository(a);val refs=PerformanceManager.refs(a.applicationContext);val snap=AppManagers.analytics.snapshot(refs,1);val db=QBankDb(a);val sources=try{db.sources()}finally{db.close()};Pair(snap,repo.latestResumeTarget(sources))}.getOrNull();a.runOnUiThread{if(a.isFinishing||a.isDestroyed)return@runOnUiThread;val snap=result?.first;val resume=result?.second;val target=50;val solved=(snap?.todaySolved?:0).coerceAtLeast(0);val pct=(solved*100/target).coerceIn(0,100);progPct.text=pct.toString()+"%";progDetail.text="QBank "+solved+"/"+target+" today  •  "+(snap?.correct?:0)+" correct  •  "+(snap?.wrong?:0)+" wrong  •  "+(snap?.attempted?:0)+" attempted";dailyBar.progress=pct;continueBtn.text=if(resume!=null)"CONTINUE  •  "+resume.title.take(32)+"  ›" else "CONTINUE  •  OPEN QBANKS";continueBtn.setOnClickListener{if(resume==null)a.startActivity(Intent(a,MainQBankLibraryActivity::class.java)) else a.startActivity(Intent(a,QuizActivity::class.java).putExtra("testId",resume.testId).putExtra("title",resume.title).putExtra("position",resume.position))}}}.start()')
s=s.replace('qbankActions.addView(tv(a,"QBank Library",18f,Color.WHITE,true))','qbankActions.addView(tv(a,"QBank Library",18f,ThemeManager.text(a),true))')
s=s.replace('Browse main QBanks by subject and expand each subject to select its sub-QBanks, PYQ and test categories.','Browse your imported main QBanks separately, then open each bank to its tests.')
s=s.replace('content.addView(tv(a,"Quick Tools",19f,Color.WHITE,true)','content.addView(tv(a,"Quick Tools",19f,ThemeManager.text(a),true)')
s=s.replace('content.addView(tv(a,"Continue Learning    ›",19f,Color.WHITE,true)','content.addView(tv(a,"Continue Learning    ›",19f,ThemeManager.text(a),true)')
s=s.replace('background=surface(a,ThemeManager.pastelAccentFill(a,i),Color.WHITE)','background=surface(a,ThemeManager.pastelAccentFill(a,i),ThemeManager.elevated(a))')
s=s.replace('tv(a,s,15f,ThemeManager.text(a),true)','tv(a,s,15f,ThemeManager.pastelAccentText(a,i),true)')
s=s.replace('arrayOf("▣\\nPYQ","✓\\nTests","✎\\nNotes","▤\\nLibrary","⌘\\nTools","⚙\\nSettings").forEach{s->qr.addView(tv(a,s,14f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=surface(a,Color.argb(95,255,255,255),Color.argb(35,70,160,255))},LinearLayout.LayoutParams(d(104,a),d(76,a)).apply{rightMargin=d(8,a)})}','val quickLabels=arrayOf("▣\\nPYQ","✓\\nTests","✎\\nNotes","▤\\nLibrary","⌘\\nTools","⚙\\nSettings");quickLabels.forEachIndexed{idx,label->val v=tv(a,label,14f,ThemeManager.text(a),true).apply{gravity=Gravity.CENTER;background=surface(a,ThemeManager.elevated(a),ThemeManager.explanationBg(a));isClickable=true;setOnClickListener{when(idx){0->a.startActivity(Intent(a,StudyToolsActivity::class.java));1->a.startActivity(Intent(a,MainQBankLibraryActivity::class.java));2->a.startActivity(Intent(a,NotesActivity::class.java));3->a.startActivity(Intent(a,MainQBankLibraryActivity::class.java));4->a.startActivity(Intent(a,StudyToolsActivity::class.java));5->a.startActivity(Intent(a,SettingsActivity::class.java))}}};qr.addView(v,LinearLayout.LayoutParams(d(104,a),d(76,a)).apply{rightMargin=d(8,a)})}')
s=s.replace('nav.addView(n,LinearLayout.LayoutParams(0,d(58,a),1f))','n.tag="ROVEX_FOOTER_ITEM";nav.addView(n,LinearLayout.LayoutParams(0,d(58,a),1f))')
s=s.replace('shell.addView(nav,FrameLayout.LayoutParams(-1,d(70,a),Gravity.BOTTOM).apply{leftMargin=d(12,a);rightMargin=d(12,a);bottomMargin=d(10,a)})','nav.tag="ROVEX_FOOTER";shell.addView(nav,FrameLayout.LayoutParams(-1,d(70,a),Gravity.BOTTOM).apply{leftMargin=d(12,a);rightMargin=d(12,a);bottomMargin=d(10,a)})')
s=s.replace('fun refreshTheme(a:MainActivity){val root=a.findViewById<ViewGroup>(R.id.dashboardRoot)?:return;val shell=root.findViewWithTag<FrameLayout>("ROVEX_HOME_SHELL")?:return;shell.background=ThemeManager.backgroundDrawable(a);shell.invalidate()}','fun refreshTheme(a:MainActivity){val root=a.findViewById<ViewGroup>(R.id.dashboardRoot)?:return;val shell=root.findViewWithTag<FrameLayout>("ROVEX_HOME_SHELL")?:return;shell.background=ThemeManager.backgroundDrawable(a);val nav=shell.findViewWithTag<LinearLayout>("ROVEX_FOOTER");nav?.background=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;cornerRadius=d(24,a).toFloat();setColor(ThemeManager.elevated(a));val ac=ThemeManager.accent(a);setStroke(d(1,a),Color.argb(if(ThemeManager.isDark(a))150 else 80,Color.red(ac),Color.green(ac),Color.blue(ac)))};nav?.let{for(i in 0 until it.childCount){(it.getChildAt(i) as? TextView)?.setTextColor(if(i==0)ThemeManager.accent(a) else ThemeManager.text(a))}};shell.invalidate()}')
p.write_text(s)
# Imported-main-QBank library: real scrolling plus rename/delete/reorder controls.
p=j/"MainQBankLibraryActivity.kt";s=p.read_text()
s=s.replace('root.addView(TextView(this).apply{text="Imported main QBanks • tap a bank to open its tests";',
            'root.addView(TextView(this).apply{text="Imported main QBanks • rename, delete and reorder them";')
s=s.replace('sources.forEachIndexed{i,s->',
            'val order=getSharedPreferences("rovex_qbank_library",MODE_PRIVATE).getString("order","").orEmpty().split(",").mapNotNull{it.toLongOrNull()};val byId=sources.associateBy{it.id};val ordered=ArrayList<Source>();order.forEach{byId[it]?.let{v->if(ordered.none{x->x.id==v.id})ordered.add(v)}};sources.forEach{if(ordered.none{x->x.id==it.id})ordered.add(it)};getSharedPreferences("rovex_qbank_library",MODE_PRIVATE).edit().putString("order",ordered.joinToString(","){it.id.toString()}).apply();ordered.forEachIndexed{i,s->')
s=s.replace('card.setOnClickListener{startActivity(Intent(this,TestListActivity::class.java).putExtra("sourceId",s.id).putExtra("sourceName",s.fileName))};list.addView(card',
            '''val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    fun act(label:String,click:()->Unit)=TextView(this@MainQBankLibraryActivity).apply{text=label;textSize=10f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(this@MainQBankLibraryActivity));background=bg(ThemeManager.explanationBg(this@MainQBankLibraryActivity),12f);setOnClickListener{click()}}
    actions.addView(act("OPEN"){startActivity(Intent(this,TestListActivity::class.java).putExtra("sourceId",s.id).putExtra("sourceName",s.fileName))},LinearLayout.LayoutParams(0,d(38),1f).apply{rightMargin=d(3)})
    actions.addView(act("EDIT"){editSource(s)},LinearLayout.LayoutParams(0,d(38),1f).apply{leftMargin=d(3);rightMargin=d(3)})
    actions.addView(act("DELETE"){deleteSource(s)},LinearLayout.LayoutParams(0,d(38),1f).apply{leftMargin=d(3)})
    actions.addView(act("↑"){reorder(ordered,s,-1)},LinearLayout.LayoutParams(d(42),d(38)).apply{leftMargin=d(3)})
    actions.addView(act("↓"){reorder(ordered,s,1)},LinearLayout.LayoutParams(d(42),d(38)).apply{leftMargin=d(3)})
    card.addView(actions,LinearLayout.LayoutParams(-1,d(38)).apply{topMargin=d(8)})
    card.setOnClickListener{startActivity(Intent(this,TestListActivity::class.java).putExtra("sourceId",s.id).putExtra("sourceName",s.fileName))};list.addView(card''')
insert='''          private fun editSource(s:Source){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(20),0,d(20),0)}
  val name=EditText(this).apply{setText(s.fileName);setSingleLine(true);hint="QBank name"}
  val series=EditText(this).apply{setText(s.seriesNumber);setSingleLine(true);hint="Series number"}
  box.addView(name,LinearLayout.LayoutParams(-1,d(52)));box.addView(series,LinearLayout.LayoutParams(-1,d(52)))
  val dialog=android.app.AlertDialog.Builder(this).setTitle("Edit QBank").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
  dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(ThemeManager.accent(this));dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(ThemeManager.text(this));dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
    val n=name.text.toString().trim();if(n.isBlank()){Toast.makeText(this,"Name cannot be empty",Toast.LENGTH_SHORT).show();return@setOnClickListener}
    Thread{val ok=runCatching{MainRepository(this).use{it.updateSourceMetadata(s.id,n,series.text.toString().trim())}}.getOrDefault(false);runOnUiThread{if(ok){AppManagers.qbankLoading.invalidate(s.id);dialog.dismiss();load()}else Toast.makeText(this,"Could not save",Toast.LENGTH_SHORT).show()}}.start()
  }};dialog.show()
}
private fun deleteSource(s:Source){
  android.app.AlertDialog.Builder(this).setTitle("Delete imported QBank?").setMessage("This removes this imported bank and its questions.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->Thread{runCatching{MainRepository(this).use{it.deleteSource(s.id)}};runOnUiThread{AppManagers.qbankLoading.invalidate(s.id);load()}}.start()}.show()
}
private fun reorder(all:List<Source>,s:Source,delta:Int){
  val ids=all.map{it.id}.toMutableList();val i=ids.indexOf(s.id);val j=i+delta;if(i<0||j !in ids.indices)return
  val x=ids.removeAt(i);ids.add(j,x);getSharedPreferences("rovex_qbank_library",MODE_PRIVATE).edit().putString("order",ids.joinToString(",")).apply();load()
}
''';
s=s.replace('   override fun onResume(){',insert+'\n   override fun onResume(){')
p.write_text(s)
# Theme-adaptive Today Mission dialogs/panel and less intrusive calendar/planner surfaces.
p=j/"RovexDailyStudyHub.kt";s=p.read_text()
s=s.replace('color: Int = Color.WHITE','color: Int = ThemeManager.text(c)')
s=s.replace('box.background = card(c, Color.argb(120, 255, 65, 170), Color.argb(105, 70, 75, 235))','box.background = card(c, ThemeManager.peacockFill(c), ThemeManager.elevated(c))')
s=s.replace('background = card(c, Color.argb(100, 255, 255, 255), Color.argb(55, 110, 100, 255))','background = card(c, ThemeManager.elevated(c), ThemeManager.explanationBg(c))')
s=s.replace('background = card(c, Color.argb(100, 55, 210, 255), Color.argb(70, 145, 55, 235))','background = card(c, ThemeManager.pastelBlueFill(c), ThemeManager.elevated(c))')
s=s.replace('background = card(c, Color.argb(90, 0, 225, 185), Color.argb(65, 0, 100, 210))','background = card(c, ThemeManager.peacockFill(c), ThemeManager.elevated(c))')
s=s.replace('dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.dismiss(); showCalendarEvents(ctx) } }','dialog.setOnShowListener { themeDialog(dialog,ctx); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.dismiss(); showCalendarEvents(ctx) } }')
s=s.replace('dialog.setOnShowListener {\n            val sync = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)','dialog.setOnShowListener {\n            themeDialog(dialog,c)\n            val sync = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)')
s=s.replace('\n    private fun exportFirstCalendarBlock','\n    private fun themeDialog(dialog:AlertDialog,c:Context){dialog.window?.setBackgroundDrawable(GradientDrawable().apply{setColor(ThemeManager.dialogBg(c));cornerRadius=dp(c,22).toFloat()});dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ThemeManager.accent(c));dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ThemeManager.text(c));dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(ThemeManager.accent(c))}\n\n    private fun exportFirstCalendarBlock')
p.write_text(s)
# Ben + AI immersive reading pass: reclaim vertical space and make wide tables truly scrollable.
p=j/"BenQuestionAiContextDialog.kt";s=p.read_text()
s=s.replace('setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))','setPadding(dp(activity, 6), dp(activity, 3), dp(activity, 6), dp(activity, 3))')
s=s.replace('textSize = 23f','textSize = 20f')
s=s.replace('textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER','textSize = 9f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER',1)
s=s.replace('setPadding(dp(activity, 10), dp(activity, 7), dp(activity, 10), dp(activity, 7))','setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 4))')
s=s.replace('setPadding(0, dp(activity, 2), 0, dp(activity, 9))','setPadding(0, dp(activity, 1), 0, dp(activity, 3))')
s=s.replace('modeRow.addView(c, LinearLayout.LayoutParams(0, dp(activity, 40), 1f)','modeRow.addView(c, LinearLayout.LayoutParams(0, dp(activity, 33), 1f)')
s=s.replace('setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 4))','setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2))')
s=s.replace('setPadding(0, dp(activity, 8), 0, 0)','setPadding(0, dp(activity, 3), 0, 0)')
s=s.replace('dp(activity, 44)','dp(activity, 38)')
s=s.replace('dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)','dialog.window?.setBackgroundDrawable(rounded(activity, ThemeManager.dialogBg(activity), 0f))')
s=s.replace('dialog.window?.setLayout(-1, -1)','dialog.window?.setLayout(-1, -1)\n        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)\n        dialog.window?.setDimAmount(0.10f)')
s=s.replace('web.loadDataWithBaseURL(null, localSummary, "text/html", "UTF-8", null)\n                web.loadDataWithBaseURL(null, localSummary, "text/html", "UTF-8", null)','web.loadDataWithBaseURL(null, localSummary, "text/html", "UTF-8", null)')
s=s.replace("private fun styledHtml(body: String, activity: QuizActivity): String {","private fun styledHtml(body: String, activity: QuizActivity): String {\n        val safeBody = body.replace(Regex(\"(?i)<table\\\\b\"), \"<div class='table-wrap'><table\").replace(Regex(\"(?i)</table>\"), \"</table></div>\")")
s=s.replace('body *{color:inherit}','body *{color:inherit}.table-wrap{width:100%;max-width:100%;overflow-x:auto;overflow-y:hidden;touch-action:pan-x;overscroll-behavior-x:contain;-webkit-overflow-scrolling:touch}.table-wrap table{width:max-content;min-width:100%;table-layout:auto;display:table;overflow:visible}.table-wrap th,.table-wrap td{min-width:120px}')
s=s.replace('<body>$body</body>','<body>$safeBody</body>')
p.write_text(s)
print("BEN_IMMERSIVE_PATCH_APPLIED")
print("MISSION_THEME_PATCH_APPLIED")
print("MAIN_QBANK_LIBRARY_PATCH_APPLIED")
print("HOME_UX_PATCH_APPLIED")
print("STABILITY_UI_REPAIR_APPLIED")
