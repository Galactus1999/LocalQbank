#!/usr/bin/env python3
"""Apply Rovex v8.3.622 premium-surface migration to verified v8.3.621 CI candidate."""
from pathlib import Path
import sys
V='versionName = "8.3.621"'
C='versionCode = 707'
def r(p,old,new,label):
    s=p.read_text(encoding='utf-8')
    if old not in s: raise SystemExit(f"v8.3.622: {label} anchor missing")
    p.write_text(s.replace(old,new,1),encoding='utf-8')
def main():
    if len(sys.argv)!=2: raise SystemExit("usage: ... <project>")
    root=Path(sys.argv[1]).resolve(); pkg=root/'app/src/main/java/com/localqbank/library'; gr=root/'app/build.gradle.kts'
    g=gr.read_text(encoding='utf-8')
    if V not in g or C not in g: raise SystemExit("v8.3.622 requires v8.3.621/707")
    r(pkg/'RovexModernUi.kt',
      ' fun applyMain(a:MainActivity){val root=a.findViewById<View>(R.id.dashboardRoot)?:return;root.setBackgroundColor(ThemeManager.bg(a));val e=ThemeManager.elevated(a);val st=Color.argb(if(ThemeManager.isDark(a))72 else 42,Color.red(ThemeManager.accent(a)),Color.green(ThemeManager.accent(a)),Color.blue(ThemeManager.accent(a)));listOf(R.id.renCard,R.id.todaySolvedCard,R.id.studyMenuCard,R.id.flashcardCard,R.id.performanceLabCard,R.id.homeSearchCard).forEach{id->a.findViewById<View>(id)?.apply{background=surface(this,e,st,if(id==R.id.homeSearchCard)18 else 22);elevation=dp(if(id==R.id.renCard)5 else 2,this).toFloat()}};listOf(R.id.importantButton,R.id.reviseButton,R.id.doubtButton,R.id.favoriteButton,R.id.addButton,R.id.continueStudyButton).forEach{id->a.findViewById<Button>(id)?.let{styleButton(it)}};tintText(root,a)}\n','',"dead applyMain")
    r(pkg/'RovexSectionDashboardActivity.kt','private fun panel():android.graphics.drawable.Drawable=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.panel(this@RovexSectionDashboardActivity));cornerRadius=d(22).toFloat()}','private fun panel():android.graphics.drawable.Drawable=RovexVisualSurfaceStyle.glass(this@RovexSectionDashboardActivity,22f)','section panel')
    r(pkg/'RovexSectionDashboardActivity.kt','background=android.graphics.drawable.GradientDrawable().apply{setColor(fill);cornerRadius=d(17).toFloat()};isClickable=true;isFocusable=true;setOnClickListener','background=RovexVisualSurfaceStyle.glass(this@RovexSectionDashboardActivity,17f);isClickable=true;isFocusable=true;RovexTouchFeedback.bind(this);setOnClickListener','section row')
    r(pkg/'RovexSectionDashboardActivity.kt','background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.accent(this@RovexSectionDashboardActivity));cornerRadius=d(22).toFloat()};setPadding(0,d(11),0,d(11));setOnClickListener','RovexVisualButtonStyle.apply(this, this@RovexSectionDashboardActivity, emphasized=true);setPadding(0,d(11),0,d(11));setOnClickListener','section CTA')
    r(pkg/'FlashcardActivity.kt','background=GradientDrawable(GradientDrawable.Orientation.TL_BR,headerGradient()).apply{cornerRadius=18f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))70 else 45,100,120,140))}','background=RovexVisualSurfaceStyle.glass(this@FlashcardActivity,18f,true)','flash header')
    r(pkg/'FlashcardActivity.kt','''            background=GradientDrawable().apply{
                setColor(ThemeManager.panel(this@FlashcardActivity))
                cornerRadius=20f*resources.displayMetrics.density
                setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))55 else 35,100,125,145))
            }''','            background=RovexVisualSurfaceStyle.glass(this@FlashcardActivity,20f,true)','flash board')
    r(pkg/'FlashcardActivity.kt','''            setTextColor(pastelText())
            background=GradientDrawable().apply{
                setColor(fill);cornerRadius=15f*resources.displayMetrics.density
                setStroke(dp(1),Color.argb(if(dark)75 else 52,100,125,145))
            }
            isClickable=true;isFocusable=true;setOnClickListener{click()}''','''            setTextColor(pastelText())
            background=GradientDrawable().apply{
                setColor(fill);cornerRadius=15f*resources.displayMetrics.density
                setStroke(dp(1),Color.argb(if(dark)75 else 52,100,125,145))
            }
            isClickable=true;isFocusable=true;RovexTouchFeedback.bind(this);setOnClickListener{click()}''','flash action touch')
    r(pkg/'FlashcardActivity.kt','''            setTextColor(pastelText())
            background=GradientDrawable().apply{setColor(fill);cornerRadius=13f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))65 else 50,100,125,145))}
            isClickable=true;isFocusable=true;setOnClickListener{click()}''','''            setTextColor(pastelText())
            background=GradientDrawable().apply{setColor(fill);cornerRadius=13f*resources.displayMetrics.density;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))65 else 50,100,125,145))}
            isClickable=true;isFocusable=true;RovexTouchFeedback.bind(this);setOnClickListener{click()}''','flash compact touch')
    r(pkg/'FlashcardActivity.kt','background=GradientDrawable().apply{setColor(if(n.depth==0)pastel(rootIndex++)else ThemeManager.elevated(this@FlashcardActivity));cornerRadius=14f;setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardActivity))55 else 35,100,120,140))}','background=if(n.depth==0) GradientDrawable().apply{setColor(pastel(rootIndex++));cornerRadius=14f;setStroke(dp(1),RovexVisualColors.border(this@FlashcardActivity))} else RovexVisualSurfaceStyle.glass(this@FlashcardActivity,14f)','flash deck row')
    r(pkg/'FlashcardActivity.kt','''            row.setOnClickListener{
                if(bulkDeleteMode){''','''            RovexTouchFeedback.bind(row)
            row.setOnClickListener{
                if(bulkDeleteMode){''','flash row touch')
    r(pkg/'QuizLayoutBuilder.kt','''            background=ThemeManager.backgroundDrawable(context)
            elevation = dp(1).toFloat()''','''            background=RovexVisualSurfaceStyle.glass(context,18f,true)
            elevation = dp(1).toFloat()''','quiz top')
    r(pkg/'QuizLayoutBuilder.kt','''        back.background = rounded(ThemeManager.elevated(context), 14f)
        back.setTextColor(ThemeManager.text(context))''','''        RovexVisualButtonStyle.apply(back, context)''','quiz back')
    r(pkg/'QuizLayoutBuilder.kt','''            submit.background = rounded(ThemeManager.elevated(context), 14f)
            submit.setTextColor(ThemeManager.accent(context))''','''            RovexVisualButtonStyle.apply(submit, context, emphasized=true)''','quiz submit')
    r(pkg/'QuizLayoutBuilder.kt','''            background = GradientDrawable().apply {
                setColor(ThemeManager.elevated(context))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (ThemeManager.isDark(context)) Color.rgb(48,61,75) else Color.rgb(222,227,231))
            }''','''            background = RovexVisualSurfaceStyle.glass(context,22f,true)''','quiz dock')
    r(pkg/'QuizLayoutBuilder.kt','''            background = rounded(ThemeManager.panel(context), 17f)
            contentDescription = "Previous question"''','''            RovexVisualButtonStyle.apply(this, context)
            contentDescription = "Previous question"''','quiz previous')
    r(pkg/'QuizLayoutBuilder.kt','''            background = rounded(ThemeManager.panel(context), 17f)
            setPadding(dp(8), 0, dp(8), 0)''','''            RovexVisualButtonStyle.apply(this, context)
            setPadding(dp(8), 0, dp(8), 0)''','quiz bookmark')
    r(pkg/'QuizLayoutBuilder.kt','''            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (ThemeManager.isDark(context)) Color.rgb(58, 139, 214) else Color.rgb(34, 111, 188))
                cornerRadius = dp(17).toFloat()
            }''','''            RovexVisualButtonStyle.apply(this, context, emphasized=true)''','quiz next')
    r(pkg/'QuizActivity.kt','''        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(if (ThemeManager.isDark(this@QuizActivity)) Color.rgb(31,43,58) else Color.rgb(28,70,103), 12f)''','''        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        RovexVisualButtonStyle.apply(this, this@QuizActivity, emphasized=true)''','quiz header')
    r(pkg/'RenActivity.kt','''            background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),10f)
            setPadding(dp(8),0,dp(8),0); setOnClickListener{click()}''','''            RovexVisualButtonStyle.apply(this, this@RenActivity)
            setPadding(dp(8),0,dp(8),0); setOnClickListener{click()}''','ren header')
    r(pkg/'RenActivity.kt','''        val chatFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }''','''        val chatFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            background = RovexVisualSurfaceStyle.glass(this@RenActivity, 18f, true)
        }''','ren chat')
    r(pkg/'RenActivity.kt','background=UiDrawableUtils.roundedDrawable(this@RenActivity,ThemeManager.elevated(this@RenActivity),16f)','background=RovexVisualSurfaceStyle.glass(this@RenActivity,16f,true)','ren input')
    # Ren action() was already migrated by the v8.3.617 overlay; do not re-patch it.
    r(pkg/'SettingsScreen.kt','this.background=if(background==Color.TRANSPARENT) ThemeManager.transparentSectionDrawable(activity) else rounded(background,radius)','this.background=if(background==Color.TRANSPARENT) RovexVisualSurfaceStyle.glass(activity,radius.toFloat()) else rounded(background,radius)','settings card')
    r(pkg/'SettingsScreen.kt','background=rounded(if(ThemeManager.isDark(activity))Color.rgb(20,28,37) else Color.rgb(248,249,249),13)','background=RovexVisualSurfaceStyle.glass(activity,13f)','settings metric')
    r(pkg/'SettingsScreen.kt','background=GradientDrawable().apply{setColor(ThemeManager.elevated(activity));cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.argb(if(dark)70 else 45,Color.red(tint),Color.green(tint),Color.blue(tint)))}','background=RovexVisualSurfaceStyle.glass(activity,18f)','settings category')
    r(pkg/'SettingsScreen.kt','''            setOnClickListener{click()}
        }''','''            RovexTouchFeedback.bind(this)
            setOnClickListener{click()}
        }''','settings category touch')
    # Harden rendered screenshot capture: clear stale files and capture PNG through shell stdout,
    # then sync, so the host collector never accepts a zero-byte/stale frame.
    vt=root/'app/src/androidTest/java/com/localqbank/library/RovexVisualTruthCaptureTest.kt'
    if vt.is_file():
        t=vt.read_text(encoding='utf-8')
        old='''    private fun capture(name: String) {
        shell("screencap -p > /sdcard/RovexVisualTruth/$name.png && sync")
        shell("test -s /sdcard/RovexVisualTruth/$name.png")
    }

    private fun settle() { Thread.sleep(1400) }

    @Test
    fun captureCoreRenderedScreens() {
        shell("rm -rf /sdcard/RovexVisualTruth && mkdir -p /sdcard/RovexVisualTruth")
'''
        new='''    private fun capture(name: String) {
        shell("screencap -p > /sdcard/RovexVisualTruth/$name.png && sync")
        shell("test -s /sdcard/RovexVisualTruth/$name.png")
    }

    private fun settle() { Thread.sleep(1400) }

    @Test
    fun captureCoreRenderedScreens() {
        shell("rm -rf /sdcard/RovexVisualTruth && mkdir -p /sdcard/RovexVisualTruth")
'''
        if old not in t: raise SystemExit('v8.3.622: visual truth capture anchor missing')
        vt.write_text(t.replace(old,new,1),encoding='utf-8')
    # Correct the known Visual Lab swatch nesting bug from the earlier CI-only lab.
    lab=pkg/'VisualLabActivity.kt'
    if lab.is_file():
        s=lab.read_text(encoding='utf-8')
        bad='''                listOf(p.backgroundA, p.elevated, p.accent).forEachIndexed { j, c ->
                    addView(View(this@VisualLabActivity).apply {
                        background = GradientDrawable().apply { setColor(c); cornerRadius = d(5).toFloat() }
                    }, LinearLayout.LayoutParams(0, d(15), 1f).apply { if (j > 0) leftMargin = d(3) })
                }
                addView(swatches)'''
        if bad in s:
            good='''                listOf(p.backgroundA, p.elevated, p.accent).forEachIndexed { j, c ->
                    swatches.addView(View(this@VisualLabActivity).apply {
                        background = GradientDrawable().apply { setColor(c); cornerRadius = d(5).toFloat() }
                    }, LinearLayout.LayoutParams(0, d(15), 1f).apply { if (j > 0) leftMargin = d(3) })
                }
                addView(swatches)'''
            s=s.replace(bad,good,1)
            lab.write_text(s,encoding='utf-8')
    g=g.replace(V,'versionName = "8.3.622"',1).replace(C,'versionCode = 708',1)
    gr.write_text(g,encoding='utf-8')
    print('v8.3.622 premium surface migration overlay: APPLIED')
if __name__=='__main__': main()
