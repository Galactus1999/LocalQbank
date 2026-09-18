# Rovex v8.3.162 — Diagnostics CI Correction

- Corrected the CI compiler error in `RovexDiagnosticsDataCenter.kt`: the `LinearLayout.LayoutParams` weight argument is explicitly `1f` (`Float`).
- Preserved the permanent Rovex Test & Diagnostics Center and its full EmbeddingGemma Contract + NPU test entry.
- Removed the redundant second Diagnostics Center launcher from the Adaptive Engines neural-lab card; the dedicated Diagnostics page remains the canonical entry point.
- Bumped version to 8.3.162 / versionCode 259.
- No model/runtime architecture changes.

Validation status: source/static validation performed locally; CI Android compilation must still be run externally and is not claimed green until it passes.
