from pathlib import Path
import re
R=Path(__file__).resolve().parent
B=R/'app/src/main/java/com/localqbank/library'
p=B/'ThemeManager.kt'
s=p.read_text()
s=s.replace('const val DARK="legacy_dark"; const val SEPIA="legacy_sepia"; const val OBSIDIAN_NIGHT="legacy_obsidian"; const val MIDNIGHT="legacy_midnight"; const val COSMOS="legacy_cosmos"; const val AVATAR="legacy_avatar"','')
p.write_text(s)
legacy={
 'ThemeManager.DARK':'ThemeManager.PASTEL',
 'ThemeManager.SEPIA':'ThemeManager.SUNSET',
 'ThemeManager.OBSIDIAN_NIGHT':'ThemeManager.AMOLED',
 'ThemeManager.MIDNIGHT':'ThemeManager.AMOLED',
 'ThemeManager.COSMOS':'ThemeManager.AMOLED',
 'ThemeManager.AVATAR':'ThemeManager.LAVENDER'
}
for f in B.glob('*.kt'):
 s=f.read_text()
 for a,b in legacy.items(): s=s.replace(a,b)
 f.write_text(s)
# Make the global pass style semantic cards/sheets/nav and image-icon containers.
p=B/'RovexThemeEngine.kt';s=p.read_text()
old='is ProgressBar->{v.progressTintList=ColorStateList.valueOf(ThemeManager.accent(a))};is TextView->text(v,a);is ViewGroup->if(depth==0)v.background=ThemeManager.backgroundDrawable(a)'
new='is ProgressBar->{v.progressTintList=ColorStateList.valueOf(ThemeManager.accent(a))};is ImageView->image(v,a);is TextView->text(v,a);is ViewGroup->group(v,a,depth)'
s=s.replace(old,new)
needle='    private fun text(v:TextView,a:Activity){'
helper='''    private fun group(v:ViewGroup,a:Activity,depth:Int){
        if(depth==0){v.background=ThemeManager.backgroundDrawable(a);return}
        val id=runCatching{v.resources.getResourceEntryName(v.id).lowercase()}.getOrDefault("")
        val cn=v.javaClass.name.lowercase()
        val semantic=id.contains("card")||id.contains("panel")||id.contains("sheet")||id.contains("dialog")||id.contains("hero")||id.contains("nav")||id.contains("pill")||id.contains("toolbar")||id.contains("search")||cn.contains("cardview")||cn.contains("bottomsheet")
        if(semantic && !id.contains("scroll") && !id.contains("list")){
            val ac=ThemeManager.accent(a)
            v.background=surface(v,ThemeManager.elevated(a),ThemeManager.panel(a),Color.argb(if(ThemeManager.isDark(a))150 else 70,Color.red(ac),Color.green(ac),Color.blue(ac)),if(id.contains("nav")||id.contains("pill"))26 else 22)
            v.elevation=dp(if(id.contains("nav")||id.contains("hero"))8 else 3,v).toFloat()
            v.clipToOutline=true
        }
    }
    private fun image(v:ImageView,a:Activity){
        val id=runCatching{v.resources.getResourceEntryName(v.id).lowercase()}.getOrDefault("")
        if(id.contains("icon")||id.contains("avatar")||id.contains("hero")||id.contains("medical")||id.contains("cube")){
            val ac=ThemeManager.accent(a)
            v.background=surface(v,ThemeManager.elevated(a),ThemeManager.peacockFill(a),Color.argb(100,Color.red(ac),Color.green(ac),Color.blue(ac)),18)
            v.elevation=dp(4,v).toFloat();v.clipToOutline=true
        }
    }
'''
s=s.replace(needle,helper+needle)
p.write_text(s)
# version
g=R/'app/build.gradle.kts';s=g.read_text().replace('versionCode = 395','versionCode = 396',1).replace('versionName = "8.3.301"','versionName = "8.3.302"',1);g.write_text(s)
# audit marker
(B/'RovexThemeAuditMarker.kt').write_text('''package com.localqbank.library
object RovexThemeAuditMarker { const val SYSTEM="ROVEX_UI_302"; const val USER_THEMES="LIGHT,PASTEL,MINT,SUNSET,LAVENDER,AMOLED" }''')
