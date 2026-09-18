#!/usr/bin/env python3
"""Rovex Stage 8 architecture regression gate.

The baseline describes the current accepted architecture. The gate prevents accidental growth
or new ownership violations without forcing a risky rewrite of legacy screens.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = ROOT / "tools" / "architecture_baseline.json"
SRC = ROOT / "app" / "src" / "main" / "java" / "com" / "localqbank" / "library"
ACT = sorted(SRC.glob("*Activity.kt"))

def read(p): return p.read_text(encoding="utf-8")

def count(s, pattern): return len(re.findall(pattern, s, re.MULTILINE))

base = json.loads(BASE.read_text(encoding="utf-8"))
errors=[]

# 0. Incremental package migration: new architecture must not create a second root-level copy.
for required_package, required_files in {
    "ai.context": ["ContextPack.kt", "EvidencePack.kt", "ContextPackBuilder.kt"],
    "data.quiz": ["QuizDataSources.kt"],
    "core.observability": ["RovexObservability.kt"],
}.items():
    pkg_dir = SRC.joinpath(*required_package.split("."))
    for filename in required_files:
        if not (pkg_dir / filename).exists():
            errors.append(f"missing migrated architecture file: {required_package}/{filename}")

# 1. Activity growth: tolerate small maintenance changes, reject architectural bloat.
for p in ACT:
    key=p.name; lines=len(read(p).splitlines())
    b=base["activity_lines"].get(key, 0)
    if b and (lines > max(b + 80, int(b * 1.10))):
        errors.append(f"{key}: {lines} lines exceeds baseline {b} by >10% / 80 lines")
    if lines > 1800:
        errors.append(f"{key}: absolute Activity size {lines} > 1800 lines")

# 2. Direct persistence access: existing legacy access is baselined; any new occurrence fails.
patterns = {
    "qbank_db_ctor": r"\bQBankDb\s*\(",
    "progress_store": r"\bProgressStore\s*\(",
    "shared_prefs": r"\bgetSharedPreferences\s*\(",
}
for p in ACT:
    s=read(p); key=p.name
    for name,pat in patterns.items():
        current=count(s,pat); allowed=base["activity_persistence"].get(key,{}).get(name,0)
        if current > allowed:
            errors.append(f"{key}: new direct persistence access {name}: {current} > baseline {allowed}")

# 3. Manager/service construction: baseline existing framework/layout constructions; new manager
#    construction inside Activities is a regression unless explicitly approved in baseline.
manager_pat = r"\b[A-Z][A-Za-z0-9_]*(?:Manager|Engine)\s*\("
for p in ACT:
    key=p.name; s=read(p)
    current=count(s, manager_pat); allowed=base["activity_manager_construction"].get(key,0)
    if current > allowed:
        errors.append(f"{key}: new manager/engine construction count {current} > baseline {allowed}")

# 4. Coroutine/thread primitives: no unbounded global coroutine scope or blocking calls in UI.
for p in ACT:
    s=read(p); key=p.name
    for label,pat in {
        "runBlocking": r"\brunBlocking\b",
        "GlobalScope": r"\bGlobalScope\b",
        "Thread.sleep": r"\bThread\.sleep\s*\(",
        "scheduleAtFixedRate": r"\bscheduleAtFixedRate\s*\(",
    }.items():
        if re.search(pat,s): errors.append(f"{key}: prohibited UI pattern {label}")

# 5. Global singleton growth: baseline the accepted singleton set; new objects require explicit review.
all_text="\n".join(read(p) for p in SRC.rglob("*.kt"))
objects=count(all_text, r"^\s*(?:public\s+|private\s+|internal\s+)?object\s+[A-Za-z0-9_]+")
if objects > base["global_object_count"]:
    errors.append(f"main source singleton/object declarations increased {objects} > baseline {base['global_object_count']}")

# 6. Required dependency boundaries for the major refactored screens.
required = {
    "MainActivity.kt": "appContainer.mainViewModelFactory",
    "QuizActivity.kt": "appContainer.quizViewModelFactory",
    "SettingsActivity.kt": "appContainer.settingsViewModelFactory",
    "HtmlImportActivity.kt": "appContainer.newHtmlImportRepository",
}
for file, needle in required.items():
    if needle not in read(SRC / file): errors.append(f"{file}: required AppContainer boundary missing: {needle}")

# 7. Authoritative manager ownership: Activities may consume AppManagers, but must not assign a
#    new manager field that would become a parallel owner.
for p in ACT:
    s=read(p)
    if re.search(r"\b(?:private|internal|public)?\s*(?:val|var)\s+\w+\s*=\s*AppManagers\.[A-Za-z0-9_]+\s*\(",s):
        errors.append(f"{p.name}: appears to construct/own an AppManagers child directly")

# 8. Shared AI context/evidence contract must remain the single path for new grounded adapters.
quiz_text = read(SRC / "QuizActivity.kt")
pipeline_text = read(SRC / "BenGroundedNeuralPipeline.kt")
if "ContextPackBuilder" not in quiz_text:
    errors.append("QuizActivity.kt: shared ContextPackBuilder boundary missing")
if "EvidencePack" not in pipeline_text or "ContextPackBuilder" not in pipeline_text:
    errors.append("BenGroundedNeuralPipeline.kt: unified ContextPack/EvidencePack boundary missing")

if errors:
    print("ARCHITECTURE REGRESSION AUDIT FAILED")
    print("\n".join(" - "+e for e in errors))
    sys.exit(1)

print("ARCHITECTURE REGRESSION AUDIT PASSED")
print(f"Activities checked: {len(ACT)}; singleton declarations: {objects}")
