#!/usr/bin/env python3
from pathlib import Path
import sys
P=Path(sys.argv[1]).resolve()
K=P/"app/src/main/java/com/localqbank/library"
def f(n):
 p=K/n
 if not p.is_file(): raise SystemExit("[638] missing "+str(p))
 return p
def rep(p,a,b,label,count=1):
 s=p.read_text()
 if s.count(a)<count: raise SystemExit("[638] missing anchor: "+label)
 p.write_text(s.replace(a,b,count))
g=P/"app/build.gradle.kts"; s=g.read_text()
if 'versionName = "8.3.637"' not in s or "versionCode = 723" not in s: raise SystemExit("[638] wrong version baseline")
g.write_text(s.replace('versionName = "8.3.637"','versionName = "8.3.638"',1).replace("versionCode = 723","versionCode = 724",1))
p=f("QuizLayoutBuilder.kt")
rep(p,"setPadding(dp(12), dp(5), dp(12), dp(6))","setPadding(dp(12), dp(1), dp(12), dp(1))","header padding")
rep(p,"background=RovexVisualSurfaceStyle.glass(context,18f,true)","background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)","header border")
rep(p,"top.addView(back, LinearLayout.LayoutParams(dp(42), dp(42))","top.addView(back, LinearLayout.LayoutParams(dp(38), dp(38))","back bounds")
rep(p,"top.addView(titleBox, LinearLayout.LayoutParams(0, dp(50), 1f))","top.addView(titleBox, LinearLayout.LayoutParams(0, dp(44), 1f))","title box")
rep(p,"root.addView(top, LinearLayout.LayoutParams(-1, dp(48)))","root.addView(top, LinearLayout.LayoutParams(-1, dp(46)))","header height")
rep(p,"setPadding(dp(18), dp(3), dp(18), dp(4))","setPadding(dp(18), 0, dp(18), 0)","progress padding")
rep(p,"background=RovexVisualSurfaceStyle.glass(context, 14f)","background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)","progress border")
rep(p,"infoRow.addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(6),0,dp(6),0) })","infoRow.addView(settingsButton, LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(4),0,dp(4),0) })","settings button")
rep(p,"infoRow.addView(jumpButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(10), 0, 0, 0) })","infoRow.addView(jumpButton, LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(6), 0, 0, 0) })","jump button")
rep(p,"root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(32)))","root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(34)))","progress height")
rep(p,"setPadding(dp(20), dp(14), dp(20), dp(50))","setPadding(dp(20), dp(14), dp(20), dp(18))","question trailing padding")
rep(p,"setPadding(dp(12), dp(8), dp(12), dp(10))","setPadding(dp(10), dp(4), dp(10), dp(4))","footer outer padding")
rep(p,"background=ThemeManager.backgroundDrawable(context)","background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)","footer scrim")
rep(p,"setPadding(dp(8), dp(7), dp(8), dp(7))","setPadding(dp(8), dp(5), dp(8), dp(5))","footer inner padding")
rep(p,"background = RovexVisualSurfaceStyle.glass(context,22f,true)","""background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(ThemeManager.elevated(context), ThemeManager.panel(context))).apply {
                cornerRadius = dp(18).toFloat()
                val accent = ThemeManager.accent(context)
                setStroke(dp(1), Color.argb(44, Color.red(accent), Color.green(accent), Color.blue(accent)))
            }""","footer glass outline")
rep(p,"root.addView(actionWrap, LinearLayout.LayoutParams(-1, dp(54)))","root.addView(actionWrap, LinearLayout.LayoutParams(-1, dp(56)))","footer height")
p=f("QuizActivity.kt")
old="""        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !ThemeManager.isDark(this@QuizActivity)
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }"""
rep(p,old,"        applyQuizImmersive()","initial immersive mode")
rep(p,"                setContentView(built)\n","                setContentView(built)\n                window.decorView.post { applyQuizImmersive() }\n","post content immersive mode")
old="""    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing && !isDestroyed) {
            runCatching {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
        }
    }"""
new="""    private fun applyQuizImmersive() {
        if (isFinishing || isDestroyed) return
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !ThemeManager.isDark(this@QuizActivity)
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing && !isDestroyed) window.decorView.post { applyQuizImmersive() }
    }"""
rep(p,old,new,"focus immersive callback")
rep(p,"        window.decorView.post { enforceThemeTextVisibility(window.decorView) }\n        sessionLifecycle.onResume()","        window.decorView.post { applyQuizImmersive(); enforceThemeTextVisibility(window.decorView) }\n        sessionLifecycle.onResume()","resume immersive callback")
rep(p,"        panel.requestApplyInsets()\n","        panel.requestApplyInsets()\n        window.decorView.post { applyQuizImmersive() }\n","Ben+AI fullscreen")
old="""                AdaptiveLayoutManager.install(this@QuizActivity, built, topExtraDp = 0, bottomExtraDp = 0) { m ->
                    val density=resources.displayMetrics.density
                    val compactHeight=m.heightPx/density < 620f
                    if (built.childCount >= 5) {
                        val top=built.getChildAt(0); val info=built.getChildAt(1)
                        top.layoutParams=top.layoutParams.apply{height=dp(if(compactHeight) 52 else if(m.windowClass==AdaptiveLayoutManager.WindowClass.EXPANDED) 60 else 56)}
                        info.layoutParams=info.layoutParams.apply{height=dp(if(compactHeight) 36 else 38)}
                        top.requestLayout(); info.requestLayout()
                    }
                }"""
new="""                AdaptiveLayoutManager.install(this@QuizActivity, built, topExtraDp = 0, bottomExtraDp = 0) { _ ->
                    if (built.childCount >= 5) {
                        val top = built.getChildAt(0)
                        val info = built.getChildAt(1)
                        top.layoutParams = top.layoutParams.apply { height = dp(46) }
                        info.layoutParams = info.layoutParams.apply { height = dp(34) }
                        top.requestLayout(); info.requestLayout()
                    }
                }"""
rep(p,old,new,"adaptive header override")
print("[638] quiz layout + fullscreen patches applied; version 8.3.638 / 724")
