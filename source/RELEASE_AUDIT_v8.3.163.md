# Rovex v8.3.164 Audit — Diagnostics Runtime/UI Correction

## Root cause fixed
The v8.3.162 CI build reached Kotlin compilation and failed only on `RovexDiagnosticsDataCenter.kt` line 33 because `LinearLayout.LayoutParams` weight was passed as `1` (`Int`) instead of `1f` (`Float`).

## Runtime defect discovered during user device test
`BenInferenceProcessClient.oneShot()` attempted to use `remote` directly without first calling `ensureBound()`. A freshly created diagnostics client therefore could return `null` before the asynchronous isolated-process Binder connection was established. This exactly explains the user's "Diagnostic failed: no report returned" result.

## Corrections
- All one-shot IPC operations now bind through `ensureBound()` before creating/sending a request.
- EmbeddingGemma diagnostics have a 90-second client timeout and isolated-service hard deadline to accommodate first-run native initialization.
- Diagnostic progress messages are sent across Binder for visible UI status.
- Diagnostics Center shows STARTING/RUNNING/COMPLETE/FAILED/CANCELLED state, indeterminate progress, elapsed wall time, and a stop control.
- Latest completed diagnostic is persisted in the main process with a bounded five-report history; study/question text is not stored.
- Main-process telemetry mirrors the completed report after IPC returns, avoiding isolated-process memory visibility loss.
- Settings now calls the surface `Test & Diagnostics Center` and exposes direct Ben Model Lab access from that center.
- Share export includes the stored full EmbeddingGemma report plus current main-process telemetry.

## Validation
- XML parse: 26 files, 0 errors.
- Per-layout duplicate IDs: 0.
- Production scans for `runBlocking`, `GlobalScope`, `Thread.sleep`, broad `catch(Throwable)`: 0.
- Changed Kotlin source brace/quote sanity: balanced.
- Architecture audit: passes ownership/routing checks; large-file warnings remain for `QuizActivity.kt` and `BenEmbeddingGemmaEngine.kt`.
- Architecture regression audit: still warns/fails on 51 source objects vs baseline 44; not suppressed or rebaselined.
- Local Gradle Android compilation remains unavailable because `downloads.gradle.org` DNS cannot be resolved in this environment.
- Actual CI/device validation is required before claiming release-green status.
