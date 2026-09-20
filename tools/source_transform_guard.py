from pathlib import Path
import re
import sys

ROOT = Path("app/src/main")
bad = []
kotlin = list(ROOT.rglob("*.kt"))

for path in kotlin:
    source = path.read_text(encoding="utf-8", errors="strict")
    if r"\nprivate " in source or r"\npublic " in source or r"\noverride " in source:
        bad.append(f"{path}: literal escaped newline before declaration")
    for line_no, line in enumerate(source.splitlines(), 1):
        if re.search(r"(^|[^\\])\b(?:class|object|fun|val|var|private|public|override)\b", line) and "\\" in line:
            bad.append(f"{path}:{line_no}: suspicious backslash in declaration")

if bad:
    print("\n".join(bad))
    sys.exit("SOURCE_TRANSFORM_GUARD_FAILED")

print(f"SOURCE_TRANSFORM_GUARD=PASS ({len(kotlin)} Kotlin files)")
