#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1]).resolve()
TARGET_CLASSES = [
    "RovexClinicalDayHomeBackgroundDrawable",
    "RovexLivingBackgroundDrawable",
]

def find_class_file(class_name: str) -> Path:
    hits = []
    marker = f"class {class_name}"
    for p in ROOT.rglob("*.kt"):
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        if marker in text:
            hits.append(p)
    if len(hits) != 1:
        raise SystemExit(f"Expected exactly one source for {class_name}, found {len(hits)}: {hits}")
    return hits[0]

def find_context_expression(text: str, class_name: str) -> str:
    # Prefer a stored Context property.
    m = re.search(r"(?m)^\\s*(?:private\\s+|internal\\s+|public\\s+)?(?:val|var)\\s+(\\w+)\\s*:\\s*Context\\b", text)
    if m:
        return m.group(1)

    # Otherwise inspect the primary constructor for a Context parameter.
    m = re.search(r"class\\s+" + re.escape(class_name) + r"\\s*\\(([^)]*)\\)", text, re.S)
    if m:
        params = m.group(1)
        m2 = re.search(r"(?:val|var)?\\s*(\\w+)\\s*:\\s*Context\\b", params)
        if m2:
            return m2.group(1)

    raise SystemExit(f"Could not safely locate a Context for {class_name}; refusing blind rendering patch")

def find_draw_body_span(text: str) -> tuple[int, int]:
    m = re.search(r"(?m)^\\s*override\\s+fun\\s+draw\\s*\\(\\s*canvas\\s*:\\s*Canvas\\s*\\)\\s*\\{", text)
    if not m:
        raise SystemExit("Expected override fun draw(canvas: Canvas) was not found")
    start = text.find("{", m.start())
    depth = 0
    in_string = False
    escaped = False
    in_line_comment = False
    in_block_comment = False
    i = start
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            i += 1
            continue
        if ch == "/" and nxt == "/":
            in_line_comment = True
            i += 2
            continue
        if ch == "/" and nxt == "*":
            in_block_comment = True
            i += 2
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return start, i
        i += 1
    raise SystemExit("Unbalanced braces while locating draw()")

for class_name in TARGET_CLASSES:
    path = find_class_file(class_name)
    text = path.read_text(encoding="utf-8")
    context_expr = find_context_expression(text, class_name)
    start, end = find_draw_body_span(text)
    body = text[start + 1:end]
    if "v8.3.616 LIGHT-BACKGROUND GUARD" in body:
        raise SystemExit(f"Patch already present in {path}")

    insertion = f'''
        // v8.3.616 LIGHT-BACKGROUND GUARD
        // The decorative background must never be allowed to become the
        // opaque/dark base of a light theme. Keep the existing dark-theme
        // artwork untouched; replace only the light-theme canvas with the
        // authoritative opaque theme color.
        if (!ThemeManager.isDark({context_expr})) {{
            canvas.drawColor(ThemeManager.bg({context_expr}))
            return
        }}
'''
    patched = text[:start + 1] + insertion + body + text[end:]
    path.write_text(patched, encoding="utf-8")
    print(f"Patched light-theme background guard: {path.relative_to(ROOT)}")

gradle_candidates = []
for p in ROOT.rglob("build.gradle.kts"):
    try:
        t = p.read_text(encoding="utf-8", errors="ignore")
        if "versionCode = 701" in t and 'versionName = "8.3.615"' in t:
            gradle_candidates.append(p)
    except OSError:
        pass
if len(gradle_candidates) != 1:
    raise SystemExit(f"Expected exactly one v8.3.615 app Gradle file, found {len(gradle_candidates)}: {gradle_candidates}")

gradle = gradle_candidates[0]
s = gradle.read_text(encoding="utf-8")
s = s.replace("versionCode = 701", "versionCode = 702", 1)
s = s.replace('versionName = "8.3.615"', 'versionName = "8.3.616"', 1)
gradle.write_text(s, encoding="utf-8")
print(f"Bumped version: {gradle.relative_to(ROOT)} -> 8.3.616 / 702")
