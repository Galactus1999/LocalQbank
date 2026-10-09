#!/usr/bin/env python3
"""Regression guard: emulator functional instrumentation must not execute Macrobenchmark."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
runner = root / ".github/ci/adaptive-instrumentation-v2.sh"
text = runner.read_text(encoding="utf-8")

assert "Macrobenchmark is intentionally NOT run on this emulator lane." in text, (
    "The emulator lane must explicitly explain why Macrobenchmark is not run."
)
assert "status=BLOCKED / NOT RUN" in text, (
    "The unsupported benchmark lane must be explicitly reported as BLOCKED / NOT RUN."
)
assert "suite=benchmark:connectedAndroidTest" in text, (
    "The report must identify the preserved benchmark suite."
)
assert not re.search(
    r"gradlew[^\n]*:benchmark:connectedAndroidTest",
    text,
), "The emulator instrumentation runner must not execute the Macrobenchmark task."

print("PASS: Macrobenchmark remains identified but is not run on the emulator instrumentation lane.")
