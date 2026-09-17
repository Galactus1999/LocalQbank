# v8.3.142 source audit

- Baseline: v8.3.141 / versionCode 239.
- Target: v8.3.142 / versionCode 240.
- Native neural execution path: moved behind `BenInferenceProcessService` in `:inference_process`.
- Main-process native direct references: grounded pipeline, Settings neural tests and Ben Model Lab tests now use `BenInferenceProcessClient`.
- Application startup in inference process: explicitly skipped.
- Main-process memory trim: calls neural pipeline trim.
- Inference-process memory trim: closes generator/runtime state.
- Neural circuit: CLOSED/OPEN/HALF_OPEN.
- Rejected neural answer: deterministic fallback.
- XML changes: manifest service declaration only; no layout XML changes.
- Android compile: NOT CLAIMED until actual CI.
- Device NPU regression: REQUIRED before release; v8.3.140 remains the last device-proven Qualcomm NPU baseline.
