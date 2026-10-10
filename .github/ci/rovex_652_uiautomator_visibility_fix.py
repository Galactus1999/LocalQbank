#!/usr/bin/env python3
"""Phase 652: honor UiAutomator dumps that omit visible-to-user attributes."""
from pathlib import Path
import sys

project = Path(sys.argv[1]).resolve()
explorer = project / ".ci" / "rovex_interaction_explorer.py"
if not explorer.is_file():
    raise SystemExit(f"[652] injected interaction explorer missing: {explorer}")

# CI evidence: the actual foreground Rovex recovery dialog contained clickable LATER
# and RESUME buttons with enabled/bounds attributes, but UiAutomator omitted
# visible-to-user on every node. The strict "must equal true" filter discarded all of them.
source = explorer.read_text(encoding="utf-8")
old_nodes = 'if a.get("enabled")!="true" or a.get("visible-to-user")!="true": continue'
new_nodes = '''# UiAutomator may omit visible-to-user on otherwise visible hierarchy nodes.
        # Only an explicit false excludes a node; enabled and valid bounds are still required.
        if a.get("enabled") != "true" or a.get("visible-to-user") == "false": continue'''
old_state = 'if a.get("visible-to-user")!="true": continue'
new_state = '''# Keep state fingerprints consistent when UiAutomator omits this optional field.
        if a.get("visible-to-user") == "false": continue'''

if old_nodes in source:
    source = source.replace(old_nodes, new_nodes, 1)
elif new_nodes in source:
    print("[652] nodes() visibility compatibility already applied")
else:
    raise SystemExit("[652] expected nodes() visibility filter anchor missing; refusing unsafe edit")

if old_state in source:
    source = source.replace(old_state, new_state, 1)
elif new_state in source:
    print("[652] state_key() visibility compatibility already applied")
else:
    raise SystemExit("[652] expected state_key() visibility filter anchor missing; refusing unsafe edit")

if 'a.get("visible-to-user") == "false"' not in source:
    raise SystemExit("[652] explicit-invisible filter missing")
if 'a.get("visible-to-user")!="true"' in source:
    raise SystemExit("[652] missing visibility attributes are still incorrectly rejected")
if 'if a.get("enabled") != "true"' not in source:
    raise SystemExit("[652] disabled-node rejection was not preserved")

explorer.write_text(source, encoding="utf-8")

build_files = list(project.rglob("build.gradle")) + list(project.rglob("build.gradle.kts"))
version_updated = False
for path in build_files:
    text = path.read_text(encoding="utf-8", errors="ignore")
    if 'versionName = "8.3.649"' in text and "versionCode = 735" in text:
        text = text.replace('versionName = "8.3.649"', 'versionName = "8.3.650"', 1)
        text = text.replace("versionCode = 735", "versionCode = 736", 1)
        path.write_text(text, encoding="utf-8")
        version_updated = True
    elif 'versionName = "8.3.650"' in text and "versionCode = 736" in text:
        version_updated = True
if not version_updated:
    raise SystemExit("[652] expected Phase 651 v8.3.649 / versionCode 735 baseline not found")

print("[652] UiAutomator nodes without visible-to-user remain discoverable")
print("[652] explicitly invisible and disabled nodes remain excluded")
print("[652] nodes() and state_key() now use the same visibility semantics")
print("[652] applied v8.3.650 / versionCode 736")
