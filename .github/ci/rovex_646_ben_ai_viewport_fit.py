#!/usr/bin/env python3
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"
gradle = P / "app/build.gradle.kts"
dialog = K / "BenQuestionAiContextDialog.kt"
profile = K / "BenExamProfile.kt"
policy_test = P / "app/src/test/java/com/localqbank/library/BenQuestionAiModePromptTest.kt"

for required in (gradle, dialog, profile, policy_test):
    if not required.is_file():
        raise SystemExit(f"[646] required file missing: {required.relative_to(P)}")

g = gradle.read_text()
if 'versionName = "8.3.645"' not in g or "versionCode = 731" not in g:
    raise SystemExit("[646] wrong baseline; expected v8.3.645 / versionCode 731")
g = g.replace('versionName = "8.3.645"', 'versionName = "8.3.646"', 1)
g = g.replace("versionCode = 731", "versionCode = 732", 1)
gradle.write_text(g)

s = dialog.read_text()
mode_anchor = 'modeScroll.addView(modeRow, android.view.ViewGroup.LayoutParams(-1, dp(activity, 34)))'
if s.count(mode_anchor) != 1:
    raise SystemExit(f"[646] expected one mode viewport anchor, found {s.count(mode_anchor)}")
if "modeScroll.isFillViewport = true" not in s:
    s = s.replace(mode_anchor, 'modeScroll.isFillViewport = true\n        modeScroll.isHorizontalScrollBarEnabled = false\n        modeScroll.overScrollMode = View.OVER_SCROLL_NEVER\n        ' + mode_anchor, 1)

footer_anchor = 'actionsScroll.addView(actions, android.view.ViewGroup.LayoutParams(-1, dp(activity, 32)))'
if s.count(footer_anchor) != 1:
    raise SystemExit(f"[646] expected one footer viewport anchor, found {s.count(footer_anchor)}")
if "actionsScroll.isFillViewport = true" not in s:
    s = s.replace(footer_anchor, 'actionsScroll.isFillViewport = true\n        actionsScroll.isHorizontalScrollBarEnabled = false\n        actionsScroll.overScrollMode = View.OVER_SCROLL_NEVER\n        ' + footer_anchor, 1)
dialog.write_text(s)

# The weighted rows must be measured against the visible viewport, not an unbounded
# HorizontalScrollView width; fillViewport forces the weighted children to share screen width.
s = dialog.read_text()
for needle in (
    "modeScroll.isFillViewport = true",
    "actionsScroll.isFillViewport = true",
    "modeScroll.isHorizontalScrollBarEnabled = false",
    "actionsScroll.isHorizontalScrollBarEnabled = false",
    "modeScroll.overScrollMode = View.OVER_SCROLL_NEVER",
    "actionsScroll.overScrollMode = View.OVER_SCROLL_NEVER",
    "LinearLayout.LayoutParams(0, dp(activity, 32), 1f)",
    "LinearLayout.LayoutParams(0, dp(activity, 30), 1f)",
):
    if needle not in s:
        raise SystemExit(f"[646] missing viewport-fit contract: {needle}")

# Retain the distinct prompt contracts introduced in Phase 645.
p = profile.read_text()
for needle in (
    "previous-year-question provenance",
    "whole-topic revision map",
    "practice speculation",
    "option-by-option discriminator analysis",
):
    if needle not in p:
        raise SystemExit(f"[646] exam prompt contract missing: {needle}")
t = policy_test.read_text()
if "fourModesHaveDistinctExamPreparationJobs" not in t:
    raise SystemExit("[646] distinct-mode prompt regression test missing")

print("[646] applied v8.3.646 / versionCode 732")
print("[646] mode chips and footer use fillViewport so weighted actions are constrained to screen width")
print("[646] horizontal scrollbars and overscroll affordances disabled for both four-button rows")
print("[646] distinct NEET-PG/INI-CET prompt contracts preserved")
