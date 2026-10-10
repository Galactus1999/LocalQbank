#!/usr/bin/env python3
"""Phase 662: expose WorkInfo state before the focused Phase 663 test-race repair."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
test = root / "app/src/androidTest/java/com/localqbank/library/QBankDeletionRecoveryAdmissionTest.kt"
worker = root / "app/src/main/java/com/localqbank/library/QBankDeletionRecoveryWorker.kt"
if not test.is_file() or not worker.is_file():
    raise SystemExit("[662] required deletion-admission test/worker source missing")
source = test.read_text(encoding="utf-8")
anchor = """        val info = workManager.getWorkInfosForUniqueWork(UNIQUE).get().single()
        assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)
"""
marker = "[662] deletion admission state after enqueueIfNeeded="
replacement = """        val info = workManager.getWorkInfosForUniqueWork(UNIQUE).get().single()
        println("[662] deletion admission state after enqueueIfNeeded=" + info.state +
            "; requiresBatteryNotLow=" + info.constraints.requiresBatteryNotLow() +
            "; requiresDeviceIdle=" + info.constraints.requiresDeviceIdle())
        assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)
"""
if marker not in source:
    if source.count(anchor) != 1:
        raise SystemExit("[662] expected exactly one admission-state assertion block")
    source = source.replace(anchor, replacement, 1)
    test.write_text(source, encoding="utf-8")
else:
    if source.count(marker) != 1:
        raise SystemExit("[662] duplicate runtime state diagnostic")
    if anchor.strip() not in source:
        raise SystemExit("[662] diagnostic must preserve the assertion anchor for Phase 663")
print("[662] runtime WorkInfo diagnostic installed; Phase 663 owns the focused assertion repair")
lines = worker.read_text(encoding="utf-8").splitlines()
print("[662] worker admission implementation:")
for i, line in enumerate(lines, 1):
    if 155 <= i <= 195:
        print(f"[662] {i:04d}: {line}")
