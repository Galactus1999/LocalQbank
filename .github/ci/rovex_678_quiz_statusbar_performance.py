#!/usr/bin/env python3
"""Phase 678: remove the quiz's pale top strip and add bounded performance safeguards."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
activity_path = pkg / "QuizActivity.kt"
builder_path = pkg / "QuizLayoutBuilder.kt"
theme_path = pkg / "ThemeManager.kt"
for p in (gradle, activity_path, builder_path, theme_path):
    if not p.is_file():
        raise SystemExit(f"[678] FATAL: required source missing: {p}")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.675"' not in g or re.search(r"\bversionCode\s*=\s*761\b", g) is None:
    raise SystemExit("[678] FATAL: expected v8.3.675 / code 761 after Phase 677")

activity = activity_path.read_text(encoding="utf-8")
# The screenshot's pale strip is the system-bar region above the quiz root. Keep the safe inset
# so content never moves under system icons, but paint that region with the same dark hue as the
# quiz header and explicitly use light status-bar icons. This removes the visible pale band
# without globally changing status-bar policy for other Activities.
on_create = re.search(r"override\s+fun\s+onCreate\s*\([^)]*\)\s*\{", activity)
if not on_create:
    raise SystemExit("[678] FATAL: QuizActivity.onCreate anchor missing/ambiguous")
marker = "/* ROVEX_QUIZ_STATUS_BAR_SURFACE */"
if marker not in activity:
    insert = (
        on_create.group(0) + "\n"
        "        " + marker + "\n"
        "        // Match the system-bar region to the quiz header; keep AdaptiveLayoutManager's\n"
        "        // safe inset so question controls remain below status icons/cutouts.\n"
        "        window.statusBarColor = android.graphics.Color.rgb(36, 24, 72)\n"
        "        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)\n"
        "            .isAppearanceLightStatusBars = false\n"
    )
    activity = activity[:on_create.start()] + insert + activity[on_create.end():]
else:
    if activity.count(marker) != 1:
        raise SystemExit("[678] FATAL: duplicate quiz status-bar marker")
    if "window.statusBarColor = android.graphics.Color.rgb(36, 24, 72)" not in activity:
        raise SystemExit("[678] FATAL: status-bar marker exists without expected color assignment")
# No forced fullscreen and no global insets changes: preserve cutout/navigation safety.
if "isAppearanceLightStatusBars = false" not in activity:
    raise SystemExit("[678] FATAL: light status-bar icons not configured")

# Verify existing background caching and static-render policy instead of adding a second cache or
# an animation loop. Fail if the expected bitmap cache boundary disappears.
theme = theme_path.read_text(encoding="utf-8")
if "RovexBundledVisualAssets.bitmapIfReady" not in theme and "backgroundDrawable(c:Context)" not in theme:
    raise SystemExit("[678] FATAL: ThemeManager wallpaper cache/background boundary not found")
builder = builder_path.read_text(encoding="utf-8")
for required in ('tag = "quiz:header"', 'tag = "quiz:progress"', 'LockedScrollView(context)', 'onAdvanceQuestion()'):
    if required not in builder:
        raise SystemExit("[678] FATAL: quiz structure or interaction anchor lost: " + required)
if "ValueAnimator" in builder or "ObjectAnimator" in builder:
    raise SystemExit("[678] REVIEW REQUIRED: unexpected per-quiz animator detected; do not auto-edit animation ownership")

activity_path.write_text(activity, encoding="utf-8")
g = g.replace('versionName = "8.3.675"', 'versionName = "8.3.676"', 1).replace("versionCode = 761", "versionCode = 762", 1)
if 'versionName = "8.3.676"' not in g or re.search(r"\bversionCode\s*=\s*762\b", g) is None:
    raise SystemExit("[678] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")

print("[678] PASS: quiz status-bar strip now uses header-matched dark surface; safe content inset preserved.")
print("[678] PASS: system-bar styling scoped to QuizActivity; no global fullscreen/inset changes.")
print("[678] PASS: wallpaper cache boundary and static quiz structure preserved.")
print("[678] NOTE: battery impact is not claimed from source inspection; device/benchmark measurements remain required.")
