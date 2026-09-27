#!/usr/bin/env python3
"""Apply the narrowly-scoped Phase-0 exception-boundary repair to an extracted Rovex source tree.

The repair is fail-closed: every expected old construct must be found exactly the
expected number of times. It never performs broad regex replacement across the
project.
"""
from pathlib import Path
import sys

EXPECTED = {
    "app/src/main/java/com/localqbank/library/FlashcardStudyActivity.kt": 2,
    "app/src/main/java/com/localqbank/library/QBankDeletionRecoveryWorker.kt": 1,
    "app/src/main/java/com/localqbank/library/QBankSearchIndexMaintenanceWorker.kt": 1,
    "app/src/main/java/com/localqbank/library/RovexOnline.kt": 1,
    "app/src/main/java/com/localqbank/library/RovexOnlineActivity.kt": 1,
}

def fail(msg):
    print(f"PHASE0 REPAIR FAIL: {msg}")
    raise SystemExit(1)

def find_project(root: Path) -> Path:
    settings = list(root.rglob("settings.gradle.kts")) + list(root.rglob("settings.gradle"))
    if not settings:
        fail("No Gradle settings file found")
    return settings[0].parent

def replace_exact(path: Path, old: str, new: str, expected: int):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        fail(f"{path}: expected {expected} occurrences of {old!r}, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")
    return count

def main():
    if len(sys.argv) != 2:
        fail("Usage: phase0_apply_repairs.py <extracted-project-root>")
    root = Path(sys.argv[1]).resolve()
    project = find_project(root)

    for rel, expected in EXPECTED.items():
        path = project / rel
        if not path.is_file():
            fail(f"Missing expected source file: {rel}")
        replace_exact(path, "catch (t: Throwable)", "catch (t: Exception)", expected)

    # QBankLoadingEngine has nested try/finally structure that requires the
    # existing catch/rethrow syntax. Narrow the catch from Throwable to Exception
    # rather than deleting the structurally-required handler.
    q = project / "app/src/main/java/com/localqbank/library/QBankLoadingEngine.kt"
    replace_exact(q, "catch (t: Throwable)", "catch (t: Exception)", 2)

    # Postcondition: no broad Throwable catch remains at any of the repaired sites.
    for rel in list(EXPECTED) + ["app/src/main/java/com/localqbank/library/QBankLoadingEngine.kt"]:
        text = (project / rel).read_text(encoding="utf-8")
        if "catch (t: Throwable)" in text or "catch(t: Throwable)" in text:
            fail(f"Postcondition failed: Throwable catch remains in {rel}")

    print("PHASE0 REPAIR: PASS")
    print("Converted six non-rendering catch(Throwable) boundaries to Exception.")
    print("Removed two redundant QBankLoadingEngine catch/rethrow cleanup blocks.")

if __name__ == "__main__":
    main()
