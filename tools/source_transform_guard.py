from pathlib import Path
import sys

ROOT = Path("app/src/main")
bad = []
kotlin = list(ROOT.rglob("*.kt"))

for path in kotlin:
    source = path.read_text(encoding="utf-8", errors="strict")
    lines = source.splitlines()
    for line_no, line in enumerate(lines, 1):
        # Only reject literal escaped newlines immediately before declarations.
        if "\\nprivate " in line or "\\npublic " in line or "\\noverride " in line:
            bad.append(f"{path}:{line_no}: literal escaped newline before declaration")

if bad:
    print("\n".join(bad))
    sys.exit("SOURCE_TRANSFORM_GUARD_FAILED")

print(f"SOURCE_TRANSFORM_GUARD=PASS ({len(kotlin)} Kotlin files)")
