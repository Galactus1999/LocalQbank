#!/usr/bin/env python3
"""Regression guard for truthful rendered-UI screenshots and interaction exploration."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
runner = (ROOT / ".github/ci/adaptive-instrumentation-v2.sh").read_text(encoding="utf-8")
overlay = (ROOT / ".github/ci/rovex_649_visual_truth_capture_repair.py").read_text(encoding="utf-8")
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
assert "test=captureCoreRenderedScreens" in runner
assert "Required rendered screenshot missing" in helper
assert 'InstrumentationRegistry.getArguments().getString("rovexVisualTruth")' in overlay
assert "no visible interactive app hierarchy" in overlay
assert "actions=%d states=%d" in overlay
assert "sha256" in truth and "byte-identical screenshots" in truth
assert "01_home.png" in truth and "05_settings.png" in truth
assert "rovex_649_visual_truth_capture_repair.py" in discover

print("PASS: visual truth requires dedicated instrumentation, real Activity screenshots, non-duplicate images, and non-empty interaction exploration.")
