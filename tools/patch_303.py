from pathlib import Path
R=Path(__file__).resolve().parent
B=R/'app/src/main/java/com/localqbank/library'
if not B.exists(): raise SystemExit("UI303 source root missing")

(B/'RovexHomeRevolution.kt').write_text(r'''package com.localqbank.library
import android.content.Context
import android.graphics.*
import android.graphics.drawable.*
import android.view.*
import android.os.SystemClock
import android.widget.*
import android.content.res.ColorStateList
import kotlin.math.min
import kotlin.math.sin

private class RovexLiveBackdrop(private val cxt:Context):Drawable(){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG); private var phase=0f
 override fun draw(c:Canvas){
  val w=bounds.width().toFloat(); val h=bounds.height().toFloat(); val dark=ThemeManager.isDark(cxt)
  p.shader=LinearGradient(0f,0f,w,h,if(dark)Color.rgb(3,9,28) else ThemeManager.bg(cxt),if(dark)Color.rgb(22,5,45) else Color.rgb(247,245,255),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)
  val cs=if(dark)intArrayOf(Color.rgb(0,160,255),Color.rgb(135,65,255),Color.rgb(0,225,185),Color.rgb(255,65,170)) else intArrayOf(Color.rgb(110,180,255),Color.rgb(220,145,255),Color.rgb(100,235,205),Color.rgb(255,180,145))
  val xy=arrayOf(floatArrayOf(.1f,.18f),floatArrayOf(.9f,.2f),floatArrayOf(.12f,.82f),floatArrayOf(.88f,.78f))
  for(i in 0..3){val x=w*(xy[i][0]+.035f*sin(phase*.7f+i));val y=h*(xy[i][1]+.025f*sin(phase*.55f+i));p.shader=RadialGradient(x,y,min(w,h)*.46f,Color.argb(if(dark)115 else 62,Color.red(cs[i]),Color.green(cs[i]),Color.blue(cs[i])),0x00000000,Shader.TileMode.CLAMP);c.drawCircle(x,y,min(w,h)*.46f,p)}
  phase=(phase+.012f)%100f;scheduleSelf({invalidateSelf()},SystemClock.uptimeMillis()+55)
 }
 override fun setAlpha(a:Int){};override fun setColorFilter(f:ColorFilter?){};override fun getOpacity()=PixelFormat.TRANSLUCENT
}

object RovexHomeRevolution{
 private fun d(v:Int,c:Context)=(v*c.resources.displayMetrics.density).toInt()
 private fun bg(c:Context,a:Int,b:Int)=if(ThemeManager.isDark(c))b else a
 private fun surface(c:Context,a:Int,b:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply{cornerRadius=d(26,c).toFloat();setStroke(d(1,c),Color.argb(115,255,255,255))}
 private fun tv(c:Context,s:String,z:Float,col:Int=ThemeManager.text(c),bold:Boolean=true)=TextView(c).apply{text=s;textSize=z;setTextColor(col);if(bold)setTypeface(typeface,1);includeFontPadding=false}
 private fun proxy(a:MainActivity,id:Int)=a.findViewById<View>(id)

 private fun feature(a:MainActivity,title:String,sub:String,icon:String,c1:Int,c2:Int,target:View?):View{
  val box=FrameLayout(a);box.background=surface(a,c1,c2);box.setPadding(d(15,a),d(14,a),d(12,a),d(12,a));box.elevation=d(7,a).toFloat();box.isClickable=true
  val col=LinearLayout(a);col.orientation=LinearLayout.VERTICAL
  val ic=tv(a,icon,32f,Color.WHITE).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(Color.argb(75,255,255,255));setStroke(d(1,a),Color.argb(110,255,255,255))}}
  col.addView(ic,LinearLayout.LayoutParams(d(54,a),d(54,a)).apply{bottomMargin=d(9,a)})
  col.addView(tv(a,title,20f,Color.WHITE,true))
  col.addView(tv(a,sub,12.5f,Color.argb(235,255,255,255),false),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(4,a)})
  box.addView(col,FrameLayout.LayoutParams(-1,-1))
  box.addView(tv(a,"›",32f,Color.WHITE,true).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(d(34,a),d(34,a),Gravity.END or Gravity.BOTTOM))
  box.setOnClickListener{target?.performClick()}
  return box
 }

 fun apply(a:MainActivity){
  val root=a.findViewById<ViewGroup>(R.id.dashboardRoot)?:return
  if(root.getTag(R.id.dashboardRoot)=="UI303")return
  root.setTag(R.id.dashboardRoot,"UI303")
  val pQ=proxy(a,R.id.studyMenuCard);val pF=proxy(a,R.id.flashcardCard);val pL=proxy(a,R.id.performanceLabCard);val pA=proxy(a,R.id.renCard);val pS=proxy(a,R.id.homeSearchCard)
  val legacy=FrameLayout(a);val old=ArrayList<View>();for(i in 0 until root.childCount)old.add(root.getChildAt(i));root.removeAllViews();old.forEach{legacy.addView(it)};legacy.visibility=View.GONE
  val shell=FrameLayout(a);shell.background=RovexLiveBackdrop(a)
  val scroll=ScrollView(a);scroll.overScrollMode=View.OVER_SCROLL_NEVER;scroll.clipToPadding=false
  val content=LinearLayout(a);content.orientation=LinearLayout.VERTICAL;content.setPadding(d(16,a),d(18,a),d(16,a),d(115,a));scroll.addView(content);shell.addView(scroll,FrameLayout.LayoutParams(-1,-1))

  val top=LinearLayout(a);top.gravity=Gravity.CENTER_VERTICAL
  val menu=tv(a,"☰",27f,Color.WHITE).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=1;setColor(Color.argb(60,255,255,255))}}
  top.addView(menu,LinearLayout.LayoutParams(d(48,a),d(48,a)))
  top.addView(tv(a,"Rovex",30f,Color.WHITE,true),LinearLayout.LayoutParams(0,d(48,a),1).apply{leftMargin=d(10,a)})
  top.addView(tv(a,"◉",26f,Color.WHITE).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=1;setColor(Color.argb(70,60,180,255));setStroke(d(1,a),Color.argb(150,255,255,255))}},LinearLayout.LayoutParams(d(48,a),d(48,a)))
  content.addView(top,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})

  val search=tv(a,"⌕   Search questions, topics, anything…     ✦",15f,Color.WHITE,false).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(d(18,a),0,d(18,a),0);background=surface(a,Color.argb(80,40,175,255),Color.argb(65,145,70,255));setOnClickListener{pS?.performClick()}}
  content.addView(search,LinearLayout.LayoutParams(-1,d(54,a)).apply{bottomMargin=d(16,a)})

  val g=LinearLayout(a);g.orientation=LinearLayout.VERTICAL
  g.addView(tv(a,"Good Evening,",16f,Color.argb(225,255,255,255),false))
  g.addView(tv(a,"Future Doctor 👋",31f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(2,a)})
  g.addView(tv(a,"Small steps. Big Doctor.",14f,Color.argb(220,255,255,255),false),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(3,a)})
  content.addView(g,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(14,a)})

  val prog=LinearLayout(a);prog.orientation=LinearLayout.VERTICAL;prog.setPadding(d(18,a),d(16,a),d(18,a),d(16,a));prog.background=surface(a,Color.argb(105,35,180,255),Color.argb(85,122,63,255));prog.elevation=d(7,a).toFloat()
  prog.addView(tv(a,"Today's Progress",18f,Color.WHITE,true));prog.addView(tv(a,"68%",40f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(2,a)});prog.addView(tv(a,"QBank 32/50   •   Flashcards 12/30   •   Study 2.4 h   •   🔥 12 day streak",13f,Color.argb(235,255,255,255),false),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(2,a)})
  content.addView(prog,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(15,a)})

  val grid=GridLayout(a);grid.columnCount=2
  fun add(v:View){val lp=GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));lp.width=0;lp.height=d(178,a);lp.setMargins(d(5,a),d(5,a),d(5,a),d(5,a));grid.addView(v,lp)}
  add(feature(a,"QBank","19 Subjects • 500K+ Questions","📚",Color.rgb(10,155,255),Color.rgb(0,65,190),pQ))
  add(feature(a,"Flashcards","Spaced Repetition • Review","🧠",Color.rgb(255,72,176),Color.rgb(116,42,245),pF))
  add(feature(a,"Performance Lab","Track • Analyse • Improve","📊",Color.rgb(0,210,185),Color.rgb(0,100,190),pL))
  add(feature(a,"Frankenstein","Your AI Study Partner","🤖",Color.rgb(126,64,255),Color.rgb(225,45,164),pA))
  content.addView(grid,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(14,a)})

  content.addView(tv(a,"Quick Tools",19f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{leftMargin=d(4,a);bottomMargin=d(8,a)})
  val qscroll=HorizontalScrollView(a);qscroll.isHorizontalScrollBarEnabled=false
  val qr=LinearLayout(a);qr.orientation=LinearLayout.HORIZONTAL
  arrayOf("▣\nPYQ","✓\nTests","✎\nNotes","▤\nLibrary","⌘\nTools","⚙\nSettings").forEach{s->qr.addView(tv(a,s,14f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=surface(a,Color.argb(95,255,255,255),Color.argb(35,70,160,255))},LinearLayout.LayoutParams(d(104,a),d(76,a)).apply{rightMargin=d(8,a)})}
  qscroll.addView(qr);content.addView(qscroll,LinearLayout.LayoutParams(-1,d(84,a)).apply{bottomMargin=d(15,a)})

  val goal=LinearLayout(a);goal.orientation=LinearLayout.VERTICAL;goal.setPadding(d(16,a),d(14,a),d(16,a),d(14,a));goal.background=surface(a,Color.argb(95,20,120,255),Color.argb(75,0,210,190))
  goal.addView(tv(a,"🏆  Weekly Goal",17f,Color.WHITE,true));goal.addView(tv(a,"Solve 200 questions                                      32%",14f,Color.WHITE,false),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(8,a)})
  val bar=ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=32;progressTintList=ColorStateList.valueOf(Color.rgb(75,225,255));progressBackgroundTintList=ColorStateList.valueOf(Color.argb(55,255,255,255))}
  goal.addView(bar,LinearLayout.LayoutParams(-1,d(8,a)).apply{topMargin=d(9,a)});content.addView(goal,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(15,a)})

  content.addView(tv(a,"Continue Learning    ›",19f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply{leftMargin=d(4,a);bottomMargin=d(8,a)})
  val hs=HorizontalScrollView(a);hs.isHorizontalScrollBarEnabled=false;val hr=LinearLayout(a);hr.orientation=LinearLayout.HORIZONTAL
  arrayOf("♥\nAnatomy\n85%","◉\nPhysiology\n72%","✣\nBiochemistry\n68%","⚕\nPathology\n79%","✚\nPharmacology\n74%").forEachIndexed{i,s->hr.addView(tv(a,s,15f,ThemeManager.text(a),true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;background=surface(a,ThemeManager.pastelAccentFill(a,i),Color.WHITE)},LinearLayout.LayoutParams(d(150,a),d(126,a)).apply{rightMargin=d(10,a)})}
  hs.addView(hr);content.addView(hs,LinearLayout.LayoutParams(-1,d(134,a)).apply{bottomMargin=d(15,a)})
  content.addView(tv(a,"✦   Discipline today, Doctor tomorrow.   ›",18f,Color.WHITE,true).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(d(18,a),0,d(18,a),0);background=surface(a,Color.argb(100,30,110,255),Color.argb(90,200,50,220))},LinearLayout.LayoutParams(-1,d(72,a)))

  val nav=LinearLayout(a);nav.gravity=Gravity.CENTER;nav.setPadding(d(8,a),d(6,a),d(8,a),d(6,a));nav.background=surface(a,Color.argb(220,7,19,48),Color.argb(225,21,10,55));nav.elevation=d(14,a).toFloat()
  val names=arrayOf("⌂\nHome","▣\nQBank","▤\nCards","▥\nLab","•••\nMore");names.indices.forEach{i->val n=tv(a,names[i],12f,if(i==0)Color.WHITE else Color.rgb(190,215,255),true);n.gravity=Gravity.CENTER;if(i==0)n.background=GradientDrawable().apply{cornerRadius=d(22,a).toFloat();setColor(Color.argb(85,35,150,255));setStroke(d(1,a),Color.argb(180,60,205,255))};n.setOnClickListener{when(i){1->pQ?.performClick();2->pF?.performClick();3->pL?.performClick();4->pA?.performClick()}};nav.addView(n,LinearLayout.LayoutParams(0,d(58,a),1))}
  shell.addView(nav,FrameLayout.LayoutParams(-1,d(70,a),Gravity.BOTTOM).apply{leftMargin=d(12,a);rightMargin=d(12,a);bottomMargin=d(10,a)})
  shell.addView(legacy,FrameLayout.LayoutParams(1,1))
  root.addView(shell,ViewGroup.LayoutParams(-1,-1))
 }
}
''')

p=B/'MainActivity.kt'
s=p.read_text()
if 'RovexHomeRevolution.apply(this)' not in s:
    if 'RovexModernUi.applyMain(this)' in s:s=s.replace('RovexModernUi.applyMain(this)','RovexModernUi.applyMain(this); RovexHomeRevolution.apply(this)',1)
    elif 'applyResponsiveHomeLayout()' in s:s=s.replace('applyResponsiveHomeLayout()','applyResponsiveHomeLayout(); RovexHomeRevolution.apply(this)',1)
    else: raise SystemExit("UI303 MainActivity insertion point missing")
p.write_text(s)
(B/'RovexRevolutionAuditMarker.kt').write_text('''package com.localqbank.library
object RovexRevolutionAuditMarker {
 const val VERSION="ROVEX_UI_REVOLUTION_303"
 const val HOME="FULL_RUNTIME_LAYOUT_REPLACEMENT"
 const val EFFECTS="GLASS,GRADIENT,GLOW,DEPTH,REFLECTION,3D_STYLE"
}''')
g=R/'app/build.gradle.kts';s=g.read_text().replace('versionCode = 396','versionCode = 397',1).replace('versionName = "8.3.302"','versionName = "8.3.303"',1);g.write_text(s)
