#!/usr/bin/env python3
from pathlib import Path
import os,runpy,sys
workspace=Path(sys.argv[1] if len(sys.argv)>1 else ".")
project=Path(os.environ.get("PROJECT_DIR",""))
if not project.exists():
    candidates=list(workspace.rglob("settings.gradle.kts"))
    project=candidates[0].parent if candidates else workspace
for name in ("repair_268_core.py","repair_268_resource_fix.py","repair_269_stability.py","repair_270_phase2.py","repair_271_final_ui.py","repair_272_offline_size.py"):
    p=workspace/"tools"/name
    if not p.exists(): raise SystemExit("ERROR: "+name+" missing in workflow workspace")
    old=sys.argv;sys.argv=[str(p),str(project)]
    try:runpy.run_path(str(p),run_name="__main__")
    finally:sys.argv=old
print("8.3.270 Phase-2 stability/UI repair chain PASS")
