#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
profile = root / "app/src/main/java/com/localqbank/library/BenExamProfile.kt"
if not profile.is_file():
    raise SystemExit("[6445] BenExamProfile.kt missing")
text = profile.read_text()
lines = text.splitlines()
indices = [i for i, line in enumerate(lines) if line.startswith("    OTHER_OPTIONS(")]
if len(indices) != 1:
    raise SystemExit(f"[6445] expected one OTHER_OPTIONS enum entry, found {len(indices)}")
i = indices[0]
if not lines[i].rstrip().endswith(")"):
    raise SystemExit("[6445] OTHER_OPTIONS entry has an unexpected format")
lines[i] = lines[i].rstrip() + ","
profile.write_text("\n".join(lines) + "\n")
print("[6445] normalized final enum entry so Phase 645 can safely replace it")
