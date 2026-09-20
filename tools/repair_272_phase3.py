from pathlib import Path
import re

root = Path(".")
app = root / "app"
print("PHASE3: start", flush=True)

ren = app / "src/main/java/com/localqbank/library/RenActivity.kt"
s = ren.read_text(encoding="utf-8")
if "openMatchButton = TextView(this)" not in s:
    needle = "        root.addView(row)\n\n        val baseLeft=root.paddingLeft; val baseTop=root.paddingTop"
    if needle not in s:
        raise SystemExit("PHASE3 FAIL: Ren insertion point missing")
    repl = """        root.addView(row)

        openMatchButton = TextView(this).apply {
            text = "OPEN MATCHING QUESTIONS"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.bg(this@RenActivity))
            background = UiDrawableUtils.roundedDrawable(this@RenActivity, ThemeManager.accent(this@RenActivity), 12f)
            setPadding(dp(10), 0, dp(10), 0)
            visibility = View.GONE
        }
        root.addView(openMatchButton, LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(5)
        })

        val baseLeft=root.paddingLeft; val baseTop=root.paddingTop"""
    s = s.replace(needle, repl, 1)
    ren.write_text(s, encoding="utf-8")
print("PHASE3: Ren OK", flush=True)

manifest = app / "src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8").replace(
    '<activity android:name=".MainActivity" android:exported="true"',
    '<activity android:name=".MainActivity" android:exported="false"')
manifest.write_text(m, encoding="utf-8")
print("PHASE3: manifest OK", flush=True)

dash = app / "src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt"
d = dash.read_text(encoding="utf-8")
d = d.replace('when(active){"qbank"->qbank(rows);"cards"->cards(cards);"stats"->stats(rows,overall);"mastery"->mastery(rows,overall);else->home(overall,cards)}',
              'when(active){"qbank"->qbank(rows);"flashcards"->cards(cards);"analytics"->stats(rows,overall);"mastery"->mastery(rows,overall);else->home(overall,cards)}')
d = d.replace('switchSection("stats")','switchSection("analytics")').replace('switchSection("cards")','switchSection("flashcards")')
d = d.replace('listOf("home" to "Home","qbank" to "QBank","cards" to "Cards","stats" to "Stats","mastery" to "Mastery")',
              'listOf("home" to "Home","qbank" to "QBank","flashcards" to "Cards","analytics" to "Stats","mastery" to "Mastery")')
dash.write_text(d, encoding="utf-8")
print("PHASE3: dashboard OK", flush=True)

gradle = app / "build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
g = re.sub(r'(?m)^\s*versionCode\s*=\s*\d+\s*$', '        versionCode = 366', g, count=1)
g = re.sub(r'(?m)^\s*versionName\s*=\s*"[^"]+"\s*$', '        versionName = "8.3.272"', g, count=1)
gradle.write_text(g, encoding="utf-8")
print("PHASE3: version OK", flush=True)

gradle_props = root / "gradle.properties"
if not gradle_props.exists():
    raise SystemExit("PHASE3 FAIL: gradle.properties missing")
gp = gradle_props.read_text(encoding="utf-8")
if re.search(r"(?m)^\s*android\.uniquePackageNames\s*=", gp):
    gp = re.sub(r"(?m)^\s*android\.uniquePackageNames\s*=.*$", "android.uniquePackageNames=false", gp)
else:
    gp = gp.rstrip() + "\nandroid.uniquePackageNames=false\n"
gradle_props.write_text(gp, encoding="utf-8")
print("PHASE3: AGP9 LiteRT namespace mitigation OK", flush=True)

for name in ["libQnnHtpPrepare.so","libQnnHtpV75Skel.so","libQnnSystem.so","libQnnHtp.so","libQnnIr.so","libQnnSaver.so","libQnnHtpV75Stub.so","libLiteRtCompilerPlugin_Qualcomm.so","libLiteRtDispatch_Qualcomm.so"]:
    (app / "src/main/jniLibs/arm64-v8a" / name).unlink(missing_ok=True)
print("PHASE3: QNN cleanup OK", flush=True)

assert 'MainActivity" android:exported="false"' in manifest.read_text(encoding="utf-8")
assert "android.uniquePackageNames=false" in gradle_props.read_text(encoding="utf-8")
assert "versionCode = 366" in gradle.read_text(encoding="utf-8")
assert 'versionName = "8.3.272"' in gradle.read_text(encoding="utf-8")
print("PHASE3: ALL ASSERTIONS PASS", flush=True)
