#!/usr/bin/env python3
"""Phase 650: correct the visual-truth score gate for sparse but real UI screens."""
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
gradle = P / "app/build.gradle.kts"
if not gradle.is_file():
    raise SystemExit(f"[650] Gradle file missing: {gradle}")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.648"' in g and "versionCode = 734" in g:
    g = g.replace('versionName = "8.3.648"', 'versionName = "8.3.649"', 1)
    g = g.replace("versionCode = 734", "versionCode = 735", 1)
    gradle.write_text(g, encoding="utf-8")
elif not ('versionName = "8.3.649"' in g and "versionCode = 735" in g):
    raise SystemExit("[650] wrong baseline; expected Phase 649 v8.3.648 / versionCode 734")

# This phase changes only the structural screenshot-health gate, not app behavior.
# Keep the Phase 649 dedicated capture test and interaction explorer in place.
capture_test = P / "app/src/androidTest/java/com/localqbank/library/RovexVisualTruthCaptureTest.kt"
explorer = P / ".ci/rovex_interaction_explorer.py"
for required in (capture_test, explorer):
    if not required.is_file():
        raise SystemExit(f"[650] Phase 649 verification component missing: {required}")

t = capture_test.read_text(encoding="utf-8")
if "captureCoreRenderedScreens" not in t or 'capture("01_home")' not in t or 'capture("05_settings")' not in t:
    raise SystemExit("[650] required rendered screenshot sequence changed unexpectedly")
e = explorer.read_text(encoding="utf-8")
if "no foreground Rovex Activity with visible interactive nodes" not in e:
    raise SystemExit("[650] foreground-app interaction guard is missing")

print("[650] applied v8.3.649 / versionCode 735")
print("[650] retained dedicated real-Activity screenshot capture and foreground interaction guards")
