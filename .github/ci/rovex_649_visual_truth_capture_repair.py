#!/usr/bin/env python3
"""Phase 649: make rendered screenshot capture truthful for non-exported Rovex Activities."""
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
gradle = P / "app/build.gradle.kts"
capture_test = P / "app/src/androidTest/java/com/localqbank/library/RovexVisualTruthCaptureTest.kt"
explorer = P / ".ci/rovex_interaction_explorer.py"

for required in (gradle, capture_test, explorer):
    if not required.is_file():
        raise SystemExit(f"[649] required file missing: {required}")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.647"' in g and "versionCode = 733" in g:
    g = g.replace('versionName = "8.3.647"', 'versionName = "8.3.648"', 1)
    g = g.replace("versionCode = 733", "versionCode = 734", 1)
    gradle.write_text(g, encoding="utf-8")
elif not ('versionName = "8.3.648"' in g and "versionCode = 734" in g):
    raise SystemExit("[649] wrong baseline; expected v8.3.647 / versionCode 733")

t = capture_test.read_text(encoding="utf-8")
t = t.replace("import org.junit.Ignore\n", "")
t = t.replace('@Ignore("Rendered screenshots are captured by the host-side visual truth harness; avoid a second multi-Activity launch inside the instrumentation process.")\n', "")
needle = "    fun captureCoreRenderedScreens() {\n"
guard = '''    fun captureCoreRenderedScreens() {
        org.junit.Assume.assumeTrue(
            "Run only in the dedicated rendered-visual-truth lane",
            InstrumentationRegistry.getArguments().getString("rovexVisualTruth") == "true"
        )
'''
if needle in t and "Run only in the dedicated rendered-visual-truth lane" not in t:
    t = t.replace(needle, guard, 1)
if "import org.junit.Ignore" in t or "@Ignore(" in t:
    raise SystemExit("[649] capture test remains ignored")
if "Run only in the dedicated rendered-visual-truth lane" not in t:
    raise SystemExit("[649] dedicated instrumentation argument guard missing")
if 'fun captureCoreRenderedScreens()' not in t or 'capture("01_home")' not in t or 'capture("05_settings")' not in t:
    raise SystemExit("[649] expected core Activity capture sequence missing")
capture_test.write_text(t, encoding="utf-8")

e = explorer.read_text(encoding="utf-8")
old_capture = '''def capture(name):
    with open(out/"screens"/(name+".png"),"wb") as f:
        subprocess.run(["adb","-s",serial,"exec-out","screencap","-p"],stdout=f,stderr=subprocess.DEVNULL)
'''
new_capture = '''def capture(name):
    r = subprocess.run(["adb","-s",serial,"exec-out","screencap","-p"],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if r.returncode != 0 or len(r.stdout) < 1024 or not r.stdout.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])):
        raise SystemExit("Interaction explorer screenshot is missing/invalid: " + name)
    (out/"screens"/(name+".png")).write_bytes(r.stdout)
'''
if old_capture not in e:
    raise SystemExit("[649] interaction explorer screenshot capture anchor missing")
e = e.replace(old_capture, new_capture, 1)

old_home = '''adb("shell","am","force-stop",app)
adb("shell","monkey","-p",app,"-c","android.intent.category.LAUNCHER","1")
time.sleep(2)
if not alive(): raise SystemExit("application died during initial launch")
capture("000-home")
home_xml=dump("000-home.xml")
clickables=[n for n in nodes(home_xml) if n.get("clickable")=="true"]
scrollables=[n for n in nodes(home_xml) if n.get("scrollable")=="true"]
'''
new_home = '''adb("shell","am","force-stop",app)
adb("shell","monkey","-p",app,"-c","android.intent.category.LAUNCHER","1")
if not alive(): raise SystemExit("application process died during initial launch")
home_xml = ""
clickables = []
scrollables = []
for attempt in range(30):
    home_xml = dump("000-home.xml")
    visible = nodes(home_xml)
    clickables = [n for n in visible if n.get("clickable") == "true"]
    scrollables = [n for n in visible if n.get("scrollable") == "true"]
    if clickables or scrollables:
        break
    time.sleep(1)
if len(home_xml.strip()) < 80 or "<node" not in home_xml or not (clickables or scrollables):
    raise SystemExit("Interaction explorer BLOCKED/FAILED: no visible interactive app hierarchy after 30 seconds; refusing to report an empty exploration as PASS. Hierarchy=" + home_xml[:500])
capture("000-home")
'''
if old_home not in e:
    raise SystemExit("[649] initial hierarchy validation anchor missing")
e = e.replace(old_home, new_home, 1)
e = e.replace('print("EXHAUSTIVE_INTERACTION_EXPLORER_PASS")','''if index == 0:
    raise SystemExit("Interaction explorer FAILED: no clickable/scrollable actions were explored.")
print("EXHAUSTIVE_INTERACTION_EXPLORER_PASS: actions=%d states=%d" % (index, len(seen_states)))''')
if "no visible interactive app hierarchy" not in e or "actions=%d states=%d" not in e:
    raise SystemExit("[649] fail-closed interaction postconditions missing")
explorer.write_text(e, encoding="utf-8")

print("[649] applied v8.3.648 / versionCode 734")
print("[649] rendered screenshot test is isolated and runs only with an explicit lane argument")
print("[649] interaction exploration rejects empty hierarchies, invalid screenshots and zero-action false passes")
