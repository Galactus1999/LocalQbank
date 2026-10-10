#!/usr/bin/env python3
"""Phase 652: honor UiAutomator dumps that omit visible-to-user attributes."""
from pathlib import Path
import sys

project = Path(sys.argv[1]).resolve()
explorer = project / ".ci" / "rovex_interaction_explorer.py"
if not explorer.is_file():
    raise SystemExit(f"[652] injected interaction explorer missing: {explorer}")

source = explorer.read_text(encoding="utf-8")
old = 'if a.get("enabled")!="true" or a.get("visible-to-user")!="true": continue'
new = 'if a.get("enabled")!="true" or a.get("visible-to-user")=="false": continue'
if old in source:
    source = source.replace(old, new, 1)
elif new in source:
    print("[652] visibility compatibility fix already applied")
else:
    raise SystemExit("[652] expected UiAutomator visibility filter anchor missing; refusing unsafe edit")

if 'a.get("visible-to-user")=="false"' not in source:
    raise SystemExit("[652] explicit-invisible filter missing")
if 'a.get("visible-to-user")!="true"' in source:
    raise SystemExit("[652] missing visibility attribute is still incorrectly rejected")
explorer.write_text(source, encoding="utf-8")
print("[652] UiAutomator nodes without visible-to-user are no longer discarded")
print("[652] explicitly invisible or disabled nodes remain excluded")
print("[652] startup recovery dialog buttons remain discoverable for safe dismissal")
