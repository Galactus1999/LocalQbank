#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail=0
printf 'Rovex ruthless static audit\n'
python3 - "$ROOT" <<'PYCFG'
import json, pathlib, sys
root=pathlib.Path(sys.argv[1])
p=root/'app/google-services.json'
if not p.exists():
    print('FIREBASE_CONFIG=ABSENT (allowed for offline source builds)')
else:
    data=json.loads(p.read_text())
    clients=data.get('client',[])
    c=next((x for x in clients if x.get('client_info',{}).get('android_client_info',{}).get('package_name')=='com.localqbank.library'),None)
    if not c: raise SystemExit('FIREBASE_CONFIG_ERROR: package com.localqbank.library not found')
    oauth=c.get('oauth_client',[])
    types={x.get('client_type') for x in oauth}
    if 1 not in types: raise SystemExit('FIREBASE_CONFIG_ERROR: Android OAuth client missing')
    if 3 not in types: raise SystemExit('FIREBASE_CONFIG_ERROR: Web OAuth client missing; default_web_client_id cannot be generated')
    print('FIREBASE_OAUTH_CONFIG=PASS (Android + Web clients present)')
PYCFG
if grep -RIn --include='*.kt' 'GlobalScope' "$ROOT/app/src"; then fail=1; fi
if grep -RIn --include='*.kt' 'Thread\.sleep(' "$ROOT/app/src"; then fail=1; fi
if grep -RIn --include='*.kt' 'runBlocking' "$ROOT/app/src/main"; then fail=1; fi
if grep -RIn --include='*.kt' 'AnimationPolicy\.class' "$ROOT/app/src"; then fail=1; fi
if grep -RIn --include='*.kt' 'TODO\|FIXME\|NotImplementedError' "$ROOT/app/src/main"; then fail=1; fi
python3 - "$ROOT" <<'PY'
import sys, re, pathlib, xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
for p in root.rglob('*.xml'):
    try: ET.parse(p)
    except Exception as e: print('XML_ERROR',p,e); raise SystemExit(1)
    text=p.read_text(errors='ignore')
    ids=re.findall(r'android:id="@\+id/([A-Za-z0-9_]+)"', text)
    dup=sorted({x for x in ids if ids.count(x)>1})
    if dup: print('DUPLICATE_ID',p,','.join(dup)); raise SystemExit(1)
print('XML_AND_LAYOUT_CHECK=PASS')
PY
printf 'STATIC_AUDIT=%s\n' "$([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"
exit "$fail"
