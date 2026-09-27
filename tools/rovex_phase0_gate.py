#!/usr/bin/env python3
"""Rovex Phase-0 verification gate.

This gate is intentionally fail-closed. It audits the source archive that the
release workflow would actually build, and distinguishes source/static evidence
from executable-test evidence. It never treats grep/source inspection as a
behavioral PASS.

Exit 0 only when all required Phase-0 static contracts and regression-test
presence contracts are satisfied.
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_PACKAGE = "com.localqbank.library"
EXPECTED_VERSION_NAME = "8.3.437"
EXPECTED_VERSION_CODE = "529"
CURRENT_RE = re.compile(r"v(\d+)\.(\d+)\.(\d+)", re.I)
SOURCE_ZIPS = sorted(ROOT.glob("Rovex_v*.zip"))

def die(msg: str) -> None:
    print(f"PHASE0 FAIL: {msg}")
    raise SystemExit(1)

def read_text(p: Path) -> str:
    try:
        return p.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""

def all_text_files(root: Path):
    for p in root.rglob("*"):
        if p.is_file() and p.suffix.lower() in {".kt", ".java", ".kts", ".gradle", ".xml", ".md"}:
            yield p

def valid_source_zip(p: Path) -> bool:
    try:
        with zipfile.ZipFile(p) as z:
            names = {n.rstrip("/") for n in z.namelist()}
            return (
                any(n == "settings.gradle" or n == "settings.gradle.kts" or n.endswith("/settings.gradle") or n.endswith("/settings.gradle.kts") for n in names)
                and any(n == "app/build.gradle" or n == "app/build.gradle.kts" or n.endswith("/app/build.gradle") or n.endswith("/app/build.gradle.kts") for n in names)
                and any(n == "gradlew" or n.endswith("/gradlew") for n in names)
            )
    except (OSError, zipfile.BadZipFile):
        return False

def choose_zip():
    candidates = []
    for p in SOURCE_ZIPS:
        if not valid_source_zip(p):
            continue
        m = CURRENT_RE.search(p.name)
        version = tuple(map(int, m.groups())) if m else (0, 0, 0)
        candidates.append((version, p.name.lower(), p))
    if not candidates:
        die("No valid Rovex source ZIP found.")
    candidates.sort(key=lambda x: (x[0], x[1]), reverse=True)
    return candidates[0][2]

def find_project(root: Path) -> Path:
    settings = list(root.rglob("settings.gradle.kts")) + list(root.rglob("settings.gradle"))
    settings = [p for p in settings if ".gradle" not in p.parts]
    if not settings:
        die("Extracted source has no settings.gradle(.kts).")
    project = settings[0].parent
    if not (project / "gradlew").is_file():
        die(f"Extracted project has no gradlew: {project}")
    return project

def extract(src: Path, dst: Path):
    with zipfile.ZipFile(src) as z:
        z.extractall(dst)

def source_text(root: Path) -> str:
    chunks = []
    for p in all_text_files(root):
        chunks.append(f"\n// FILE: {p.relative_to(root)}\n{read_text(p)}")
    return "\n".join(chunks)

def code_text(root: Path) -> str:
    chunks = []
    for p in root.rglob("*"):
        if p.is_file() and p.suffix.lower() in {".kt", ".java", ".kts", ".gradle"}:
            chunks.append(f"\n// FILE: {p.relative_to(root)}\n{read_text(p)}")
    return "\n".join(chunks)

def version_audit(project: Path, full: str):
    if EXPECTED_PACKAGE not in full:
        die(f"Expected package/applicationId {EXPECTED_PACKAGE} not found.")
    if not re.search(r'versionName\s*=\s*["\']8\.3\.437["\']', full):
        die("v8.3.437 versionName contract not found.")
    if not re.search(r'versionCode\s*=\s*529\b', full):
        die("versionCode 529 contract not found.")

def escape_audit(full: str):
    # Static contract: a LIKE expression must have an ESCAPE clause. Accept
    # ordinary Kotlin escaped-string or triple-quoted SQL representations.
    occurrences = [m.start() for m in re.finditer(r"\bESCAPE\b", full, re.I)]
    if not occurrences:
        die("No SQL ESCAPE clause found in the current source.")

    sqlish = re.findall(r"(?is).{0,180}\bLIKE\b.{0,180}\bESCAPE\b.{0,80}", full)
    if not sqlish:
        die("ESCAPE exists but no LIKE ... ESCAPE SQL path was detected.")

    # Reject the common regression where the SQL literal contains two
    # backslashes as the runtime escape character. SQLite requires the ESCAPE
    # expression to evaluate to exactly one character.
    bad = []
    for s in sqlish:
        if re.search(r"ESCAPE\s*['\"]\\\\\\\\['\"]", s):
            bad.append(s)
    if bad:
        print("PHASE0 ESCAPE DIAGNOSTIC:")
        for item in bad:
            print(item.replace("\\n", "\\n"))
        die("Suspicious four-backslash SQL ESCAPE literal detected; inspect runtime escape character.")

def throwable_audit(current: Path, baseline: Path):
    def hits(root):
        out = set()
        for p in root.rglob("*.kt"):
            txt = read_text(p)
            if "catch (t: Throwable)" in txt or "catch(t: Throwable)" in txt:
                out.add((str(p.relative_to(root)), "catch(Throwable)"))
        return out
    cur = hits(current)
    old = hits(baseline) if baseline.exists() else set()
    new = sorted(cur - old)
    if new:
        print("PHASE0 THROWABLE DIAGNOSTIC:")
        for rel, _kind in new:
            p = current / rel
            lines = read_text(p).splitlines()
            for i, line in enumerate(lines):
                if "catch (t: Throwable)" in line or "catch(t: Throwable)" in line:
                    lo = max(0, i - 5)
                    hi = min(len(lines), i + 6)
                    print(f"--- {rel}:{i+1} ---")
                    print("\\n".join(lines[lo:hi]))
        die("New catch(Throwable) sites introduced relative to v8.3.435: " + ", ".join(f"{p}:{k}" for p,k in new))

def db_construction_diff(current: Path, baseline: Path):
    pattern = re.compile(r"\bQBankDb\s*\(")

    def counts(root):
        out = {}
        for p in root.rglob("*.kt"):
            rel = str(p.relative_to(root))
            if "/src/main/" not in rel or rel.endswith("/QBankDb.kt"):
                continue
            count = len(pattern.findall(read_text(p)))
            if count:
                out[rel] = count
        return out

    cur = counts(current)
    old = counts(baseline) if baseline.exists() else {}
    new = []
    for rel, count in sorted(cur.items()):
        previous = old.get(rel, 0)
        if count > previous:
            new.append(f"{rel}: {previous} -> {count}")

    if new:
        die("New production QBankDb(...) construction volume detected: " + "; ".join(new))

def test_presence_audit(project: Path):
    tests = []
    for base in [project / "app" / "src" / "test", project / "app" / "src" / "androidTest"]:
        if base.exists():
            tests.extend(base.rglob("*.kt"))
            tests.extend(base.rglob("*.java"))
    if not tests:
        die("No app JVM/instrumented tests found.")

    corpus = "\n".join(read_text(p) for p in tests)
    requirements = {
        "search ESCAPE regression": [
            r"ESCAPE", r"[%]", r"_", r"\\\\",
        ],
        "QBank import lifecycle": [r"import", r"QBank"],
        "QBank delete lifecycle": [r"delete", r"QBank"],
        "QBank recovery/visibility lifecycle": [r"recover|recovery", r"visible|visibility|catalog"],
    }
    missing = []
    for name, patterns in requirements.items():
        if not all(re.search(p, corpus, re.I) for p in patterns):
            missing.append(name)
    if missing:
        die("Required regression-test coverage is missing from executable test sources: " + ", ".join(missing))

def write_manifest(out: Path, src: Path, project: Path):
    manifest = {
        "phase": "0",
        "source_archive": src.name,
        "source_archive_sha256": __import__("hashlib").sha256(src.read_bytes()).hexdigest(),
        "expected_package": EXPECTED_PACKAGE,
        "expected_version_name": EXPECTED_VERSION_NAME,
        "expected_version_code": int(EXPECTED_VERSION_CODE),
        "classification": {
            "static_review": "PASS",
            "static_audit": "PASS",
            "executed_tests": "NOT_EXECUTED_BY_THIS_SCRIPT",
            "build": "NOT_EXECUTED_BY_THIS_SCRIPT",
            "ci": "NOT_EXECUTED_BY_THIS_SCRIPT",
            "device": "NOT_EXECUTED_BY_THIS_SCRIPT",
        },
        "project_dir": str(project),
    }
    out.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

def main():
    src = choose_zip()
    print(f"PHASE0 SOURCE: {src.name}")
    with tempfile.TemporaryDirectory(prefix="rovex-phase0-") as td:
        td = Path(td)
        current = td / "current"
        baseline = td / "baseline"
        current.mkdir()
        baseline.mkdir()
        extract(src, current)
        project = find_project(current)

        # Apply only the audited, exact Phase-0 repair set before evaluating
        # the executable source tree. The raw source remains an input artifact;
        # the release workflow must package the corrected tree.
        subprocess.run(
            [sys.executable, str(ROOT / "tools" / "phase0_apply_repairs.py"), str(current)],
            check=True,
        )

        baseline_zips = [p for p in SOURCE_ZIPS if "v8.3.435" in p.name.lower() and valid_source_zip(p)]
        if baseline_zips:
            extract(baseline_zips[0], baseline)

        full = source_text(current)
        version_audit(project, full)
        escape_audit(code_text(current))
        throwable_audit(current, baseline)
        db_construction_diff(current, baseline)
        test_presence_audit(project)

        out = ROOT / "phase0-verification-manifest.json"
        write_manifest(out, src, project)
        print("PHASE0 STATIC AUDIT: PASS (after exact Phase-0 repair application)")
        print("PHASE0 TEST-PRESENCE AUDIT: PASS")
        print("IMPORTANT: executable tests/build/device are separate CI gates.")

if __name__ == "__main__":
    main()
