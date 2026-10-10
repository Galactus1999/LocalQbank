#!/usr/bin/env python3
"""Phase 651: wait for asynchronous launcher startup before declaring app death."""
from pathlib import Path
import sys

project = Path(sys.argv[1]).resolve()
explorer = project / ".ci" / "rovex_interaction_explorer.py"
if not explorer.is_file():
    raise SystemExit(f"[651] injected interaction explorer missing: {explorer}")

source = explorer.read_text(encoding="utf-8")
old = '''adb("shell","am","force-stop",app)
adb("shell","monkey","-p",app,"-c","android.intent.category.LAUNCHER","1")
if not alive(): raise SystemExit("application process died during initial launch")
home_xml = ""
'''
new = '''adb("shell","am","force-stop",app)
launch = adb("shell","monkey","-p",app,"-c","android.intent.category.LAUNCHER","1")
# monkey returns before Android has necessarily forked the target process.
# Treat startup as asynchronous: poll for the process instead of racing pidof.
process_started = False
for attempt in range(30):
    if alive():
        process_started = True
        break
    time.sleep(1)
if not process_started:
    raise SystemExit("application process did not start within 30 seconds after launcher request; monkey=" + launch.stdout[:300])
home_xml = ""
'''
if old in source:
    source = source.replace(old, new, 1)
elif 'process_started = False' in source and 'did not start within 30 seconds after launcher request' in source:
    print("[651] already applied")
else:
    raise SystemExit("[651] expected launcher-start race anchor not found; refusing unsafe edit")

if 'if not alive(): raise SystemExit("application process died during initial launch")' in source:
    raise SystemExit("[651] immediate post-monkey pidof race remains")
if 'for attempt in range(30):' not in source or 'did not start within 30 seconds after launcher request' not in source:
    raise SystemExit("[651] bounded launch wait missing")
explorer.write_text(source, encoding="utf-8")
print("[651] interaction explorer waits for asynchronous app process startup")
print("[651] preserves fail-closed foreground/accessibility and zero-action checks")
