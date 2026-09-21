from pathlib import Path
R=Path(__file__).resolve().parent; B=R/'app/src/main/java/com/localqbank/library'
def rw(p,f):
 q=R/p; q.write_text(f(q.read_text()))
rw('app/build.gradle.kts',lambda s:s.replace('versionCode = 391','versionCode = 392',1).replace('versionName = "8.3.297"','versionName = "8.3.298"',1))
(B/'RenChatViewModel.kt').write_text('''package com.localqbank.library
import androidx.lifecycle.ViewModel
class RenChatViewModel:ViewModel(){
 val turns=mutableListOf<Pair<String,String>>()
 var prompt:String=""
 var lastBenReply:String=""
 var webScrollY:Int=0
}''')
rw('app/src/main/java/com/localqbank/library/RenActivity.kt',lambda s:s
.replace('import androidx.core.view.WindowInsetsCompat\n','import androidx.core.view.WindowInsetsCompat\nimport androidx.appcompat.app.AppCompatActivity\nimport androidx.activity.viewModels\n')
.replace('private val chatTurns = mutableListOf<Pair<String,String>>()','private val chatState:RenChatViewModel by viewModels()\n    private val chatTurns:MutableList<Pair<String,String>> get()=chatState.turns')
.replace('private var lastBenReply: String = ""','private var lastBenReply:String get()=chatState.lastBenReply set(v){chatState.lastBenReply=v}')
.replace('setContentView(build())','setContentView(build())\n        promptInput?.setText(chatState.prompt)\n        promptInput?.setSelection(promptInput?.text?.length?:0)',1)
.replace('private fun build(): View {','private fun isLandscape()=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE\n    private fun build(): View {',1)
.replace('root.addView(header)','root.addView(header,LinearLayout.LayoutParams(-1,if(isLandscape())dp(34) else dp(42)))',1)
.replace('LinearLayout.LayoutParams(dp(78),dp(40))','LinearLayout.LayoutParams(dp(78),if(isLandscape())dp(32) else dp(40))')
.replace('LinearLayout.LayoutParams(dp(94),dp(40))','LinearLayout.LayoutParams(dp(94),if(isLandscape())dp(32) else dp(40))')
.replace('root.addView(input,LinearLayout.LayoutParams(-1,dp(62)).apply{setMargins(0,dp(2),0,dp(5))})','root.addView(input,LinearLayout.LayoutParams(-1,if(isLandscape())dp(48) else dp(62)).apply{setMargins(0,dp(1),0,if(isLandscape())dp(3) else dp(5))})')
.replace('LinearLayout.LayoutParams(0,dp(38),1f)','LinearLayout.LayoutParams(0,if(isLandscape())dp(32) else dp(38),1f)')
.replace('LinearLayout.LayoutParams(-1, dp(38))','LinearLayout.LayoutParams(-1,if(isLandscape())dp(32) else dp(38))')
.replace('topMargin = dp(5)','topMargin = if(isLandscape())dp(3) else dp(5)',1)
.replace('renderChat(answer)','renderChat(answer)\n        answer.postDelayed({if(chatState.webScrollY>0)answer.scrollTo(0,chatState.webScrollY)},120)',1)
.replace('input.setOnFocusChangeListener','input.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int)=Unit;override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){chatState.prompt=s?.toString().orEmpty()};override fun afterTextChanged(e:android.text.Editable?)=Unit})\n        input.setOnFocusChangeListener',1)
.replace('override fun onDestroy(){\n        liveJob?.cancel()','override fun onDestroy(){\n        chatWeb?.let{chatState.webScrollY=it.scrollY}\n        if(!isChangingConfigurations){liveJob?.cancel();cloudJob?.cancel()}',1)
.replace('        cloudJob?.cancel()\n        liveScope.cancel()','        liveScope.cancel()',1))
rw('app/src/main/java/com/localqbank/library/RenActivity.kt',lambda s:s.replace('width:max-content;min-width:100%;max-width:none;table-layout:auto','width:auto;min-width:100%;max-width:none;table-layout:auto',1).replace('overflow-x:hidden;overflow-wrap:anywhere','overflow-x:auto;overflow-wrap:anywhere',1).replace('.table-wrap{box-sizing:border-box;width:100%;','.table-wrap{touch-action:pan-x pan-y;box-sizing:border-box;width:100%;',1).replace('min-width:96px;max-width:280px;','min-width:108px;max-width:300px;',1))
(B/'RovexAdaptiveUi.kt').write_text('''package com.localqbank.library
import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
object RovexAdaptiveUi{
 fun apply(a:Activity){
  if(a is RenActivity||a.resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE)return
  val r=a.window.decorView.findViewById<ViewGroup>(android.R.id.content)?:return
  val d=a.resources.displayMetrics.density
  fun walk(v:View){
   val n=if(v.id!=View.NO_ID)runCatching{a.resources.getResourceEntryName(v.id)}.getOrDefault("") else ""
   if(n.contains("rovexBottomNav",true)||n.contains("bottomNav",true)||n.contains("footer",true))v.layoutParams=v.layoutParams.apply{height=(42*d).toInt()}
   if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i))
  };walk(r)
 }
}''')
rw('app/src/main/java/com/localqbank/library/ResilienceManager.kt',lambda s:s.replace('JankStatsMonitor.onResumed(activity)\n                ProductionCrashReporter.screen','JankStatsMonitor.onResumed(activity)\n                RovexAdaptiveUi.apply(activity)\n                ProductionCrashReporter.screen',1).replace('override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {','override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {\n                a.window.decorView.post { RovexAdaptiveUi.apply(a) }',1))
rw('app/src/main/java/com/localqbank/library/MainActivity.kt',lambda s:s.replace('nav.background=GradientDrawable().apply{','nav.layoutParams=nav.layoutParams.apply{height=dp(if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)42 else 58)}\n        nav.background=GradientDrawable().apply{',1).replace('val expanded=widthDp>=600','val expanded=widthDp>=600\n        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE',1).replace('if(compact) 50 else if(expanded) 60 else 56','if(landscape)44 else if(compact)50 else if(expanded)60 else 56',1).replace('if(compact) 108 else if(expanded) 118 else 104','if(landscape)82 else if(compact)108 else if(expanded)118 else 104',1).replace('if(compact)330 else if(expanded)360 else 340','if(landscape)250 else if(compact)330 else if(expanded)360 else 340',2))
rw('app/src/main/res/layout/activity_main.xml',lambda s:s.replace('android:layout_height="58dp">\n        <TextView android:id="@+id/navQBank"','android:layout_height="42dp">\n        <TextView android:id="@+id/navQBank"',1))
rw('app/src/main/AndroidManifest.xml',lambda s:s.replace('android:screenOrientation="unspecified"','android:screenOrientation="unspecified" android:resizeableActivity="true"'))
rw('app/src/main/java/com/localqbank/library/RovexMarkdown.kt',lambda s:s.replace('width:max-content;min-width:100%;max-width:none;table-layout:auto','width:auto;min-width:100%;max-width:none;table-layout:auto',1))
rw('app/src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt',lambda s:s.replace('root.addView(nav(),FrameLayout.LayoutParams(-1,d(58),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{','root.addView(nav(),FrameLayout.LayoutParams(-1,if(isLandscape())d(44) else d(58),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{',1).replace('private fun buildShell(){','private fun isLandscape()=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE\nprivate fun buildShell(){',1).replace('setPadding(0,0,0,d(82))','setPadding(0,0,0,if(isLandscape())d(58) else d(82))').replace('(content.parent as? ScrollView)?.setPadding(0,0,0,d(82))','(content.parent as? ScrollView)?.setPadding(0,0,0,if(isLandscape())d(58) else d(82))',1).replace('setPadding(d(5),d(5),d(5),d(5))','setPadding(d(5),if(isLandscape())d(3) else d(5),d(5),if(isLandscape())d(3) else d(5))',1))
print("PATCH_298_OK")
