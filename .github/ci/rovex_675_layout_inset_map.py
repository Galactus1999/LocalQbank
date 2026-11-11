#!/usr/bin/env python3
"""Phase 675: inspect the actual layout builder and inset helper that create the oversized quiz header."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
if not gradle.is_file():
    raise SystemExit("[675] FATAL: app/build.gradle.kts missing")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.672"' not in g or re.search(r"\bversionCode\s*=\s*758\b", g) is None:
    raise SystemExit("[675] FATAL: expected v8.3.672 / code 758")
def dump_file(name, needles=None):
    p = pkg / name
    if not p.is_file():
        print(f"[675] MISSING {name}")
        return
    lines = p.read_text(encoding="utf-8").splitlines()
    print(f"[675] BEGIN FILE {name} lines={len(lines)}")
    if needles is None:
        for i, line in enumerate(lines):
            print(f"[675][SOURCE] {name}:{i+1}: {line}")
    else:
        hits = [i for i,line in enumerate(lines) if any(n.lower() in line.lower() for n in needles)]
        ranges = []
        for i in hits:
            lo,hi=max(0,i-12),min(len(lines),i+22)
            if ranges and lo <= ranges[-1][1]: ranges[-1]=(ranges[-1][0],max(ranges[-1][1],hi))
            else: ranges.append((lo,hi))
        for lo,hi in ranges:
            print(f"[675] --- {name}:{lo+1}-{hi} ---")
            for i in range(lo,hi):
                print(f"[675][SOURCE] {name}:{i+1}: {lines[i]}")
    print(f"[675] END FILE {name}")
dump_file("QuizLayoutBuilder.kt")
dump_file("AdaptiveLayoutManager.kt", ["WindowInsets", "topExtraDp", "keepStatusBarVisible", "protectDisplayCutout", "setPadding", "paddingTop"])
dump_file("QuizUiPreferences.kt", ["headerMm", "header"])
g = g.replace('versionName = "8.3.672"', 'versionName = "8.3.673"', 1).replace("versionCode = 758", "versionCode = 759", 1)
if 'versionName = "8.3.673"' not in g or re.search(r"\bversionCode\s*=\s*759\b", g) is None:
    raise SystemExit("[675] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")
print("[675] SOURCE MAP ONLY; header correction is still not claimed.")
