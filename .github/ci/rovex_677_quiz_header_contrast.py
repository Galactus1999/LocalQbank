#!/usr/bin/env python3
"""Phase 677: enforce readable quiz-header contrast over dark or bright wallpapers."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
builder_path = pkg / "QuizLayoutBuilder.kt"
activity_path = pkg / "QuizActivity.kt"
if not all(p.is_file() for p in (gradle, builder_path, activity_path)):
    raise SystemExit("[677] FATAL: required Gradle, QuizLayoutBuilder, or QuizActivity source missing")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.674"' not in g or re.search(r"\bversionCode\s*=\s*760\b", g) is None:
    raise SystemExit("[677] FATAL: expected v8.3.674 / code 760 after Phase 676")

builder = builder_path.read_text(encoding="utf-8")
activity = activity_path.read_text(encoding="utf-8")

replacements = [
    # A localized dark translucent scrim preserves the wallpaper while protecting text from
    # bright/dark image details. It is limited to the two quiz-header rows, not the whole screen.
    ('background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)\n            elevation = dp(1).toFloat()',
     'background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(\n                 Color.argb(232, 17, 13, 43), Color.argb(184, 36, 24, 72)))\n            elevation = dp(1).toFloat()'),
    ('setTextColor(ThemeManager.text(context))\n            maxLines = 1',
     'setTextColor(Color.rgb(250, 251, 255))\n            maxLines = 1'),
    ('setTextColor(ThemeManager.muted(context))\n            maxLines = 1',
     'setTextColor(Color.rgb(224, 229, 245))\n            maxLines = 1'),
    ('background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)\n        }\n        val counter = TextView(context).apply {',
     'background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(\n                 Color.argb(224, 17, 13, 43), Color.argb(160, 36, 24, 72)))\n        }\n        val counter = TextView(context).apply {'),
    ('setTextColor(ThemeManager.muted(context))\n            gravity = Gravity.CENTER_VERTICAL',
     'setTextColor(Color.rgb(250, 251, 255))\n            gravity = Gravity.CENTER_VERTICAL'),
]
for old, new in replacements:
    if builder.count(old) != 1:
        raise SystemExit("[677] FATAL: missing/ambiguous quiz-header contrast anchor: " + old[:100].replace("\n", "\\n"))
    builder = builder.replace(old, new, 1)

# Verify runtime sizing still matches the compact layout. This phase changes visual contrast,
# not window insets or tap callbacks.
if "height = dp(38)" not in activity or "height = dp(26)" not in activity:
    raise SystemExit("[677] FATAL: expected Phase 676 runtime row sizing is missing")
if "tag = \"quiz:header\"" not in builder or "tag = \"quiz:progress\"" not in builder:
    raise SystemExit("[677] FATAL: quiz header/progress identity tags were lost")
for required in ("onShowSettings(this)", "onShowQuestionJump(this)", "onAdvanceQuestion()", "onShowBookmark(this)", "Previous question"):
    if required not in builder:
        raise SystemExit("[677] FATAL: interaction callback missing: " + required)
if "Color.rgb(250, 251, 255)" not in builder or "Color.rgb(224, 229, 245)" not in builder:
    raise SystemExit("[677] FATAL: readable title/source/progress foregrounds not installed")
if builder.count("GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(") < 2:
    raise SystemExit("[677] FATAL: localized header scrims missing")

builder_path.write_text(builder, encoding="utf-8")
g = g.replace('versionName = "8.3.674"', 'versionName = "8.3.675"', 1).replace("versionCode = 760", "versionCode = 761", 1)
if 'versionName = "8.3.675"' not in g or re.search(r"\bversionCode\s*=\s*761\b", g) is None:
    raise SystemExit("[677] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")
print("[677] PASS: quiz title, filename, and progress now use explicit high-contrast light foregrounds.")
print("[677] PASS: translucent dark scrims are confined to quiz header/progress rows; wallpaper remains visible.")
print("[677] PASS: compact row sizes, controls, tags, question/settings/bookmark/navigation callbacks preserved.")
