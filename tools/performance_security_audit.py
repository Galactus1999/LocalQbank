#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(".")
SRC = ROOT / "app" / "src"
files = [p for p in SRC.rglob("*") if p.is_file() and p.suffix.lower() in {".kt",".java",".xml",".html",".js"}]

patterns = [
    ("CRITICAL", "Javascript bridge", re.compile(r"addJavascriptInterface\s*\(")),
    ("HIGH", "WebView JavaScript enabled", re.compile(r"setJavaScriptEnabled\s*\(\s*true\s*\)")),
    ("HIGH", "WebView file URL", re.compile(r"file://|setAllowFileAccess\s*\(\s*true\s*\)")),
    ("HIGH", "Unbounded WebView loading", re.compile(r"loadUrl\s*\(|loadDataWithBaseURL\s*\(")),
    ("MEDIUM", "Main-thread blocking primitive", re.compile(r"runBlocking\s*\{|Thread\.sleep\s*\(")),
    ("MEDIUM", "Broad Throwable catch", re.compile(r"catch\s*\(\s*[A-Za-z_][A-Za-z0-9_]*\s*:\s*Throwable\s*\)")),
    ("MEDIUM", "Dynamic hierarchy rebuild", re.compile(r"removeAllViews\s*\(|removeAllViewsInLayout\s*\(")),
    ("MEDIUM", "Repeated HTML parsing", re.compile(r"Html\.fromHtml\s*\(")),
    ("MEDIUM", "Bitmap decode", re.compile(r"BitmapFactory\.decode|decodeStream\s*\(")),
]

hits = []
for p in files:
    try:
        text = p.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        continue
    for severity, label, rx in patterns:
        for m in rx.finditer(text):
            line = text.count("\n", 0, m.start()) + 1
            hits.append((severity, label, str(p), line))

rank = {"CRITICAL":0,"HIGH":1,"MEDIUM":2}
hits.sort(key=lambda x:(rank[x[0]],x[2],x[3]))

print("=== ROVEX PERFORMANCE / SECURITY SOURCE AUDIT ===")
print(f"Scanned files: {len(files)}")
print(f"Findings: {len(hits)}")
for severity, label, path, line in hits:
    print(f"[{severity}] {label}: {path}:{line}")

print("\nInterpretation:")
print("- This is a source-level detector, not proof of exploitability or runtime lag.")
print("- CRITICAL/HIGH WebView findings require trust-boundary review.")
print("- MEDIUM findings require profiling/context review before refactoring.")
print("- The audit intentionally does not fail the build: evidence must drive changes.")
