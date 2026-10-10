#!/usr/bin/env python3
"""Regression guard for truthful rendered-UI screenshots and interaction exploration."""
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[2]
runner = (ROOT / ".github/ci/adaptive-instrumentation-v2.sh").read_text(encoding="utf-8")
overlay = (ROOT / ".github/ci/rovex_649_visual_truth_capture_repair.py").read_text(encoding="utf-8")
overlay650 = (ROOT / ".github/ci/rovex_650_sparse_screen_score_fix.py").read_text(encoding="utf-8")
overlay651 = (ROOT / ".github/ci/rovex_651_interaction_launch_race_fix.py").read_text(encoding="utf-8")
overlay652 = (ROOT / ".github/ci/rovex_652_uiautomator_visibility_fix.py").read_text(encoding="utf-8")
overlay653 = (ROOT / ".github/ci/rovex_653_hidden_status_inset_fix.py").read_text(encoding="utf-8")
overlay654 = (ROOT / ".github/ci/rovex_654_flashcard_startup_recovery.py").read_text(encoding="utf-8")
overlay655 = (ROOT / ".github/ci/rovex_655_home_card_motion_contrast.py").read_text(encoding="utf-8")
helper = (ROOT / ".github/ci/rovex_visual_truth_capture.sh").read_text(encoding="utf-8")
truth = (ROOT / "tools/rovex_visual_truth.py").read_text(encoding="utf-8")
discover = (ROOT / ".github/ci/adaptive-discover.sh").read_text(encoding="utf-8")

assert "RovexVisualTruthCaptureTest" in runner
assert "-e rovexVisualTruth true" in runner
assert "rovex_visual_truth_capture.sh" in runner
assert "capture_exact_activity" not in runner, "Host-side am start cannot prove non-exported Activity rendering."
assert 'capture_visual "home-production"' not in runner, "Unvalidated host screenshots must not be called visual truth."
assert "INSTRUMENTATION_CODE: -1" in runner
assert "INSTRUMENTATION_STATUS_CODE: 0" in runner
assert "Run only when this test class is explicitly selected" in overlay
assert "captureCoreRenderedScreens" in overlay
assert "Required rendered screenshot missing" in helper
assert 'InstrumentationRegistry.getArguments().getString("class")' in overlay
assert '-e class com.localqbank.library.RovexVisualTruthCaptureTest' in runner
assert "no foreground Rovex Activity with visible interactive nodes" in overlay
assert "foreground Rovex Activity" in overlay
assert "def foreground_app()" in overlay
assert "actions=%d states=%d" in overlay
assert "sha256" in truth and "byte-identical screenshots" in truth
assert "01_home.png" in truth and "05_settings.png" in truth
score = runpy.run_path(str(ROOT / "tools/rovex_visual_truth.py"))["score"]
# Regression fixture from the real Phase 649 Ren screenshot: it is visibly rendered,
# but a sparse light-theme screen has lower entropy and >98% bright pixels.
ren_first_run = {
    "width": 1080, "height": 1920, "entropy": 0.21913,
    "edge_density": 0.01015, "bright_pixel_ratio": 0.98968,
}
assert score(ren_first_run) >= 75, "A valid sparse light-theme Activity must not fail the structural-render gate."
assert "rovex_649_visual_truth_capture_repair.py" in discover
assert "rovex_650_sparse_screen_score_fix.py" in discover
assert "rovex_651_interaction_launch_race_fix.py" in discover
assert "process_started = False" in overlay651
assert "for attempt in range(30)" in overlay651
assert "did not start within 30 seconds after launcher request" in overlay651

# The captured emulator hierarchy had real clickable LATER/RESUME buttons but omitted
# visible-to-user on all nodes. The explorer must retain those nodes, while still excluding
# explicitly hidden and disabled controls.
import xml.etree.ElementTree as ET
fixture = ET.fromstring("""<hierarchy>
  <node package="com.localqbank.library" enabled="true" clickable="true" bounds="[10,10][100,60]" text="LATER"/>
  <node package="com.localqbank.library" enabled="true" clickable="true" visible-to-user="false" bounds="[10,70][100,120]" text="HIDDEN"/>
  <node package="com.localqbank.library" enabled="false" clickable="true" bounds="[10,130][100,180]" text="DISABLED"/>
  <node package="com.localqbank.library" enabled="true" clickable="true" bounds="[10,190][100,240]" text="RESUME"/>
</hierarchy>""")
fixture_visible = [
    node.attrib["text"] for node in fixture.iter()
    if node.attrib.get("enabled") == "true"
    and node.attrib.get("visible-to-user") != "false"
    and node.attrib.get("clickable") == "true"
    and node.attrib.get("bounds")
]
assert fixture_visible == ["LATER", "RESUME"], fixture_visible
assert "source = source.replace(old, new, 1)" in overlay651
assert 'a.get("visible-to-user")!="true"' in overlay652
assert "nodes() and state_key() now use the same visibility semantics" in overlay652
assert "rovex_652_uiautomator_visibility_fix.py" in discover
assert "rovex_653_hidden_status_inset_fix.py" in discover
assert "Hidden-status-bar screens must not reserve the hidden status bar's inset." in overlay653
assert "insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top" in overlay653
assert "versionCode = 737" in overlay653
assert "rovex_654_flashcard_startup_recovery.py" in discover
assert "half-built reviewer Activity" in overlay654
assert "window.decorView.post { if (!isFinishing && !isDestroyed) finish() }" in overlay654
assert "versionCode = 738" in overlay654
assert "rovex_655_home_card_motion_contrast.py" in discover
assert "ThemeManager.PASTEL -> 0.27f" in overlay655
assert "Glass, not opaque pastel" in overlay655
assert "RovexVisualSurfaceStyle.glass(c, 22f, true)" in overlay655
assert "colorRefraction" in overlay655
assert "neonRim" in overlay655
assert "versionCode = 739" in overlay655
assert 'a.get("visible-to-user") == "false"' in overlay652
assert "state_key() visibility compatibility" in overlay652
assert "versionCode = 736" in overlay652
assert "versionCode = 735" in overlay650
assert 'm["entropy"] >= 0.20' in truth
assert 'm["bright_pixel_ratio"] <= 0.995' in truth

print("PASS: visual truth requires dedicated instrumentation, real Activity screenshots, non-duplicate images, and non-empty interaction exploration.")
