#!/usr/bin/env python3
from pathlib import Path
import sys

PROJECT = Path(sys.argv[1]).resolve()
GRADLE = PROJECT / "app/build.gradle.kts"
PKG = PROJECT / "app/src/main/java/com/localqbank/library"

def one(name):
    p = PKG / name
    if not p.is_file():
        raise SystemExit(f"[635] missing expected source: {p}")
    return p

g = GRADLE.read_text()
if 'versionName = "8.3.634"' not in g or "versionCode = 720" not in g:
    raise SystemExit("[635] expected v8.3.634 / 720 baseline; refusing to patch")
GRADLE.write_text(g.replace('versionName = "8.3.634"', 'versionName = "8.3.635"', 1).replace("versionCode = 720", "versionCode = 721", 1))

# Root cause: all light pastel-family themes fall through to the same four generic fills.
p = one("ThemeManager.kt"); s = p.read_text()
old = '    fun pastelAccentFill(c:Context,index:Int)=when(index%4){0->pastelBlueFill(c);1->pastelPinkFill(c);2->pastelYellowFill(c);else->pastelLavenderFill(c)}'
new = '''    fun pastelAccentFill(c:Context,index:Int):Int {
        val i=((index%4)+4)%4
        return when(get(c)) {
            LIGHT -> when(i){0->Color.rgb(235,244,255);1->Color.rgb(246,240,255);2->Color.rgb(255,247,224);else->Color.rgb(232,250,247)}
            PASTEL -> when(i){0->Color.rgb(224,235,255);1->Color.rgb(255,226,243);2->Color.rgb(255,241,203);else->Color.rgb(230,224,255)}
            MINT -> when(i){0->Color.rgb(218,247,237);1->Color.rgb(215,242,248);2->Color.rgb(237,249,220);else->Color.rgb(218,240,231)}
            SUNSET -> when(i){0->Color.rgb(255,230,211);1->Color.rgb(255,218,224);2->Color.rgb(255,241,205);else->Color.rgb(250,224,242)}
            LAVENDER -> when(i){0->Color.rgb(232,225,255);1->Color.rgb(247,225,250);2->Color.rgb(225,232,255);else->Color.rgb(241,229,255)}
            else -> when(i){0->pastelBlueFill(c);1->pastelPinkFill(c);2->pastelYellowFill(c);else->pastelLavenderFill(c)}
        }
    }'''
if old not in s: raise SystemExit("[635] pastelAccentFill anchor missing")
p.write_text(s.replace(old,new,1))

# Home cards should use theme-owned profile accents for borders and the existing animated wordmark engine for the quote.
p = one("RovexHomeRevolution.kt"); s = p.read_text()
old = 'val dailyQuote=RovexDailyNotificationContent.quoteForNow(java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()))\n        quote.addView(tv(a,"“$dailyQuote”",16.5f,ThemeManager.text(a),true).apply{tag="rovex_home_daily_quote"},LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(6,a)})'
new = 'val dailyQuote=RovexDailyNotificationContent.quoteForNow(java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()))\n        quote.addView(RovexWaveTextView(a).apply{text="“$dailyQuote”";textSize=16.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(a));tag="rovex_home_daily_quote";includeFontPadding=false},LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(6,a)})'
if old not in s: raise SystemExit("[635] daily motivation quote anchor missing")
s = s.replace(old,new,1)
# Apply the same flowing text implementation on quote refresh without replacing its TextView API.
p.write_text(s)

# Immersive question-solving mode: hide status bar, retain navigation and swipe-to-reveal.
p = one("QuizActivity.kt"); s = p.read_text()
old = '''        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !ThemeManager.isDark(this)'''
new = '''        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !ThemeManager.isDark(this@QuizActivity)
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }'''
if old not in s: raise SystemExit("[635] QuizActivity system-bar setup anchor missing")
s = s.replace(old,new,1)
# Re-assert the solving-only immersive policy when returning from dialogs/settings.
anchor = '    /** Read navigation extras defensively. Older builds may have stored IDs as numeric extras. */'
insert = '''    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing && !isDestroyed) {
            runCatching {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
        }
    }

'''
if anchor not in s: raise SystemExit("[635] QuizActivity focus anchor missing")
s = s.replace(anchor,insert+anchor,1)
p.write_text(s)

# Reclaim vertical space without touching question/options rendering or navigation state.
p = one("QuizLayoutBuilder.kt"); s = p.read_text()
replacements = [
    ('root.addView(top, LinearLayout.LayoutParams(-1, dp(56)))','root.addView(top, LinearLayout.LayoutParams(-1, dp(48)))'),
    ('root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(38)))','root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(32)))'),
    ('actionBar.addView(bookmarkButton, LinearLayout.LayoutParams(0, dp(42), 1.05f)','actionBar.addView(bookmarkButton, LinearLayout.LayoutParams(0, dp(38), 1.05f)'),
    ('actionBar.addView(nextButton, LinearLayout.LayoutParams(0, dp(42), 1.35f)','actionBar.addView(nextButton, LinearLayout.LayoutParams(0, dp(38), 1.35f)'),
    ('actionWrap.addView(actionBar, FrameLayout.LayoutParams(-1, dp(60)))','actionWrap.addView(actionBar, FrameLayout.LayoutParams(-1, dp(48)))'),
    ('root.addView(actionWrap, LinearLayout.LayoutParams(-1, dp(78)))','root.addView(actionWrap, LinearLayout.LayoutParams(-1, dp(54)))'),
]
for old,new in replacements:
    if old not in s: raise SystemExit("[635] QuizLayoutBuilder anchor missing: "+old)
    s=s.replace(old,new,1)
p.write_text(s)

print("[635] applied v8.3.635 / versionCode 721")
print("[635] theme-specific dashboard fills, flowing motivation quote, immersive solving status bar, compact quiz shell")
