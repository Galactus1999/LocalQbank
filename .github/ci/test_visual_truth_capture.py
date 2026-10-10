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
assert "source = source.replace(old, new, 1)" in overlay651
assert 'a.get("visible-to-user")=="false"' in overlay652
assert 'a.get("visible-to-user")!="true"' in overlay652
assert "startup recovery dialog buttons remain discoverable" in overlay652
assert "rovex_652_uiautomator_visibility_fix.py" in discover
assert "versionCode = 735" in overlay650
assert 'm["entropy"] >= 0.20' in truth
assert 'm["bright_pixel_ratio"] <= 0.995' in truth

print("PASS: visual truth requires dedicated instrumentation, real Activity screenshots, non-duplicate images, and non-empty interaction exploration.")
