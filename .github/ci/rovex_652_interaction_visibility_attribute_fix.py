#!/usr/bin/env python3
"""Phase 652: accept visible UiAutomator nodes when the optional visibility attribute is omitted."""
from pathlib import Path
import sys

project = Path(sys.argv[1]).resolve()
explorer = project / ".ci" / "rovex_interaction_explorer.py"
if not explorer.is_file():
    raise SystemExit(f"[652] injected interaction explorer missing: {explorer}")

# The baseline explorer requires visible-to-user="true". On the CI emulator, UiAutomator
# emits the actual foreground recovery dialog with enabled/clickable/bounds attributes but
# omits visible-to-user on every node. This made two real buttons (LATER/RESUME) disappear
# from the action list and caused a false CI failure after screenshots had already passed.
source = explorer.read_text(encoding="utf-8")
old_nodes = '''        if a.get("enabled")!="true" or a.get("visible-to-user")!="true": continue'''
new_nodes = '''        # UiAutomator may omit visible-to-user on otherwise visible hierarchy nodes.
        # Its dumped tree contains visible nodes; only an explicit false excludes one.
        if a.get("enabled") != "true" or a.get("visible-to-user") == "false": continue'''
old_state = '''        if a.get("visible-to-user")!="true": continue'''
new_state = '''        # Match nodes() visibility semantics when UiAutomator omits this optional field.
        if a.get("visible-to-user") == "false": continue'''

if old_nodes in source:
    source = source.replace(old_nodes, new_nodes, 1)
elif new_nodes in source:
    print("[652] visible-node filter already applied")
else:
    raise SystemExit("[652] expected nodes() visibility filter anchor not found")

if old_state in source:
    source = source.replace(old_state, new_state, 1)
elif new_state in source:
    print("[652] state-key visibility filter already applied")
else:
    raise SystemExit("[652] expected state_key() visibility filter anchor not found")

if 'a.get("visible-to-user") == "false"' not in source or 'a.get("enabled") != "true"' not in source:
    raise SystemExit("[652] missing explicit-false visibility policy")
if 'a.get("visible-to-user")!="true"' in source:
    raise SystemExit("[652] strict optional-attribute filter remains")

# Version changes are deliberately isolated to the source baseline metadata.
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

explorer.write_text(source, encoding="utf-8")
print("[652] visible-node detection tolerates omitted UiAutomator visible-to-user attributes")
print("[652] explicit visible-to-user=false, disabled nodes, invalid bounds and empty exploration remain rejected")
print("[652] applied v8.3.650 / versionCode 736")
