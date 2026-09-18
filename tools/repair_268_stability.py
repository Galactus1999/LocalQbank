#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
def one(name):
    found=[p for p in root.rglob(name) if "app/src/main" in p.as_posix()]
    if len(found)!=1:
        raise SystemExit(f"ERROR: expected exactly one {name}, found {len(found)}")
    return found[0]

ren=one("RenActivity.kt")
s=ren.read_text(encoding="utf-8")
if "import android.app.AlertDialog" not in s:
    lines=s.splitlines(True)
    pkg=next(i for i,x in enumerate(lines) if x.startswith("package "))
    lines.insert(pkg+1,"import android.app.AlertDialog
")
    ren.write_text("".join(lines),encoding="utf-8")
    print("RenActivity AlertDialog import: ADDED")
else:
    print("RenActivity AlertDialog import: already present")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.268"' not in g:
    g=g.replace('versionName = "8.3.267"','versionName = "8.3.268"')
    g=g.replace("versionCode = 361","versionCode = 362")
    gradle.write_text(g,encoding="utf-8")
print("8.3.268 version contract: checked")

for rel in [
    "app/src/main/java/com/localqbank/library/RovexSectionDashboardActivity.kt",
    "app/src/main/java/com/localqbank/library/BenQuestionAiContextDialog.kt",
    "app/src/main/java/com/localqbank/library/BenCloudAiGateway.kt",
    "app/src/main/java/com/localqbank/library/BenResponsePolicy.kt",
]:
    if not (root/rel).exists():
        raise SystemExit(f"ERROR: required stability/UI source missing: {rel}")
print("8.3.268 stability/UI source contract: PASS")
