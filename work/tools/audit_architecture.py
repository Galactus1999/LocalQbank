#!/usr/bin/env python3
"""Warning-oriented architecture cohesion audit for Rovex."""
from pathlib import Path
import re
ROOT = Path('app/src/main/java/com/localqbank/library')
files = list(ROOT.glob('*.kt'))
text = {p: p.read_text(errors='ignore') for p in files}
classes = {}
for p,s in text.items():
    for m in re.finditer(r'\b(class|object|interface)\s+(\w+)',s): classes[m.group(2)] = p.name
router=text.get(ROOT/'RenIntelligenceOrchestrator.kt','')
facade=text.get(ROOT/'IntelligenceOrchestrator.kt','')
assert 'class RenIntelligenceOrchestrator' in router
assert 'private val router: RenIntelligenceOrchestrator' in facade
for forbidden in ('AppManagers.bio.plan','PerformanceManager.refs','smartMix(','matchSubject('):
    assert forbidden not in facade, f'Facade regained duplicated routing logic: {forbidden}'
print('PASS: single intent-routing owner + thin compatibility facade')
contracts=text.get(ROOT/'ManagerContracts.kt','')
for name in ('RenIntelligenceOrchestrator','IntelligenceGuardian','EngineMeshCoordinator'):
    assert name in contracts, f'Missing contract entry for {name}'
print('PASS: orchestration/control contracts registered')
for p,s in sorted(text.items(), key=lambda kv: len(kv[1]), reverse=True)[:10]:
    lines=s.count('\n')+1
    if lines>=700: print(f'WARN: large source file {p.name}: {lines} lines')
for name,p in classes.items():
    if re.search(r'(Orchestrator|Coordinator)$',name) and name not in contracts:
        # Coordinator names are architectural boundaries; keep the registry authoritative.
        raise SystemExit(f'ERROR: {name} in {p} is not documented in ManagerContractRegistry')
print('PASS: no undocumented orchestrator/coordinator classes')
