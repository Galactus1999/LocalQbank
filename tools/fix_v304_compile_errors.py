#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/localqbank/library"

def patch(name, replacements):
    p = JAVA / name
    s = p.read_text()
    original = s
    for old, new in replacements:
        if old not in s:
            raise SystemExit(f"ROVEX_CI_PATCH_MISSING {name}: {old[:140]}")
        s = s.replace(old, new, 1)
    if s != original:
        p.write_text(s)

# Existing v304 Kotlin compile repairs.
patch("BackupActivity.kt", [
    ("setTextColor(ThemeManager.text(this));gravity=Gravity.CENTER;background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@BackupActivity))",
     "setTextColor(ThemeManager.text(this@BackupActivity));gravity=Gravity.CENTER;background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@BackupActivity))"),
    ("setTextColor(ThemeManager.text(this))};bar.addView(title",
     "setTextColor(ThemeManager.text(this@BackupActivity))};bar.addView(title"),
])

p = JAVA / "MainActivity.kt"
s = p.read_text()
# PerformanceRing: its onDraw receiver is the custom View, so use View.context.
old_line = 'override fun onDraw(c:android.graphics.Canvas){super.onDraw(c);val cx=width/2f;val cy=height/2f;val r=minOf(width,height)*.34f;p.color=ThemeManager.panel(this@MainActivity);c.drawCircle(cx,cy,r,p);p.color=if(accuracy>=80)ThemeManager.accent2(this@MainActivity)else ThemeManager.accent(this@MainActivity);c.drawArc(cx-r,cy-r,cx+r,cy+r,-90f,accuracy*3.6f,false,p);t.color=ThemeManager.text(this@MainActivity);t.textSize=25f*density;c.drawText("$accuracy%",cx,cy+8f*density,t);t.textSize=9.5f*density;t.color=ThemeManager.muted(this@MainActivity);c.drawText("ACCURACY",cx,cy+25f*density,t)}'
new_line = old_line.replace("this@MainActivity", "context")
if old_line in s:
    s = s.replace(old_line, new_line, 1)
elif "ThemeManager.panel(context)" not in s:
    raise SystemExit("ROVEX_CI_PATCH_MISSING MainActivity PerformanceRing")
# Settings helper for the new Home More menu.
marker = '    private fun openSearch(){'
if 'internal fun launchQBankImport()' not in s:
    if marker not in s: raise SystemExit("ROVEX_CI_PATCH_MISSING MainActivity openSearch")
    s = s.replace(marker, '''    internal fun launchQBankImport(){
        if(isFinishing || isDestroyed) return
        htmlImportLauncher.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml"))
    }

''' + marker, 1)
p.write_text(s)

# Home visual revolution: make the visible controls functional rather than leaving
# the original XML behind a 1x1 legacy container.
p = JAVA / "RovexHomeRevolution.kt"
s = p.read_text()
old = 'top.addView(tv(a,"◉",26f,ThemeManager.text(a)).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=1;setColor(Color.argb(70,60,180,255));setStroke(d(1,a),Color.argb(150,255,255,255))}},LinearLayout.LayoutParams(d(48,a),d(48,a)))'
new = 'top.addView(tv(a,"⚙",25f,ThemeManager.text(a),true).apply{gravity=Gravity.CENTER;background=GradientDrawable().apply{shape=1;setColor(Color.argb(70,60,180,255));setStroke(d(1,a),Color.argb(150,255,255,255))};contentDescription="Settings";setOnClickListener{a.startActivity(Intent(a,SettingsActivity::class.java))}},LinearLayout.LayoutParams(d(48,a),d(48,a)))'
if old in s: s = s.replace(old, new, 1)
elif 'contentDescription="Settings";setOnClickListener{a.startActivity(Intent(a,SettingsActivity::class.java))}' not in s:
    raise SystemExit("ROVEX_CI_PATCH_MISSING Home settings control")

old = 'box.setOnClickListener{target?.performClick()}'
new = 'box.setOnClickListener{when(title){"QBank"->a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","qbank"));"Flashcards"->a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","flashcards"));"Performance Lab"->target?.performClick();else->target?.performClick()}}'
if old in s: s = s.replace(old, new, 1)
elif 'RovexSectionDashboardActivity::class.java).putExtra("section","qbank")' not in s:
    raise SystemExit("ROVEX_CI_PATCH_MISSING Home feature routing")

old = 'val nav=LinearLayout(a);nav.gravity=Gravity.CENTER;nav.setPadding(d(8,a),d(6,a),d(8,a),d(6,a));nav.background=surface(a,Color.argb(220,7,19,48),Color.argb(225,21,10,55));nav.elevation=d(14,a).toFloat()\n  val names=arrayOf("⌂\\nHome","▣\\nQBank","▤\\nCards","▥\\nLab","•••\\nMore");names.indices.forEach{i->val n=tv(a,names[i],12f,if(i==0)ThemeManager.peacockText(a) else ThemeManager.text(a),true);n.gravity=Gravity.CENTER;if(i==0)n.background=GradientDrawable().apply{cornerRadius=d(22,a).toFloat();setColor(ThemeManager.peacockFill(a));setStroke(d(1,a),Color.argb(180,Color.red(ThemeManager.accent(a)),Color.green(ThemeManager.accent(a)),Color.blue(ThemeManager.accent(a))));};n.setOnClickListener{when(i){1->pQ?.performClick();2->pF?.performClick();3->pL?.performClick();4->a.startActivity(Intent(a,StudyToolsActivity::class.java))}};nav.addView(n,LinearLayout.LayoutParams(0,d(58,a),1f))}'
new = 'val nav=LinearLayout(a);nav.gravity=Gravity.CENTER;nav.setPadding(d(8,a),d(6,a),d(8,a),d(6,a));nav.background=surface(a,Color.argb(220,7,19,48),Color.argb(225,21,10,55));nav.elevation=d(14,a).toFloat()\n  val names=arrayOf("⌂\\nHome","▣\\nQBank","▤\\nCards","•••\\nMore");names.indices.forEach{i->val n=tv(a,names[i],12f,if(i==0)ThemeManager.peacockText(a) else ThemeManager.text(a),true);n.gravity=Gravity.CENTER;if(i==0)n.background=GradientDrawable().apply{cornerRadius=d(22,a).toFloat();setColor(ThemeManager.peacockFill(a));setStroke(d(1,a),Color.argb(180,Color.red(ThemeManager.accent(a)),Color.green(ThemeManager.accent(a)),Color.blue(ThemeManager.accent(a))));};n.setOnClickListener{when(i){1->a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","qbank"));2->a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","flashcards"));3->showMoreMenu(a)}};nav.addView(n,LinearLayout.LayoutParams(0,d(58,a),1f))}'
if old in s: s = s.replace(old, new, 1)
elif 'val names=arrayOf("⌂\\nHome","▣\\nQBank","▤\\nCards","•••\\nMore")' not in s:
    raise SystemExit("ROVEX_CI_PATCH_MISSING Home footer")

if 'private fun showMoreMenu(a:MainActivity)' not in s:
    marker = '\n fun apply(a:MainActivity){'
    if marker not in s: raise SystemExit("ROVEX_CI_PATCH_MISSING Home apply")
    helper = '''\n private fun showMoreMenu(a:MainActivity){\n  val dialog=android.app.Dialog(a)\n  val root=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(18,a),d(16,a),d(18,a),d(18,a));background=ThemeManager.dialogBg(a)}\n  root.addView(tv(a,"More",22f,ThemeManager.text(a),true),LinearLayout.LayoutParams(-1,d(42,a)))\n  root.addView(tv(a,"QBank management, study tools and app settings",12f,ThemeManager.muted(a),false),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})\n  fun item(label:String,sub:String,click:()->Unit){root.addView(tv(a,label+"\\n"+sub,15f,ThemeManager.text(a),true).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(d(14,a),0,d(10,a),0);background=ThemeManager.transparentSectionDrawable(a);setOnClickListener{dialog.dismiss();click()}},LinearLayout.LayoutParams(-1,d(62,a)).apply{bottomMargin=d(7,a)})}\n  item("▣  QBank Library","View subjects, QBanks, PYQs and tests"){a.startActivity(Intent(a,RovexSectionDashboardActivity::class.java).putExtra("section","qbank"))}\n  item("＋  Import QBank","Import HTML / QBank files"){a.launchQBankImport()}\n  item("⌘  Study Tools","Revision, weak areas and tests"){a.startActivity(Intent(a,StudyToolsActivity::class.java))}\n  item("⚙  Settings","Themes, Adaptive Engine, Ben and app controls"){a.startActivity(Intent(a,SettingsActivity::class.java))}\n  dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.window?.setLayout((a.resources.displayMetrics.widthPixels*.90f).toInt(),-2)\n }\n'''
    s = s.replace(marker, helper + marker, 1)
p.write_text(s)

# QBank dashboard: import button and main QBank access are visible in the QBank section itself.
p = JAVA / "RovexSectionDashboardActivity.kt"
s = p.read_text()
if 'ActivityResultContracts' not in s:
    s = s.replace('import android.widget.*', 'import android.widget.*\nimport androidx.activity.result.contract.ActivityResultContracts')
if 'qbankImportLauncher' not in s:
    marker='private var renderedTheme:String?=null\n'
    if marker not in s: raise SystemExit("ROVEX_CI_PATCH_MISSING Section renderedTheme")
    s=s.replace(marker, '''private var renderedTheme:String?=null\nprivate val qbankImportLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris ->\n    if(uris.isNotEmpty()) startActivity(Intent(this,HtmlImportActivity::class.java).putParcelableArrayListExtra("uris",ArrayList(uris)))\n}\n''',1)
old='private fun qbank(rows:List<Row>){\n    title("QBank","19 subjects with QBank / PYQ / Custom / test categories")\n    analyticsHero(rows)'
new='''private fun qbank(rows:List<Row>){\n    title("QBank","19 subjects with QBank / PYQ / Custom / test categories")\n    val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}\n    fun action(text:String,primary:Boolean=false,click:()->Unit)=TextView(this).apply{this.text=text;textSize=11.5f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setTextColor(if(primary)ThemeManager.peacockText(this@RovexSectionDashboardActivity) else ThemeManager.accent(this@RovexSectionDashboardActivity));background=GradientDrawable().apply{setColor(if(primary)ThemeManager.accent(this@RovexSectionDashboardActivity) else ThemeManager.elevated(this@RovexSectionDashboardActivity));cornerRadius=d(18).toFloat();setStroke(d(1),ThemeManager.accent(this@RovexSectionDashboardActivity))};setOnClickListener{click()}}\n    actions.addView(action("＋ IMPORT QBANK",true){qbankImportLauncher.launch(arrayOf("text/html","text/plain","application/xhtml+xml"))},LinearLayout.LayoutParams(0,d(46),1f).apply{rightMargin=d(5)})\n    actions.addView(action("⌂ MAIN QBANK"){sectionScroll.scrollTo(0,0)},LinearLayout.LayoutParams(0,d(46),1f).apply{leftMargin=d(5)})\n    renderTargetOrContent().addView(actions,LinearLayout.LayoutParams(-1,d(52)).apply{bottomMargin=d(10)})\n    analyticsHero(rows)'''
if old in s: s=s.replace(old,new,1)
elif '＋ IMPORT QBANK' not in s: raise SystemExit("ROVEX_CI_PATCH_MISSING QBank action row")
p.write_text(s)

p = JAVA / "RovexSectionDashboardActivity.kt"
s = p.read_text()
if "import android.graphics.drawable.GradientDrawable" not in s:
    s=s.replace("import android.graphics.Color", "import android.graphics.Color\nimport android.graphics.drawable.GradientDrawable")
p.write_text(s)

print("ROVEX_CI_COMPILE_AND_NAV_FIXES=APPLIED")
