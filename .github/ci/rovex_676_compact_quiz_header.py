#!/usr/bin/env python3
"""Phase 676: compact the actual quiz header rows and keep their runtime sizing in sync."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
builder_path = pkg / "QuizLayoutBuilder.kt"
activity_path = pkg / "QuizActivity.kt"
if not all(p.is_file() for p in (gradle, builder_path, activity_path)):
    raise SystemExit("[676] FATAL: required Gradle, QuizLayoutBuilder, or QuizActivity source missing")

gradle_text = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.673"' not in gradle_text or re.search(r"\bversionCode\s*=\s*759\b", gradle_text) is None:
    raise SystemExit("[676] FATAL: expected v8.3.673 / code 759 after Phase 675")

builder = builder_path.read_text(encoding="utf-8")
activity = activity_path.read_text(encoding="utf-8")

# Narrow, anchored changes: shrink the two quiz rows, their children, and the duplicate
# runtime layout override. Keep question content, state, callbacks, and bottom dock intact.
builder_replacements = [
    ("top.addView(back, LinearLayout.LayoutParams(dp(38), dp(38)).apply { setMargins(0, 0, dp(10), 0) })",
     "top.addView(back, LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(0, 0, dp(8), 0) })"),
    ("titleBox.addView(tv, LinearLayout.LayoutParams(-1, dp(24)))",
     "titleBox.addView(tv, LinearLayout.LayoutParams(-1, dp(21)))"),
    ("titleBox.addView(subRow, LinearLayout.LayoutParams(-1, dp(20)))",
     "titleBox.addView(subRow, LinearLayout.LayoutParams(-1, dp(17)))"),
    ("top.addView(titleBox, LinearLayout.LayoutParams(0, dp(44), 1f))",
     "top.addView(titleBox, LinearLayout.LayoutParams(0, dp(38), 1f))"),
    ("top.addView(submit, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(dp(6), 0, 0, 0) })",
     "top.addView(submit, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(4), 0, 0, 0) })"),
    ("root.addView(top, LinearLayout.LayoutParams(-1, dp(46)))",
     "root.addView(top, LinearLayout.LayoutParams(-1, dp(38)))"),
    ("infoRow.addView(counter, LinearLayout.LayoutParams(0, dp(30), 1f))",
     "infoRow.addView(counter, LinearLayout.LayoutParams(0, dp(24), 1f))"),
    ("infoRow.addView(settingsButton, LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(4),0,dp(4),0) })",
     "infoRow.addView(settingsButton, LinearLayout.LayoutParams(dp(24), dp(24)).apply { setMargins(dp(3),0,dp(3),0) })"),
    ("infoRow.addView(jumpButton, LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(6), 0, 0, 0) })",
     "infoRow.addView(jumpButton, LinearLayout.LayoutParams(dp(24), dp(24)).apply { setMargins(dp(4), 0, 0, 0) })"),
    ("root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(34)))",
     "root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(26)))"),
]
for old, new in builder_replacements:
    if builder.count(old) != 1:
        raise SystemExit("[676] FATAL: builder anchor missing/ambiguous: " + old)
    builder = builder.replace(old, new, 1)

activity_replacements = [
    ("top.layoutParams = top.layoutParams.apply { height = dp(46) }",
     "top.layoutParams = top.layoutParams.apply { height = dp(38) }"),
    ("info.layoutParams = info.layoutParams.apply { height = dp(34) }",
     "info.layoutParams = info.layoutParams.apply { height = dp(26) }"),
]
for old, new in activity_replacements:
    if activity.count(old) != 1:
        raise SystemExit("[676] FATAL: runtime sizing anchor missing/ambiguous: " + old)
    activity = activity.replace(old, new, 1)

# Postconditions prevent a builder/runtime mismatch or accidental edits to unrelated layouts.
for forbidden in (
    "root.addView(top, LinearLayout.LayoutParams(-1, dp(46)))",
    "root.addView(infoRow, LinearLayout.LayoutParams(-1, dp(34)))",
    "infoRow.addView(settingsButton, LinearLayout.LayoutParams(dp(32), dp(32))",
    "infoRow.addView(jumpButton, LinearLayout.LayoutParams(dp(32), dp(32))",
):
    if forbidden in builder:
        raise SystemExit("[676] FATAL: stale oversized builder sizing remains: " + forbidden)
if "height = dp(46)" in activity or "height = dp(34)" in activity:
    raise SystemExit("[676] FATAL: stale runtime sizing override remains")
for required in ("tag = \"quiz:header\"", "tag = \"quiz:progress\"", "onShowSettings(this)", "onShowQuestionJump(this)", "onAdvanceQuestion()"):
    if required not in builder:
        raise SystemExit("[676] FATAL: required quiz interaction/state anchor lost: " + required)
if "onShowBookmark(this)" not in builder or "Previous question" not in builder:
    raise SystemExit("[676] FATAL: bottom/question navigation callback lost")

builder_path.write_text(builder, encoding="utf-8")
activity_path.write_text(activity, encoding="utf-8")
gradle_text = gradle_text.replace('versionName = "8.3.673"', 'versionName = "8.3.674"', 1).replace("versionCode = 759", "versionCode = 760", 1)
if 'versionName = "8.3.674"' not in gradle_text or re.search(r"\bversionCode\s*=\s*760\b", gradle_text) is None:
    raise SystemExit("[676] FATAL: version bump postcondition failed")
gradle.write_text(gradle_text, encoding="utf-8")
print("[676] PASS: quiz header reduced from 80dp to 64dp (46+34 -> 38+26).")
print("[676] PASS: builder and runtime sizing agree; title, source, status, progress, timer, settings, and navigation callbacks preserved.")
print("[676] PASS: scoped to QuizLayoutBuilder/QuizActivity; no global theme or other-screen header rewrite.")
