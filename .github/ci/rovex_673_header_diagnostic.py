#!/usr/bin/env python3
"""Phase 673: expose the real question-screen layout source in CI so header repair is anchored to actual code."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
activity = pkg / "QuizActivity.kt"
if not gradle.is_file() or not activity.is_file():
    raise SystemExit("[673] FATAL: required Gradle or QuizActivity source missing")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.670"' not in g or re.search(r"\bversionCode\s*=\s*756\b", g) is None:
    raise SystemExit("[673] FATAL: expected v8.3.670 / code 756 after Phase 672")
source = activity.read_text(encoding="utf-8")
lines = source.splitlines()
# Keep diagnostics tightly scoped: onCreate and the UI assembly around window setup.
hits = [i for i, line in enumerate(lines) if "override fun onCreate" in line or "setContentView(" in line or "statusBarColor" in line or "WindowInsets" in line or "decorFitsSystemWindows" in line]
ranges = []
for i in hits:
    lo, hi = max(0, i-12), min(len(lines), i+24)
    if ranges and lo <= ranges[-1][1]:
        ranges[-1] = (ranges[-1][0], max(ranges[-1][1], hi))
    else:
        ranges.append((lo, hi))
print("[673] BEGIN REAL QuizActivity source excerpt")
for lo, hi in ranges:
    print(f"[673] --- QuizActivity.kt lines {lo+1}-{hi} ---")
    for i in range(lo, hi):
        print(f"[673][SOURCE] {i+1}: {lines[i]}")
print("[673] END REAL QuizActivity source excerpt")
# Fail closed on ambiguous baselines; this phase only gathers source evidence, it does not claim
# to have fixed the header. Phase 674 will patch the exact root/header anchor discovered here.
g = g.replace('versionName = "8.3.670"', 'versionName = "8.3.671"', 1).replace("versionCode = 756", "versionCode = 757", 1)
if 'versionName = "8.3.671"' not in g or re.search(r"\bversionCode\s*=\s*757\b", g) is None:
    raise SystemExit("[673] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")
print("[673] SOURCE DIAGNOSTIC ONLY; header correction remains pending exact source review.")
