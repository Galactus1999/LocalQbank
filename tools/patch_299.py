# Reconstruct the offline v8.3.299 handoff from the verified v8.3.298 stage.
from pathlib import Path
ROOT=Path(__file__).resolve().parent
B=ROOT/'app/src/main/java/com/localqbank/library'
def rw(rel,fn):
 p=ROOT/rel; s=p.read_text(); p.write_text(fn(s))

rw('app/build.gradle.kts', lambda s:s.replace('versionCode = 392','versionCode = 393',1).replace('versionName = "8.3.298"','versionName = "8.3.299"',1))
(B/'RovexModernUi.kt').write_text('''package com.localqbank.library
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
object RovexModernUi {
 private fun dp(v:Int,view:View)= (v*view.resources.displayMetrics.density).toInt()
 private fun surface(view:View,fill:Int,stroke:Int,radius:Int=20)=GradientDrawable().apply{setColor(fill);setStroke(dp(1,view),stroke);cornerRadius=dp(radius,view).toFloat()}
 fun applyMain(a:MainActivity){val root=a.findViewById<View>(R.id.dashboardRoot)?:return;root.setBackgroundColor(ThemeManager.bg(a));val e=ThemeManager.elevated(a);val st=Color.argb(if(ThemeManager.isDark(a))72 else 42,Color.red(ThemeManager.accent(a)),Color.green(ThemeManager.accent(a)),Color.blue(ThemeManager.accent(a)));listOf(R.id.renCard,R.id.todaySolvedCard,R.id.studyMenuCard,R.id.flashcardCard,R.id.performanceLabCard,R.id.homeSearchCard).forEach{id->a.findViewById<View>(id)?.apply{background=surface(this,e,st,if(id==R.id.homeSearchCard)18 else 22);elevation=dp(if(id==R.id.renCard)5 else 2,this).toFloat()}};listOf(R.id.importantButton,R.id.reviseButton,R.id.doubtButton,R.id.favoriteButton,R.id.addButton,R.id.continueStudyButton).forEach{id->a.findViewById<Button>(id)?.let{styleButton(it)}};a.findViewById<ViewGroup>(R.id.rovexBottomNav)?.apply{background=surface(this,ThemeManager.elevated(a),st,24);elevation=dp(10,this).toFloat()};tintText(root,a)}
 fun applySection(root:View,a:RovexSectionDashboardActivity){root.setBackgroundColor(ThemeManager.bg(a));tintText(root,a);root.findViewById<ViewGroup>(R.id.rovexBottomNav)?.let{it.background=surface(it,ThemeManager.elevated(a),Color.argb(72,Color.red(ThemeManager.accent(a)),Color.green(ThemeManager.accent(a)),Color.blue(ThemeManager.accent(a))),24)}}
 fun applyRen(root:View,a:RenActivity){root.setBackgroundColor(ThemeManager.bg(a));tintText(root,a)}
 private fun tintText(v:View,a:android.app.Activity){if(v is TextView){val id=if(v.id!=View.NO_ID)runCatching{v.resources.getResourceEntryName(v.id).lowercase()}.getOrDefault("") else "";v.setTextColor(when{ id.contains("ai")||id.contains("ben")->ThemeManager.aiText(a);id.contains("hint")||id.contains("sub")||id.contains("stats")->ThemeManager.muted(a);else->ThemeManager.text(a)})};if(v is ViewGroup)for(i in 0 until v.childCount)tintText(v.getChildAt(i),a)}
 private fun styleButton(b:Button){val c=b.context;b.background=surface(b,ThemeManager.peacockFill(c),Color.argb(80,Color.red(ThemeManager.accent(c)),Color.green(ThemeManager.accent(c)),Color.blue(ThemeManager.accent(c))),16);b.setTextColor(ThemeManager.peacockText(c));b.stateListAnimator=null;b.minHeight=dp(42,b);b.isAllCaps=false}
}
''')
rw('app/src/main/java/com/localqbank/library/MainActivity.kt',lambda s:s.replace('setContentView(R.layout.activity_main);applyResponsiveHomeLayout(); AppState.register(stateListener)','setContentView(R.layout.activity_main);applyResponsiveHomeLayout(); RovexModernUi.applyMain(this); AppState.register(stateListener)'))
rw('app/src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt',lambda s:s.replace('    setContentView(root)\n','    setContentView(root)\n    RovexModernUi.applySection(root, this)\n',1))
rw('app/src/main/java/com/localqbank/library/RenActivity.kt',lambda s:s.replace('''    private var lastBenReply:String get()=chatState.lastBenReply set(v){chatState.lastBenReply=v}''','''    private var lastBenReply: String
        get() = chatState.lastBenReply
        set(value) { chatState.lastBenReply = value }''').replace('''        ViewCompat.requestApplyInsets(root)\n''','''        ViewCompat.requestApplyInsets(root)\n        RovexModernUi.applyRen(root, this)\n''',1))