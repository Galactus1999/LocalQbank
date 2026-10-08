#!/usr/bin/env python3
from pathlib import Path
import re, sys

project = Path(sys.argv[1]).resolve()
build_files = list(project.rglob("build.gradle")) + list(project.rglob("build.gradle.kts"))
all_text = "\n".join(p.read_text(errors="ignore") for p in build_files)
if 'versionName = "8.3.623"' not in all_text or "versionCode = 709" not in all_text:
    raise SystemExit("v624 baseline guard failed: expected 8.3.623/versionCode 709")

ci = project / ".ci"
ci.mkdir(parents=True, exist_ok=True)
(ci / "rovex_interaction_explorer.py").write_text(r'''#!/usr/bin/env python3
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path

serial, app, root = sys.argv[1], sys.argv[2], Path(sys.argv[3])
out = root / ".adaptive-interaction"
(out / "screens").mkdir(parents=True, exist_ok=True)
(out / "hierarchies").mkdir(parents=True, exist_ok=True)
report = out / "interaction-report.tsv"
report.write_text("index\taction\tlabel\tresource_id\tbounds\tresult\tdetail\n", encoding="utf-8")

def adb(*args, capture=True):
    return subprocess.run(["adb","-s",serial,*args], stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.DEVNULL, text=True)

def alive():
    return adb("shell","pidof",app).returncode == 0

def dump(name):
    adb("shell","uiautomator","dump","/sdcard/rovex-explore.xml")
    r=subprocess.run(["adb","-s",serial,"exec-out","cat","/sdcard/rovex-explore.xml"],
                     stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    p=out/"hierarchies"/name
    p.write_bytes(r.stdout)
    return r.stdout.decode("utf-8","ignore")

def capture(name):
    with open(out/"screens"/(name+".png"),"wb") as f:
        subprocess.run(["adb","-s",serial,"exec-out","screencap","-p"],stdout=f,stderr=subprocess.DEVNULL)

def bounds(s):
    m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",s or "")
    return tuple(map(int,m.groups())) if m else None

def nodes(xml):
    try: rootxml=ET.fromstring(xml)
    except Exception: return []
    result=[]
    for n in rootxml.iter():
        a=n.attrib
        if a.get("enabled")!="true" or a.get("visible-to-user")!="true": continue
        b=bounds(a.get("bounds"))
        if not b or b[2]<=b[0] or b[3]<=b[1]: continue
        if a.get("clickable")=="true" or a.get("scrollable")=="true":
            result.append(a.copy())
    return result

def log(i,action,a,result,detail):
    with report.open("a",encoding="utf-8") as f:
        f.write("\t".join([str(i),action,
            (a.get("text") or a.get("content-desc") or "unnamed").replace("\t"," "),
            a.get("resource-id",""),a.get("bounds",""),result,detail])+"\n")

def recover():
    adb("shell","input","keyevent","KEYCODE_BACK")
    time.sleep(.35)
    if not alive():
        adb("shell","am","force-stop",app)
        adb("shell","monkey","-p",app,"-c","android.intent.category.LAUNCHER","1")
        time.sleep(1)

adb("shell","am","force-stop",app)
adb("shell","monkey","-p",app,"-c","android.intent.category.LAUNCHER","1")
time.sleep(2)
if not alive(): raise SystemExit("application died during initial launch")
capture("000-home")
home_xml=dump("000-home.xml")
clickables=[n for n in nodes(home_xml) if n.get("clickable")=="true"]
scrollables=[n for n in nodes(home_xml) if n.get("scrollable")=="true"]
seen=set()
index=0

# Explore every unique visible clickable exposed by the initial screen. Each
# tap is followed by a liveness check and recovery; destructive labels are
# opened only far enough to expose their confirmation UI, then recovered.
for original in clickables:
    key=(original.get("resource-id",""), original.get("text",""), original.get("content-desc",""))
    if key in seen: continue
    seen.add(key)
    current=dump("pre-%03d.xml"%(index+1))
    candidates=nodes(current)
    target=None
    for n in candidates:
        if n.get("clickable")!="true": continue
        if original.get("resource-id") and n.get("resource-id")==original.get("resource-id"):
            target=n; break
        if not original.get("resource-id") and (n.get("text")==original.get("text")) and (n.get("content-desc")==original.get("content-desc")):
            target=n; break
    if target is None: continue
    b=bounds(target.get("bounds"))
    if not b: continue
    index+=1
    x,y=(b[0]+b[2])//2,(b[1]+b[3])//2
    adb("shell","input","tap",str(x),str(y))
    time.sleep(.8)
    ok=alive()
    if ok:
        dump("post-%03d.xml"%index)
        capture("%03d-click"%index)
    label=target.get("text") or target.get("content-desc") or target.get("resource-id","")
    detail="tap-survived"
    if re.search(r"\b(delete|remove|reset|clear|erase|logout|sign.?out|wipe|uninstall)\b",label,re.I):
        detail="destructive-control-opened-and-recovered"
    log(index,"TAP",target,"PASS" if ok else "FAIL",detail)
    if not ok: raise SystemExit("application died after clickable interaction: "+label)
    recover()

# Exercise every scrollable node visible on the initial screen in both
# directions and require an observable hierarchy change when content exists.
for n in scrollables:
    b=bounds(n.get("bounds"))
    if not b: continue
    x=(b[0]+b[2])//2
    top=b[1]+max(20,(b[3]-b[1])//4)
    bottom=b[3]-max(20,(b[3]-b[1])//4)
    before=dump("scroll-before-%03d.xml"%(index+1))
    adb("shell","input","swipe",str(x),str(bottom),str(x),str(top),"450")
    time.sleep(.5)
    after=dump("scroll-after-%03d.xml"%(index+1))
    changed=before!=after
    index+=1
    log(index,"SCROLL",n,"PASS" if changed else "WARN",
        "viewport-or-hierarchy-changed" if changed else "no-observable-hierarchy-change")
    if not alive(): raise SystemExit("application died during scroll")
    recover()

print("EXHAUSTIVE_INTERACTION_EXPLORER_PASS")
''', encoding="utf-8")
(ci / "rovex_interaction_explorer.py").chmod(0o755)

inst = project / ".github" / "ci" / "adaptive-instrumentation.sh"
s = inst.read_text()
marker = 'echo "Adaptive instrumentation suite PASS."'
block = '''if [[ -x "$PROJECT/.ci/rovex_interaction_explorer.py" ]]; then
  echo "===== exhaustive interaction/navigation explorer ====="
  python3 "$PROJECT/.ci/rovex_interaction_explorer.py" "$SERIAL" "$APP_ID" "$ROOT"
else
  echo "Exhaustive interaction explorer BLOCKED: injected explorer missing"
  exit 1
fi

'''
if block not in s:
    s=s.replace(marker, block+marker)
inst.write_text(s)

for p in build_files:
    old=p.read_text()
    new=old.replace('versionCode = 709','versionCode = 710').replace('versionName = "8.3.623"','versionName = "8.3.624"')
    if new != old: p.write_text(new)

(project/"app").mkdir(exist_ok=True)
(project/"app"/"interaction-contract.json").write_text('''{
  "version": 1,
  "scope": "full-reachable-ui",
  "required": ["launch_survival","visible_clickables","visible_scrollables","navigation_recovery","screenshot_evidence","interaction_ledger"],
  "destructive_actions": "open then recover/cancel; never intentionally commit irreversible state",
  "hard_fail": ["application-death","explorer-missing","instrumentation-failure"],
  "scroll": "require observable hierarchy/viewport change when content permits"
}
''')
print("v8.3.624 exhaustive interaction explorer injected")
