#!/usr/bin/env python3
"""Temporary Phase 661 diagnostic: expose deletion-recovery admission test and scheduler source."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
matches = []
for base in (root / "app/src/androidTest", root / "app/src/test"):
    if not base.exists():
        continue
    for path in base.rglob("*.kt"):
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            continue
        if "QBankDeletionRecoveryAdmissionTest" in path.name or "admissionUsesDurableBackgroundConstraintWithoutDeviceIdleGate" in text:
            matches.append((path, text))
if not matches:
    raise SystemExit("[661-diagnostic] QBankDeletionRecoveryAdmissionTest not found in generated source")
for path, text in matches:
    lines = text.splitlines()
    print(f"[661-diagnostic] TEST SOURCE {path.relative_to(root)}")
    for i, line in enumerate(lines, 1):
        if 35 <= i <= 65:
            print(f"[661-diagnostic] {i:04d}: {line}")
print("[661-diagnostic] scheduler/worker admission snippets:")
found = 0
for path in (root / "app/src/main").rglob("*.kt"):
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError):
        continue
    if "QBankDeletionRecovery" not in path.name and not any("QBankDeletionRecoveryWorker" in line for line in lines):
        continue
    for i, line in enumerate(lines, 1):
        if any(token in line for token in ("Constraints.Builder", "setRequires", "ExistingWorkPolicy", "enqueueUnique", "OneTimeWorkRequest", "PeriodicWorkRequest", "WorkRequest.Builder")):
            print(f"[661-diagnostic] {path.relative_to(root)}:{i}: {line.strip()}")
            found += 1
if not found:
    print("[661-diagnostic] no direct WorkManager admission anchors found")
