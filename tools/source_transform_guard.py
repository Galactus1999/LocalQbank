from pathlib import Path
import re
import sys

ROOT = Path("app/src/main")
bad = []
kotlin = list(ROOT.rglob("*.kt"))

for path in kotlin:
    text = path.read_text(encoding="utf-8", errors="strict")
    if r"\nprivate " in text or r"\npublic " in text or r"\noverride " in text:
        bad.append(f"{path}: literal escaped newline before declaration")
    if r"\r\n" in text or r"\r" in text:
    for line_no, line in enumerate(text.splitlines(), 1):
        if re.search(r"(^|[^\\])\\b(?:class|object|fun|val|var|private|public|override)\\b", line) and "\\" in line:
            bad.append(f"{path}:{line_no}: suspicious backslash in declaration")

if bad:
    print("\n".join(bad))
    sys.exit("SOURCE_TRANSFORM_GUARD_FAILED")

print(f"SOURCE_TRANSFORM_GUARD=PASS ({len(kotlin)} Kotlin files)")
