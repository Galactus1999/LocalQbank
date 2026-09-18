#!/usr/bin/env python3
from pathlib import Path
import runpy,sys
root=Path(sys.argv[1] if len(sys.argv)>1 else ".")
for name in ("repair_268_core.py","repair_268_resource_fix.py"):
 p=root/"tools"/name
 if not p.exists(): raise SystemExit("ERROR: "+name+" missing")
 old=sys.argv;sys.argv=[str(p),str(root)]
 try:runpy.run_path(str(p),run_name="__main__")
 finally:sys.argv=old
print("8.3.268 stability/UI repair PASS")
