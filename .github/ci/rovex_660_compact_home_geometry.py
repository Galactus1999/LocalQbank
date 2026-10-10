#!/usr/bin/env python3
"""Phase 660: compact Home geometry at the actual view/layout source."""
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
gp = P / "app/build.gradle.kts"
hp = P / "app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
tp = P / "app/src/androidTest/java/com/localqbank/library/RovexHomeCompactGeometryRegressionTest.kt"
for p in (gp, hp):
    if not p.is_file():
        raise SystemExit("[660] required file missing: " + str(p))
g = gp.read_text(encoding="utf-8")
if 'versionName = "8.3.657"' not in g or "versionCode = 743" not in g:
    raise SystemExit("[660] expected v8.3.657 / versionCode 743 baseline")
g = g.replace('versionName = "8.3.657"', 'versionName = "8.3.658"', 1)
g = g.replace("versionCode = 743", "versionCode = 744", 1)
gp.write_text(g, encoding="utf-8")

s = hp.read_text(encoding="utf-8")
repls = [
('val content=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14,a),d(14,a),d(14,a),d(92,a))}', 'val content=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(12,a),d(12,a),d(12,a),d(82,a))}'),
('top.addView(RovexHeaderCosmicView(a),LinearLayout.LayoutParams(d(48,a),d(44,a)).apply{rightMargin=d(4,a)})', 'top.addView(RovexHeaderCosmicView(a),LinearLayout.LayoutParams(d(42,a),d(40,a)).apply{rightMargin=d(4,a)})'),
('textSize=29f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(a));gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,d(48,a),1f)', 'textSize=27f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(a));gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,d(44,a),1f)'),
('LinearLayout.LayoutParams(d(48,a),d(48,a)))', 'LinearLayout.LayoutParams(d(42,a),d(42,a)))'),
('content.addView(header,LinearLayout.LayoutParams(-1,d(57,a)).apply{bottomMargin=d(9,a)})', 'content.addView(header,LinearLayout.LayoutParams(-1,d(52,a)).apply{bottomMargin=d(7,a)})'),
('setPadding(d(18,a),d(15,a),d(12,a),d(15,a))', 'setPadding(d(14,a),d(9,a),d(10,a),d(9,a))'),
('contentDescription = "Clinical Day study cockpit"', 'tag = "rovex_home_clinical_hero"\n                contentDescription = "Clinical Day study cockpit"'),
('tv(a,"Build momentum, one question at a time.",19f,ThemeManager.text(a),true)', 'tv(a,"Build momentum, one question at a time.",16f,ThemeManager.text(a),true)'),
('tv(a,"Your saved answer history drives the dashboard.",11.5f,ThemeManager.muted(a),false)', 'tv(a,"Your saved answer history drives the dashboard.",10.5f,ThemeManager.muted(a),false)'),
('rightMargin=d(104,a);gravity=Gravity.START or Gravity.CENTER_VERTICAL', 'rightMargin=d(78,a);gravity=Gravity.START or Gravity.CENTER_VERTICAL'),
('FrameLayout.LayoutParams(d(92,a),d(92,a),Gravity.END or Gravity.CENTER_VERTICAL)', 'FrameLayout.LayoutParams(d(64,a),d(64,a),Gravity.END or Gravity.CENTER_VERTICAL)'),
('LinearLayout.LayoutParams(-1,d(126,a)).apply{bottomMargin=d(13,a)}', 'LinearLayout.LayoutParams(-1,d(88,a)).apply{bottomMargin=d(9,a)}'),
('contentDescription="rovexHomeSearch";setPadding(d(16,a),0,d(16,a),0)', 'tag="rovex_home_search";contentDescription="rovexHomeSearch";setPadding(d(12,a),0,d(12,a),0)'),
('LinearLayout.LayoutParams(-1,d(52,a)).apply{bottomMargin=d(13,a)}', 'LinearLayout.LayoutParams(-1,d(46,a)).apply{bottomMargin=d(9,a)}'),
('val online=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(15,a),d(12,a),d(15,a),d(11,a));background=card(a,0);', 'val online=LinearLayout(a).apply{tag="rovex_home_online";orientation=LinearLayout.VERTICAL;setPadding(d(12,a),d(8,a),d(12,a),d(7,a));background=card(a,0);'),
('LinearLayout.LayoutParams(d(38,a),d(38,a))', 'LinearLayout.LayoutParams(d(32,a),d(32,a))'),
('tv(a,"Rovex Online",16f,ThemeManager.text(a),true)', 'tv(a,"Rovex Online",14.5f,ThemeManager.text(a),true)'),
('"Optional • friends, streak & synced progress",11f,ThemeManager.muted(a),false)', '"Optional • friends, streak & synced progress",10.5f,ThemeManager.muted(a),false)'),
('tv(a,"›",27f,ThemeManager.accent(a),true)', 'tv(a,"›",22f,ThemeManager.accent(a),true)'),
('LinearLayout.LayoutParams(d(30,a),d(34,a))', 'LinearLayout.LayoutParams(d(26,a),d(30,a))'),
('LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})\n\n        val quote=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16,a),d(13,a),d(16,a),d(13,a));background=card(a,2)}', 'LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(8,a)})\n\n        val quote=LinearLayout(a).apply{tag="rovex_home_daily_motivation";orientation=LinearLayout.VERTICAL;setPadding(d(12,a),d(8,a),d(12,a),d(8,a));background=card(a,2)}'),
('quote.addView(RovexColorFlowTextView(a).apply{text="“$dailyQuote”";textSize=16.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(a));tag="rovex_home_daily_quote";includeFontPadding=false},LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(6,a)})', 'quote.addView(tv(a,"“$dailyQuote”",13.5f,ThemeManager.text(a),true).apply{tag="rovex_home_daily_quote";includeFontPadding=false},LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(4,a)})'),
('content.addView(withMotionSurface(quote,a,"daily-motivation",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(12,a)})', 'content.addView(withMotionSurface(quote,a,"daily-motivation",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(9,a)})'),
('val progress=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(16,a),d(14,a),d(16,a),d(14,a));background=card(a,2);elevation=d(4,a).toFloat()}', 'val progress=LinearLayout(a).apply{tag="rovex_home_today_progress";orientation=LinearLayout.VERTICAL;setPadding(d(12,a),d(10,a),d(12,a),d(10,a));background=card(a,2);elevation=d(3,a).toFloat()}'),
('val progressTitle=tv(a,"Today\'s Progress",18f,ThemeManager.text(a),true)', 'val progressTitle=tv(a,"Today\'s Progress",15.5f,ThemeManager.text(a),true)'),
('val progressValue=tv(a,"Loading calibrated progress…",29f,ThemeManager.text(a),true)', 'val progressValue=tv(a,"Loading calibrated progress…",24f,ThemeManager.text(a),true)'),
('LinearLayout.LayoutParams(-1,d(120,a)).apply{topMargin=d(7,a)}', 'LinearLayout.LayoutParams(-1,d(76,a)).apply{topMargin=d(5,a)}'),
('progress.addView(continueButton,LinearLayout.LayoutParams(-1,d(38,a)).apply{topMargin=d(8,a)})', 'progress.addView(continueButton,LinearLayout.LayoutParams(-1,d(34,a)).apply{topMargin=d(6,a)})'),
('content.addView(withMotionSurface(progress,a,"today-progress",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13,a)})', 'content.addView(withMotionSurface(progress,a,"today-progress",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(9,a)})'),
('setPadding(d(14,a),d(13,a),d(10,a),d(10,a))', 'setPadding(d(11,a),d(9,a),d(8,a),d(8,a))'),
('tv(a,title,18f,ThemeManager.text(a),true)', 'tv(a,title,16f,ThemeManager.text(a),true)'),
('tv(a,sub,11.5f,ThemeManager.muted(a),false)', 'tv(a,sub,10.5f,ThemeManager.muted(a),false)'),
('lp.height=d(160,a)', 'lp.height=d(128,a)'),
('content.addView(qscroll,LinearLayout.LayoutParams(-1,d(78,a)).apply{bottomMargin=d(13,a)})', 'content.addView(qscroll,LinearLayout.LayoutParams(-1,d(68,a)).apply{bottomMargin=d(10,a)})'),
]
for old, new in repls:
    n=s.count(old)
    if n != 1:
        raise SystemExit("[660] anchor count " + str(n) + ": " + old[:80])
    s=s.replace(old,new,1)
old='iconFrame.addView(RovexClinicalFeatureIconView(a, iconKind), FrameLayout.LayoutParams(-1,-1).apply { setMargins(d(10,a),d(10,a),d(10,a),d(10,a)) })\n            col.addView(iconFrame,LinearLayout.LayoutParams(d(50,a),d(50,a)).apply{bottomMargin=d(8,a)})'
new='iconFrame.addView(RovexClinicalFeatureIconView(a, iconKind), FrameLayout.LayoutParams(-1,-1).apply { setMargins(d(8,a),d(8,a),d(8,a),d(8,a)) })\n            col.addView(iconFrame,LinearLayout.LayoutParams(d(40,a),d(40,a)).apply{bottomMargin=d(6,a)})'
if s.count(old)!=1:
    raise SystemExit("[660] feature icon anchor missing")
s=s.replace(old,new,1)
hp.write_text(s,encoding="utf-8")
if tp.exists():
    raise SystemExit("[660] refusing to overwrite existing geometry test")
tp.write_text('''package com.localqbank.library

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RovexHomeCompactGeometryRegressionTest {
    @Test
    fun primaryHomeCardsStayCompactAndMotivationUsesStableTypography() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val oldTheme = ThemeManager.get(context)
        try {
            ThemeManager.set(context, ThemeManager.LIGHT)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot)
                        ?: error("Home dashboard root missing")
                    val density = activity.resources.displayMetrics.density
                    fun assertHeight(tag: String, maximumDp: Float) {
                        val view = root.findViewWithTag<View>(tag)
                            ?: error("Missing Home geometry target: " + tag)
                        val heightDp = view.measuredHeight / density
                        check(heightDp > 0f) { tag + " was not measured" }
                        check(heightDp <= maximumDp) {
                            tag + " is oversized: " + String.format("%.1f", heightDp) +
                                "dp > " + maximumDp + "dp"
                        }
                    }
                    assertHeight("rovex_home_clinical_hero", 96f)
                    assertHeight("rovex_home_search", 50f)
                    assertHeight("rovex_home_online", 70f)
                    assertHeight("rovex_home_daily_motivation", 88f)
                    assertHeight("rovex_home_today_progress", 225f)
                    assertHeight("modern_feature_qbank", 132f)
                    assertHeight("modern_feature_flashcards", 132f)
                    val quote = root.findViewWithTag<TextView>("rovex_home_daily_quote")
                        ?: error("Daily motivation text missing")
                    check(quote !is RovexColorFlowTextView) {
                        "Daily motivation must use stable theme text, not animated flowing text gradients"
                    }
                }
            }
        } finally {
            ThemeManager.set(context, oldTheme)
        }
    }
}
''',encoding="utf-8")
g=gp.read_text(encoding="utf-8")
s=hp.read_text(encoding="utf-8")
if 'versionName = "8.3.658"' not in g or "versionCode = 744" not in g:
    raise SystemExit("[660] version postcondition failed")
print("[660] compact Home geometry and measured bounds regression installed")
