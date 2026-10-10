#!/usr/bin/env python3
"""Read-only Rovex visual architecture audit.

Scans the exact Android project selected by Adaptive Android CI.
It never edits source files. The report is intended to expose where
visual behavior currently lives so future changes can be consolidated safely.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path

TEXT_EXTS = {".kt", ".java", ".xml", ".gradle", ".kts", ".py", ".json", ".pro", ".properties"}
SKIP_DIRS = {".git", "build", ".gradle", ".idea", "node_modules"}

PATTERNS = {
    "hardcoded_hex_colors": re.compile(r"#[0-9a-fA-F]{6,8}\b"),
    "android_color_literals": re.compile(r"Color\.(?:rgb|argb|BLACK|WHITE|RED|GREEN|BLUE|TRANSPARENT)\b"),
    "gradient_drawable": re.compile(r"GradientDrawable\b"),
    "canvas_draw": re.compile(r"Canvas\s*\.|\.draw(?:Rect|RoundRect|Circle|Path|Bitmap|Text|Color|Line|Arc|Oval|Point)\b"),
    "runtime_shader": re.compile(r"RuntimeShader\b|android\.graphics\.Shader\b|setRuntimeShader\b"),
    "generic_shader": re.compile(r"\b(?:LinearGradient|RadialGradient|SweepGradient|ComposeShader)\b"),
    "lottie": re.compile(r"lottie|LottieAnimationView|LottieDrawable", re.I),
    "rive": re.compile(r"\bRive(?:Animation|View)?\b|com\.rive\.", re.I),
    "android_animation": re.compile(r"ObjectAnimator|ValueAnimator|AnimatorSet|ViewPropertyAnimator|AlphaAnimation|TranslateAnimation|ScaleAnimation|RotateAnimation|TransitionManager", re.I),
    "compose_animation": re.compile(r"animate[A-Z]|AnimatedVisibility|rememberInfiniteTransition", re.I),
    "haptics": re.compile(r"HapticFeedbackConstants|performHapticFeedback|Vibrator|VibrationEffect", re.I),
    "audio": re.compile(r"SoundPool|MediaPlayer|AudioTrack|playSound|soundPool", re.I),
    "theme_manager": re.compile(r"ThemeManager\b|RovexThemeEngine\b"),
    "background_assignment": re.compile(r"\.background\s*=|setBackground(?:Color|Resource)?\s*\("),
    "alpha_mutation": re.compile(r"\.alpha\s*=|setAlpha\s*\("),
}

def iter_files(root: Path):
    for p in root.rglob("*"):
        if not p.is_file() or p.suffix.lower() not in TEXT_EXTS:
            continue
        if any(part in SKIP_DIRS for part in p.parts):
            continue
        yield p

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True, type=Path)
    ap.add_argument("--output", required=False, type=Path)
    args = ap.parse_args()

    root = args.project.resolve()
    if not root.is_dir():
        raise SystemExit(f"Project directory does not exist: {root}")

    counts = Counter()
    files_by_category: dict[str, set[str]] = {k: set() for k in PATTERNS}
    hardcoded_examples: list[dict[str, object]] = []
    source_files = 0
    lines = 0

    for path in iter_files(root):
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        source_files += 1
        rel = str(path.relative_to(root))
        file_lines = text.count("\n") + (1 if text else 0)
        lines += file_lines

        for category, pattern in PATTERNS.items():
            matches = list(pattern.finditer(text))
            if not matches:
                continue
            counts[category] += len(matches)
            files_by_category[category].add(rel)
            if category == "hardcoded_hex_colors" and len(hardcoded_examples) < 30:
                for m in matches[:5]:
                    line = text.count("\n", 0, m.start()) + 1
                    hardcoded_examples.append({"file": rel, "line": line, "value": m.group(0)})

    def sorted_files(category: str):
        return sorted(files_by_category[category])

    report = {
        "schema": "rovex.visual-audit.v1",
        "project": str(root),
        "source_files": source_files,
        "source_lines": lines,
        "counts": dict(sorted(counts.items())),
        "files_by_category": {k: sorted_files(k) for k in PATTERNS},
        "hardcoded_color_examples": hardcoded_examples,
        "interpretation": {
            "purpose": "Inventory only. Counts are not automatic defects.",
            "next_action": "Classify findings as KEEP, REPLACE, CONSOLIDATE, DELETE, or MOVE before runtime refactoring.",
        },
    }

    out = args.output or (root / "rovex-visual-audit.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print("Rovex visual architecture audit")
    print(f"Project: {root}")
    print(f"Text source files: {source_files}")
    print(f"Source lines: {lines}")
    for category, value in sorted(counts.items()):
        print(f"{category}: {value} matches across {len(files_by_category[category])} files")
    print(f"Report: {out}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
