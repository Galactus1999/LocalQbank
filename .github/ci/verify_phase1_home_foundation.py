#!/usr/bin/env python3
"""Fail-closed verification of the generated Phase 1 Home/theme source.

Run after the 660-663 overlays against the exact project CI will build. This
checks generated output, not merely the presence of overlay scripts.
"""
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: verify_phase1_home_foundation.py <generated-project> <repository-root>")

project = Path(sys.argv[1]).resolve()
repo = Path(sys.argv[2]).resolve()
pkg = project / "app/src/main/java/com/localqbank/library"
gradle = project / "app/build.gradle.kts"
home_path = pkg / "RovexHomeRevolution.kt"
tokens_path = pkg / "RovexHomeThemeTokens.kt"
palette_path = pkg / "RovexPremiumPalette.kt"
compact_test_path = project / "app/src/androidTest/java/com/localqbank/library/RovexHomeCompactGeometryRegressionTest.kt"
theme_test_path = project / "app/src/androidTest/java/com/localqbank/library/RovexHomeThemeFoundationRegressionTest.kt"
admission_test_path = project / "app/src/androidTest/java/com/localqbank/library/QBankDeletionRecoveryAdmissionTest.kt"

required_files = (
    gradle, home_path, tokens_path, palette_path,
    compact_test_path, theme_test_path, admission_test_path,
)
missing = [str(path) for path in required_files if not path.is_file()]
if missing:
    raise SystemExit("[Phase 1] generated source is incomplete; missing: " + ", ".join(missing))

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.660"' not in g or "versionCode = 746" not in g:
    raise SystemExit("[Phase 1] wrong generated release version; expected v8.3.660 / versionCode 746")
if 'applicationId = "com.localqbank.library"' not in g:
    raise SystemExit("[Phase 1] package/applicationId changed unexpectedly")

home = home_path.read_text(encoding="utf-8")
if (
    'setPadding(d(12,a),d(6,a),d(12,a),d(6,a))' not in home
    or 'LinearLayout.LayoutParams(-1,d(72,a)).apply{topMargin=d(4,a)}' not in home
    or 'includeFontPadding=false' not in home
):
    raise SystemExit("[Phase 1] measured Today Progress internal-height correction missing from generated Home source")
tokens = tokens_path.read_text(encoding="utf-8")
palette = palette_path.read_text(encoding="utf-8")
compact = compact_test_path.read_text(encoding="utf-8")
theme_test = theme_test_path.read_text(encoding="utf-8")
admission = admission_test_path.read_text(encoding="utf-8")

for marker, label in (
    ("RovexPremiumPalette.forKey(ThemeManager.get(context), ThemeManager.isDark(context))", "ThemeManager-backed semantic palette adapter"),
    ("fun surface(context: Context", "surface token"),
    ("fun text(context: Context)", "text token"),
    ("fun accent(context: Context)", "accent token"),
    ("fun border(context: Context", "border token"),
):
    if marker not in tokens:
        raise SystemExit("[Phase 1] semantic token contract missing: " + label)

for marker in ("surfaceContainer", "surfaceElevated", "onSurface", "onSurfaceVariant", "outlineStrong", "selectedContainer"):
    if marker not in palette:
        raise SystemExit("[Phase 1] palette role missing: " + marker)

card_start = "private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable"
card_end = "private fun withMotionSurface("
if home.count(card_start) != 1 or home.count(card_end) != 1:
    raise SystemExit("[Phase 1] Home card/motion helper boundaries changed; manual audit required")
card = home[home.index(card_start):home.index(card_end, home.index(card_start))]
if "RovexHomeThemeTokens.roles(c)" not in card or "palette.surfaceContainer" not in card or "palette.surfaceElevated" not in card or "palette.outline" not in card:
    raise SystemExit("[Phase 1] Home cards are not using shared semantic surface/elevation/border roles")
if "ThemeManager.pastelAccentFill(c,index)" in card or "RovexColorFlowTextView.colorOne" in card or "RovexColorFlowTextView.colorTwo" in card:
    raise SystemExit("[Phase 1] text-flow/Pastel-specific colors still own Home card backgrounds")

motion_start = home.index(card_end)
motion_end = home.find("private fun installMotionSurfaces(", motion_start)
if motion_end < 0:
    raise SystemExit("[Phase 1] Home motion installer boundary missing")
motion = home[motion_start:motion_end]
if "return view" not in motion:
    raise SystemExit("[Phase 1] Home motion wrapper may be mutating original layout geometry")
if "FrameLayout.LayoutParams(-1, if (fillHeight) -1 else -2)" in motion or "view.layoutParams =" in motion:
    raise SystemExit("[Phase 1] layout-mutating motion wrapper regression detected")

for marker in (
    'assertHeight("rovex_home_motion_header", 96f)',
    'assertHeight("rovex_home_search", 50f)',
    'assertHeight("rovex_home_online", 70f)',
    'assertHeight("rovex_home_daily_motivation", 88f)',
    'assertHeight("rovex_home_today_progress", 190f)',
    'assertHeight("modern_feature_qbank", 132f)',
    'assertHeight("modern_feature_flashcards", 132f)',
):
    if marker not in compact:
        raise SystemExit("[Phase 1] compact measured-geometry regression missing: " + marker)

if "for (attempt in 0 until 400)" not in theme_test or "within 20s after fresh MainActivity launch=" not in theme_test or "rootChildren=" not in theme_test or "isHomeDashboardAttached(root)" not in theme_test or '"ROVEX_HOME_SHELL"' not in theme_test or '"rovex_home_motion_header"' not in theme_test:
    raise SystemExit("[Phase 1] bounded Home attachment wait/diagnostic missing")

for marker in (
    "RovexPremiumPalette.forKey",
    "contrast(roles.onSurface, roles.surfaceContainer) >= 4.5",
    "contrast(roles.onSurface, roles.surfaceElevated) >= 4.5",
    'ThemeManager.LIGHT',
    'ThemeManager.AMOLED',
    'ThemeManager.MINT',
    'ThemeManager.SUNSET',
    'ThemeManager.LAVENDER',
    'ThemeManager.PASTEL',
    "captureThemeScreenshot(context, themeName)",
):
    if marker not in theme_test:
        raise SystemExit("[Phase 1] theme/contrast/screenshot regression missing: " + marker)

# This adjacent admission test failed before the new overlay was applied because
# WorkManager can finish a one-time recovery worker before the state is sampled.
# Keep FAILED/BLOCKED/CANCELLED invalid; permit SUCCEEDED only as a valid fast completion.
if "info.state == WorkInfo.State.SUCCEEDED" not in admission:
    raise SystemExit("[Phase 1] fast-completion-safe admission test update is absent")
if "info.constraints.requiresBatteryNotLow()" not in admission or "!info.constraints.requiresDeviceIdle()" not in admission:
    raise SystemExit("[Phase 1] deletion recovery constraint assertions were weakened or removed")
if "[663] WorkManager admission observed state=" not in admission:
    raise SystemExit("[Phase 1] WorkManager runtime-state diagnostic is missing from the generated admission test")

capture = repo / ".github/ci/rovex_visual_truth_capture.sh"
if not capture.is_file():
    raise SystemExit("[Phase 1] visual-truth screenshot collector missing")
capture_text = capture.read_text(encoding="utf-8")
if "for theme in light amoled mint sunset lavender pastel; do" not in capture_text or "phase1_home_${theme}.png" not in capture_text:
    raise SystemExit("[Phase 1] visual collector does not enumerate and collect all six required theme screenshots")

print("[Phase 1] generated source verification PASS")
print("[Phase 1] version/application identity, semantic roles, geometry invariants, and contrast/screenshot tests verified")
print("[Phase 1] WorkManager fast-completion case allowed without removing durable-constraint assertions")
print("[Phase 1] real emulator screenshot collector covers Light, AMOLED, Mint, Sunset, Lavender, and Pastel compatibility themes")
