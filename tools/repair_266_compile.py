#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")

candidates = list(root.rglob("RenActivity.kt"))
if not candidates:
    raise SystemExit(f"ERROR: RenActivity.kt not found under project root: {root}")

preferred = [p for p in candidates if "app/src/main" in p.as_posix()]
if len(preferred) == 1:
    target = preferred[0]
elif len(candidates) == 1:
    target = candidates[0]
else:
    raise SystemExit("ERROR: multiple RenActivity.kt files found; refusing ambiguous repair")

s = target.read_text(encoding="utf-8")
if "import android.app.AlertDialog" in s:
    print(f"RenActivity AlertDialog import: already present ({target})")
    raise SystemExit(0)

lines = s.splitlines(True)
pkg = next((i for i, line in enumerate(lines) if line.startswith("package ")), None)
if pkg is None:
    raise SystemExit(f"ERROR: package declaration not found in {target}")

lines.insert(pkg + 1, "import android.app.AlertDialog\n")
target.write_text("".join(lines), encoding="utf-8")
print(f"RenActivity AlertDialog import: ADDED ({target})")
