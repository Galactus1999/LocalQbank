#!/usr/bin/env python3
from pathlib import Path
import base64,zlib,runpy,sys,tempfile
root=Path(sys.argv[1] if len(sys.argv)>1 else ".")
payload=Path(__file__).with_name("repair_268_payload.b64").read_text().strip()
script=zlib.decompress(base64.b64decode(payload)).decode("utf-8")
with tempfile.NamedTemporaryFile("w",suffix=".py",delete=False,encoding="utf-8") as f:
    f.write(script); p=f.name
old=sys.argv; sys.argv=[p,str(root)]
try: runpy.run_path(p,run_name="__main__")
finally: sys.argv=old
