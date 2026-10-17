#!/usr/bin/env python3
"""Phase 674: expose the complete QuizActivity UI builder and header sizing call sites."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
activity = pkg / "QuizActivity.kt"
if not gradle.is_file() or not activity.is_file():
    raise SystemExit("[674] FATAL: required Gradle or QuizActivity source missing")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.671"' not in g or re.search(r"\bversionCode\s*=\s*757\b", g) is None:
    raise SystemExit("[674] FATAL: expected v8.3.671 / code 757 after Phase 673")
lines = activity.read_text(encoding="utf-8").splitlines()
def dump_block(start, label):
    if start < 0:
        print(f"[674] MISSING BLOCK: {label}")
        return
    depth = 0
    opened = False
    end = start
    for i in range(start, len(lines)):
        line = lines[i]
        # The builder's declaration brace and subsequent braces are balanced for this source block.
        depth += line.count("{") - line.count("}")
        if "{" in line:
            opened = True
        if opened and depth == 0:
            end = i + 1
            break
    print(f"[674] BEGIN {label} lines {start+1}-{end}")
    for i in range(start, end):
        print(f"[674][SOURCE] {i+1}: {lines[i]}")
    print(f"[674] END {label}")
for i, line in enumerate(lines):
    if re.search(r"\bfun\s+build\s*\(", line):
        dump_block(i, "QuizActivity.build")
for i, line in enumerate(lines):
    if any(token in line for token in ("headerMm", "topBar", "camera", "notification", "setImageResource", "AdaptiveLayoutManager.install")):
        lo, hi = max(0, i-3), min(len(lines), i+5)
        print(f"[674] CALLOUT lines {lo+1}-{hi}")
        for j in range(lo, hi):
            print(f"[674][SOURCE] {j+1}: {lines[j]}")
g = g.replace('versionName = "8.3.671"', 'versionName = "8.3.672"', 1).replace("versionCode = 757", "versionCode = 758", 1)
if 'versionName = "8.3.672"' not in g or re.search(r"\bversionCode\s*=\s*758\b", g) is None:
    raise SystemExit("[674] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")
print("[674] SOURCE MAP ONLY; no UI correction applied in this phase.")
