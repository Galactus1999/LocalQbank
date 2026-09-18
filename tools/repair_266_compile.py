#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
target = root / "app/src/main/java/com/localqbank/library/RenActivity.kt"
if not target.is_file():
    raise SystemExit(f"ERROR: expected source file not found: {target}")

s = target.read_text(encoding="utf-8")
if "import android.app.AlertDialog" in s:
    print("RenActivity AlertDialog import: already present")
    raise SystemExit(0)

lines = s.splitlines(True)
pkg = next((i for i, line in enumerate(lines) if line.startswith("package ")), None)
if pkg is None:
    raise SystemExit("ERROR: package declaration not found in RenActivity.kt")

lines.insert(pkg + 1, "import android.app.AlertDialog\n")
target.write_text("".join(lines), encoding="utf-8")
print("RenActivity AlertDialog import: ADDED")
