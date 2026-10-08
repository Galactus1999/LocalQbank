#!/usr/bin/env python3
"""Refresh the CI candidate to v8.3.621 after adding rendered visual truth capture."""
from pathlib import Path
import sys
EXPECTED_VERSION='versionName = "8.3.620"'
EXPECTED_CODE='versionCode = 706'
def main():
    if len(sys.argv)!=2: raise SystemExit("usage: rovex_621_visual_truth_retrigger_overlay.py <project>")
    project=Path(sys.argv[1]).resolve()
    gradle=project/"app"/"build.gradle.kts"
    test=project/"app"/"src"/"androidTest"/"java"/"com"/"localqbank"/"library"/"RovexVisualTruthCaptureTest.kt"
    if not gradle.is_file() or not test.is_file(): raise SystemExit("v8.3.621: v8.3.620 visual truth baseline missing")
    g=gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g: raise SystemExit("v8.3.621 requires v8.3.620/706")
    g=g.replace(EXPECTED_VERSION,'versionName = "8.3.621"',1).replace(EXPECTED_CODE,"versionCode = 707",1)
    gradle.write_text(g,encoding="utf-8")
    print("v8.3.621 rendered visual truth retrigger overlay: APPLIED")
if __name__=="__main__": raise SystemExit(main())
