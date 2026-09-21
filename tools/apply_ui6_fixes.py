#!/usr/bin/env python3
from pathlib import Path
import sys
import re
root=Path(sys.argv[1])

def edit(rel,repls):
    p=root/rel
    s=p.read_text()
    for old,new in repls:
        if old not in s: raise SystemExit("UI6 patch anchor missing: "+rel)
        s=s.replace(old,new,1)
    p.write_text(s)

p=root/"app/build.gradle.kts"
g=p.read_text()
if "compileSdk = 36" in g:
    g=g.replace("compileSdk = 36","compileSdk = 37",1)
elif "compileSdk = 37" not in g:
    raise SystemExit("UI6 patch anchor missing: compileSdk")
p.write_text(g)

edit("app/src/main/AndroidManifest.xml",[(
    '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
    '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n<uses-permission android:name="android.permission.READ_CALENDAR" />'
)])

edit("app/src/main/java/com/localqbank/library/MainActivity.kt",[(
'''    private fun openImporter(){
        htmlImportLauncher.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml"))
    }''',
'''    fun openQBankImporterFromHome(){ openImporter() }
    private fun openImporter(){
        htmlImportLauncher.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml"))
    }

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==RovexDailyStudyHub.CALENDAR_PERMISSION_REQUEST) RovexDailyStudyHub.onCalendarPermissionResult(this,grantResults)
    }'''
)])

edit("app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt",[
('  val pQ=proxy(a,R.id.studyMenuCard);val pF=proxy(a,R.id.flashcardCard);val pL=proxy(a,R.id.performanceLabCard);val pA=proxy(a,R.id.renCard);val pS=proxy(a,R.id.homeSearchCard)',
 '  val pQ:View?=null;val pF=proxy(a,R.id.flashcardCard);val pL=proxy(a,R.id.performanceLabCard);val pA=proxy(a,R.id.renCard);val pS=proxy(a,R.id.homeSearchCard)'),
('''  top.addView(tv(a,"◉",26f,Color.WHITE).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=1;setColor(Color.argb(70,60,180,255));setStroke(d(1,a),Color.argb(150,255,255,255))}},LinearLayout.LayoutParams(d(48,a),d(48,a)))''',
'''  top.addView(tv(a,"⚙",25f,ThemeManager.text(a),true).apply{
    gravity=Gravity.CENTER
    contentDescription="Settings"
    background=GradientDrawable().apply{shape=1;setColor(if(ThemeManager.isDark(a))Color.argb(70,60,180,255) else Color.argb(120,255,255,255));setStroke(d(1,a),Color.argb(if(ThemeManager.isDark(a))150 else 110,ThemeManager.accent(a).let{Color.red(it)},ThemeManager.accent(a).let{Color.green(it)},ThemeManager.accent(a).let{Color.blue(it)}))}
    setOnClickListener{a.startActivity(Intent(a,SettingsActivity::class.java))}
  },LinearLayout.LayoutParams(d(48,a),d(48,a)))'''),
('''  add(feature(a,"QBank","19 Subjects • 500K+ Questions","📚",Color.rgb(10,155,255),Color.rgb(0,65,190),pQ))''',
'''  val qbankFeature=feature(a,"QBank","19 Subjects • 500K+ Questions","📚",Color.rgb(10,155,255),Color.rgb(0,65,190),pQ)
  qbankFeature.setOnClickListener{a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","qbank").addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
  add(qbankFeature)'''),
('''  add(feature(a,"Frankenstein","Your AI Study Partner","🤖",Color.rgb(126,64,255),Color.rgb(225,45,164),pA))
  content.addView(grid,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(14,a)})''',
'''  add(feature(a,"Frankenstein","Your AI Study Partner","🤖",Color.rgb(126,64,255),Color.rgb(225,45,164),pA))
  content.addView(grid,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})

  val qbankActions=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16,a),d(13,a),d(16,a),d(13,a));background=surface(a,Color.argb(105,30,150,255),Color.argb(80,110,55,230));elevation=d(6,a).toFloat()}
  qbankActions.addView(tv(a,"QBank Library",18f,Color.WHITE,true))
  qbankActions.addView(tv(a,"Browse main QBanks by subject and expand each subject to select its sub-QBanks, PYQ and test categories.",12f,Color.argb(235,255,255,255),false),LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(4,a)})
  val qbankButtons=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val browse=tv(a,"BROWSE QBANKS",11.5f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=surface(a,Color.argb(100,35,160,255),Color.argb(75,65,90,240));setOnClickListener{a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","qbank").addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))}}
  val import=tv(a,"IMPORT QBANK",11.5f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=surface(a,Color.argb(90,0,205,185),Color.argb(70,25,110,220));setOnClickListener{a.openQBankImporterFromHome()}}
  qbankButtons.addView(browse,LinearLayout.LayoutParams(0,d(46,a),1f).apply{rightMargin=d(5,a)})
  qbankButtons.addView(import,LinearLayout.LayoutParams(0,d(46,a),1f).apply{leftMargin=d(5,a)})
  qbankActions.addView(qbankButtons,LinearLayout.LayoutParams(-1,d(48,a)).apply{topMargin=d(9,a)})
  content.addView(qbankActions,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(14,a)})'''),
('''  val nav=LinearLayout(a);nav.gravity=Gravity.CENTER;nav.setPadding(d(8,a),d(6,a),d(8,a),d(6,a));nav.background=surface(a,Color.argb(220,7,19,48),Color.argb(225,21,10,55));nav.elevation=d(14,a).toFloat()
  val names=arrayOf("⌂\\nHome","▣\\nQBank","▤\\nCards","▥\\nLab","•••\\nMore");names.indices.forEach{i->val n=tv(a,names[i],12f,if(i==0)Color.WHITE else Color.rgb(190,215,255),true);n.gravity=Gravity.CENTER;if(i==0)n.background=GradientDrawable().apply{cornerRadius=d(22,a).toFloat();setColor(Color.argb(85,35,150,255));setStroke(d(1,a),Color.argb(180,60,205,255))};n.setOnClickListener{when(i){1->pQ?.performClick();2->pF?.performClick();3->pL?.performClick();4->pA?.performClick()}};nav.addView(n,LinearLayout.LayoutParams(0,d(58,a),1f))}''',
'''  val dark=ThemeManager.isDark(a)
  val nav=LinearLayout(a);nav.gravity=Gravity.CENTER;nav.setPadding(d(8,a),d(6,a),d(8,a),d(6,a));nav.background=if(dark)surface(a,Color.argb(220,7,19,48),Color.argb(225,21,10,55)) else surface(a,Color.WHITE,Color.rgb(246,241,255));nav.elevation=d(14,a).toFloat()
  val names=arrayOf("⌂\nHome","▣\nQBank","▤\nCards","▥\nLab","•••\nMore");names.indices.forEach{i->val n=tv(a,names[i],12f,if(i==0)ThemeManager.text(a) else ThemeManager.muted(a),true);n.gravity=Gravity.CENTER;if(i==0)n.background=GradientDrawable().apply{cornerRadius=d(22,a).toFloat();setColor(if(dark)Color.argb(85,35,150,255) else Color.argb(125,55,165,255));setStroke(d(1,a),Color.argb(180,60,205,255))};n.setOnClickListener{when(i){1->a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","qbank").addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP));2->pF?.performClick();3->pL?.performClick();4->a.startActivity(Intent(a,StudyToolsActivity::class.java))}};nav.addView(n,LinearLayout.LayoutParams(0,d(58,a),1f))}''')
])

edit("app/src/main/java/com/localqbank/library/RovexDailyStudyHub.kt",[
('    private const val SLOT_COUNT = 4','    private const val SLOT_COUNT = 4\n    const val CALENDAR_PERMISSION_REQUEST = 3401'),
('''        val plan = text(c, "PLAN MY DAY", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(100, 255, 255, 255), Color.argb(55, 110, 100, 255))
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            setOnClickListener { openPlanner(c) }
        }
        val mix = text(c, "MIX REVISION", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(100, 55, 210, 255), Color.argb(70, 145, 55, 235))
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            setOnClickListener {
                markStudy(c, 1)
                if (flashcards != null) flashcards.performClick() else openPlanner(c)
            }
        }
        row.addView(plan, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { rightMargin = dp(c, 6) })
        row.addView(mix, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { leftMargin = dp(c, 6) })
        box.addView(row, LinearLayout.LayoutParams(-1, dp(c, 54)).apply { topMargin = dp(c, 8) })''',
'''        val plan = text(c, "PLAN MY DAY", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(100, 255, 255, 255), Color.argb(55, 110, 100, 255))
            setPadding(dp(c, 8), 0, dp(c, 8), 0)
            setOnClickListener { openPlanner(c) }
        }
        val calendar = text(c, "VIEW CALENDAR", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(100, 55, 210, 255), Color.argb(70, 145, 55, 235))
            setPadding(dp(c, 8), 0, dp(c, 8), 0)
            setOnClickListener { openCalendar(c) }
        }
        val mix = text(c, "MIX REVISION", 12f).apply {
            gravity = Gravity.CENTER
            background = card(c, Color.argb(90, 0, 225, 185), Color.argb(65, 0, 100, 210))
            setPadding(dp(c, 8), 0, dp(c, 8), 0)
            setOnClickListener {
                markStudy(c, 1)
                if (flashcards != null) flashcards.performClick() else openPlanner(c)
            }
        }
        row.addView(plan, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { rightMargin = dp(c, 4) })
        row.addView(calendar, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { leftMargin = dp(c, 4); rightMargin = dp(c, 4) })
        row.addView(mix, LinearLayout.LayoutParams(0, dp(c, 48), 1f).apply { leftMargin = dp(c, 4) })
        box.addView(row, LinearLayout.LayoutParams(-1, dp(c, 54)).apply { topMargin = dp(c, 8) })'''),
('    private fun openPlanner(c: MainActivity) {',
'''    private fun openCalendar(c: MainActivity) {
        if (android.os.Build.VERSION.SDK_INT >= 23 && c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            c.requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), CALENDAR_PERMISSION_REQUEST)
            return
        }
        showCalendarEvents(c)
    }

    fun onCalendarPermissionResult(c: MainActivity, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) showCalendarEvents(c)
        else Toast.makeText(c, "Calendar access was not granted. Rovex can still export plans to your calendar app.", Toast.LENGTH_LONG).show()
    }

    private fun showCalendarEvents(c: MainActivity) {
        val now = Calendar.getInstance()
        val start = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val end = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val events = mutableListOf<Triple<Long,Long,String>>()
        val resolver = c.contentResolver
        val projection = arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_ID)
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, start)
        android.content.ContentUris.appendId(builder, end)
        runCatching {
            resolver.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cur ->
                val beginIx=cur.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIx=cur.getColumnIndex(CalendarContract.Instances.END)
                val titleIx=cur.getColumnIndex(CalendarContract.Instances.TITLE)
                val locIx=cur.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                while(cur.moveToNext()) {
                    val title=cur.getString(titleIx).orEmpty().ifBlank { "Untitled event" }
                    val loc=if(locIx>=0) cur.getString(locIx).orEmpty() else ""
                    events.add(Triple(cur.getLong(beginIx),cur.getLong(endIx),if(loc.isBlank()) title else "$title  •  $loc"))
                }
            }
        }.onFailure {
            Toast.makeText(c,"Could not read calendar: " + (it.message ?: "unknown error"),Toast.LENGTH_LONG).show()
            return
        }
        val body=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,18),0,dp(c,18),dp(c,4))}
        if(events.isEmpty()) body.addView(text(c,"No calendar events found for today. If your Google Calendar is synced on this device, its events will appear here after calendar access is granted.",13f,ThemeManager.text(c),false).apply{setPadding(0,dp(8),0,dp(8))})
        else {
            val fmt=SimpleDateFormat("HH:mm",Locale.getDefault())
            events.take(30).forEach { (b,e,title) ->
                val line=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,12),dp(9),dp(c,12),dp(9));background=card(c,Color.argb(70,50,170,255),Color.argb(55,135,70,230))}
                line.addView(text(c,fmt.format(java.util.Date(b))+"–"+fmt.format(java.util.Date(e)),11f,ThemeManager.accent(c),true))
                line.addView(text(c,title,13f,ThemeManager.text(c),true),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(c,3)})
                body.addView(line,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(c,6)})
            }
        }
        val dialog=AlertDialog.Builder(c).setTitle("Today's Calendar").setView(body).setNegativeButton("Close",null).setPositiveButton("Refresh",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.dismiss(); showCalendarEvents(c) } }
        dialog.show()
    }

    private fun openPlanner(c: MainActivity) {'''),
('            .setMessage("Create a focused plan. Export any block to your calendar without giving Rovex calendar-account access.")',
 '            .setMessage("Create your study plan, then view today’s synced calendar schedule inside Rovex or export a plan block to your calendar app. Google Calendar events appear when Google Calendar is synced on this device and calendar access is granted.")')
])

# Final compile-safety normalization for the newly added calendar/UI code.
hp=root/"app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
hs=hp.read_text()
if "import android.content.Intent" not in hs:
    hs=hs.replace("import android.content.Context","import android.content.Context\nimport android.content.Intent",1)
hp.write_text(hs)

# Repair Kotlin nav label escapes if a source variant materialized literal newlines.
hs=hs.replace('''val names=arrayOf("⌂
Home","▣
QBank","▤
Cards","▥
Lab","•••
More")''', r'''val names=arrayOf("⌂\nHome","▣\nQBank","▤\nCards","▥\nLab","•••\nMore")''')
hp.write_text(hs)

dp=root/"app/src/main/java/com/localqbank/library/RovexDailyStudyHub.kt"
ds=dp.read_text()
a=ds.find("    private fun showCalendarEvents(c: MainActivity) {")
b=ds.find("    private fun openPlanner",a)
if a >= 0 and b > a:
    block=ds[a:b]
    block=block.replace("showCalendarEvents(c: MainActivity)","showCalendarEvents(ctx: MainActivity)",1)
    block=re.sub(r"\bc\.", "ctx.", block)
    block=block.replace("dp(c,","dp(ctx,")
    block=block.replace("text(c,","text(ctx,")
    block=block.replace("card(c,","card(ctx,")
    block=block.replace("ThemeManager.text(c)","ThemeManager.text(ctx)")
    block=block.replace("ThemeManager.accent(c)","ThemeManager.accent(ctx)")
    block=block.replace("Toast.makeText(c,","Toast.makeText(ctx,")
    block=block.replace("showCalendarEvents(c)","showCalendarEvents(ctx)")
    block=block.replace("LinearLayout(ctx)","LinearLayout(ctx)")
    ds=ds[:a]+block+ds[b:]
    dp.write_text(ds)
else:
    raise SystemExit("UI6 calendar block not found")

print("Rovex UI6 fixes applied")
