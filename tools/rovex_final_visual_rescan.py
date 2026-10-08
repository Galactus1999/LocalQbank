#!/usr/bin/env python3
"""Final Rovex source/error/incompleteness rescan for the exact CI candidate."""
from __future__ import annotations
import argparse, json, re, subprocess, sys, xml.etree.ElementTree as ET
from pathlib import Path
SKIP={".git",".gradle","build","node_modules"}
TEXT={".kt",".java",".xml",".gradle",".kts",".py",".pro",".properties",".json"}
BAD_MARKERS=re.compile(r"\b(?:TODO|FIXME|PLACEHOLDER|NOT IMPLEMENTED|STUB)\b",re.I)
CATCH_THROWABLE=re.compile(r"catch\s*\(\s*Throwable\b")
FIND_TYPED=re.compile(r"findViewById\s*<\s*([A-Za-z0-9_$.]+)\s*>\s*\(\s*R\.id\.([A-Za-z0-9_]+)\s*\)")
ONCREATE=re.compile(r"\b(?:override\s+)?fun\s+onCreate\s*\(")
def files(root):
    for p in root.rglob("*"):
        if p.is_file() and p.suffix.lower() in TEXT and not any(x in SKIP for x in p.parts): yield p
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--project",required=True); ap.add_argument("--output",required=True); a=ap.parse_args()
    root=Path(a.project).resolve()
    if not root.is_dir(): raise SystemExit(f"project missing: {root}")
    findings=[]; warnings=[]
    for p in files(root):
        if p.suffix==".py":
            r=subprocess.run([sys.executable,"-m","py_compile",str(p)],capture_output=True,text=True)
            if r.returncode: findings.append({"kind":"python_syntax","file":str(p.relative_to(root)),"detail":r.stderr.strip()})
    for p in root.rglob("res/layout/**/*.xml"):
        try: tree=ET.parse(p)
        except ET.ParseError as e: findings.append({"kind":"xml_parse","file":str(p.relative_to(root)),"detail":str(e)}); continue
        ids={}
        for el in tree.iter():
            v=el.attrib.get("{http://schemas.android.com/apk/res/android}id",""); m=re.fullmatch(r"@\+?id/([A-Za-z0-9_]+)",v)
            if m: ids.setdefault(m.group(1),[]).append(el.tag)
        for ident,tags in ids.items():
            if len(tags)>1: findings.append({"kind":"duplicate_layout_id","file":str(p.relative_to(root)),"id":ident,"tags":tags})
    for p in files(root):
        try: s=p.read_text(encoding="utf-8",errors="ignore")
        except OSError: continue
        rel=str(p.relative_to(root)); n=len(CATCH_THROWABLE.findall(s))
        if n: findings.append({"kind":"catch(Throwable)","file":rel,"count":n})
        for m in BAD_MARKERS.finditer(s):
            warnings.append({"kind":"incomplete_marker","file":rel,"line":s.count(chr(10),0,m.start())+1,"marker":m.group(0)})
    ns="{http://schemas.android.com/apk/res/android}"
    for p in root.rglob("AndroidManifest.xml"):
        try: tree=ET.parse(p)
        except ET.ParseError as e: findings.append({"kind":"manifest_parse","file":str(p.relative_to(root)),"detail":str(e)}); continue
        seen={}
        for tag in ("activity","service","receiver","provider"):
            for el in tree.getroot().iter(tag):
                name=el.attrib.get(ns+"name")
                if name: seen[(tag,name)]=seen.get((tag,name),0)+1
        for (tag,name),count in seen.items():
            if count>1: findings.append({"kind":"duplicate_manifest_component","file":str(p.relative_to(root)),"type":tag,"name":name,"count":count})
    xml_types={}
    for p in root.rglob("res/layout/**/*.xml"):
        try: tree=ET.parse(p)
        except ET.ParseError: continue
        for el in tree.iter():
            v=el.attrib.get(ns+"id",""); m=re.fullmatch(r"@\+?id/([A-Za-z0-9_]+)",v)
            if m: xml_types.setdefault(m.group(1),set()).add(el.tag.split("}")[-1])
    compatible={"TextView":{"TextView","Button","EditText","AutoCompleteTextView","CheckedTextView"},"Button":{"Button","TextView","com.google.android.material.button.MaterialButton"},"ImageView":{"ImageView","ImageButton"},"EditText":{"EditText","AutoCompleteTextView"}}
    for p in files(root):
        if p.suffix not in {".kt",".java"}: continue
        s=p.read_text(encoding="utf-8",errors="ignore")
        for typ,ident in FIND_TYPED.findall(s):
            simple=typ.rsplit(".",1)[-1]; tags=xml_types.get(ident,set())
            if tags and simple in compatible and not any(t in compatible[simple] or t.endswith(simple) for t in tags):
                warnings.append({"kind":"findViewById_type_mismatch_candidate","file":str(p.relative_to(root)),"id":ident,"requested":simple,"xml_tags":sorted(tags)})
    startup=[]
    for p in files(root):
        if p.suffix==".kt":
            s=p.read_text(encoding="utf-8",errors="ignore")
            if ONCREATE.search(s) and "findViewById" in s: startup.append(str(p.relative_to(root)))
    gradles=list(root.rglob("app/build.gradle.kts"))
    if len(gradles)==1:
        g=gradles[0].read_text(encoding="utf-8",errors="ignore"); versions=re.findall(r"versionCode\s*=\s*(\d+)",g); names=re.findall(r'versionName\s*=\s*"([^"]+)"',g)
        if len(versions)!=1 or len(names)!=1: findings.append({"kind":"version_contract","file":str(gradles[0].relative_to(root)),"versionCodes":versions,"versionNames":names})
    else: warnings.append({"kind":"version_contract_unresolved","app_gradle_count":len(gradles)})
    repo_discover=Path.cwd()/".github/ci/adaptive-discover.sh"
    if repo_discover.is_file():
        d=repo_discover.read_text(encoding="utf-8",errors="ignore"); chain=["616_clinical_day_background","617_visual_token","618_premium_foundation","619_visual_lab","620_visual_truth","621_visual_truth_retrigger","622_premium_surface_migration"]; positions=[d.find(x) for x in chain]
        if any(x<0 for x in positions) or positions!=sorted(positions): findings.append({"kind":"visual_overlay_chain","detail":"Expected 616→617→618→619→620→621→622 chain is incomplete or out of order."})
    report={"schema":"rovex-final-rescan/v1","project":str(root),"hard_findings":findings,"warnings":warnings,"startup_onCreate_files":sorted(startup),"status":"FAIL" if findings else "PASS_WITH_WARNINGS" if warnings else "PASS"}
    Path(a.output).write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8"); print(json.dumps(report,indent=2)); return 1 if findings else 0
if __name__=="__main__": raise SystemExit(main())
