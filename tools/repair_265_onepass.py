#!/usr/bin/env python3
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(".")
SOURCE = next(ROOT.glob("Rovex_v8_3_265_*.zip"), None)
BASE_COMMIT = "f3aff416a30ae7a50aca62fc05adfa7918de6f7a"
BASE_ZIP = "Rovex_v8_3_264_Frankenstein_Minimal_UI_Launcher_Source.zip"
PATCH = "rovex_patch_8265.py"

def git_show(path):
    return subprocess.check_output(
        ["git", "show", f"{BASE_COMMIT}:{path}"], stderr=subprocess.STDOUT
    )

with tempfile.TemporaryDirectory() as td:
    td = Path(td)
    baseline_zip = td / "baseline.zip"
    baseline_zip.write_bytes(git_show(BASE_ZIP))
    patch_file = td / "patch.py"
    patch = git_show(PATCH).decode("utf-8")
    bad = 'lines=s.splitlines(True); fs=next(i for i,l in enumerate(lines) if "BEN + FREE AI" in l); fe=next(i for i in range(fs,len(lines)) if "root.addView(row)" in lines[i]); s="".join(lines[:fs]) + footer + "\\n" + "".join(lines[fe+1:])'
    good = 'fs=s.index("        val core = TextView(this)"); fe=s.index("root.addView(row)",fs)+len("root.addView(row)")'
    if bad not in patch:
        raise SystemExit("8.3.265 footer-index defect marker not found")
    patch = patch.replace(bad, good, 1)
    titanic = 'p/"app/src/main/java/com/localqbank/library/RovexHeaderTitanicView.kt").unlink(missing_ok=True)'
    patch = patch.replace(titanic, 'p/"app/src/main/java/com/localqbank/library/RovexHeaderTitanicView.kt").exists()', 1)
    patch_file.write_text(patch, encoding="utf-8")

    work = td / "work"
    work.mkdir()
    with zipfile.ZipFile(baseline_zip) as z:
        z.extractall(work)

    project = next(work.rglob("settings.gradle.kts")).parent
    subprocess.run(["python3", str(patch_file), str(project)], check=True)

    # The patch is allowed to alter the baseline project, but the resulting source
    # archive must contain exactly the corrected project and no CI repair leftovers.
    corrected = ROOT / "Rovex_v8_3_265_Minimal_UI_User_Performance_FastAI_PDF_Library_Source.zip"
    SOURCE.unlink()
    with zipfile.ZipFile(corrected, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(work.rglob("*")):
            if path.is_file():
                z.write(path, path.relative_to(work))
    if not corrected.exists() or corrected.stat().st_size == 0:
        raise SystemExit("corrected source ZIP was not created")
print(f"Corrected source: {corrected.name} ({corrected.stat().st_size} bytes)")
