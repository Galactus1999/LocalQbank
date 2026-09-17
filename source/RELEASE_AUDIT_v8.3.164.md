# Rovex v8.3.164 — Diagnostics Failure-Visibility Correction

## User-observed failure
The device Diagnostics Center displayed `FAILED • no report returned`, while the exported main-process telemetry remained at `stage=IDLE`, `totalRequests=0`, and no stored report. This showed that the client was collapsing multiple IPC failure modes into a nullable result and that isolated-process telemetry was not sufficient to explain Binder/runtime failure from the main process.

## Corrections
- `BenInferenceProcessClient.diagnosticSuspend()` now returns `DiagnosticResult(report, failure)` rather than an ambiguous nullable String.
- Explicit failure reasons are surfaced for Binder bind failure, IPC send failure, 90-second client timeout, isolated inference-process death, remote diagnostic failure, and empty report.
- `RovexDiagnosticsDataCenter` displays the exact failure reason instead of `no report returned`.
- Successful reports continue to be durably stored and mirrored into main-process diagnostic telemetry.
- No prompts, questions, or study content are added to diagnostics.

## Important interpretation
This correction does not assume the underlying EmbeddingGemma native runtime is healthy. If the isolated process dies during model/runtime initialization, v8.3.164 should now report `INFERENCE_PROCESS_DIED` instead of hiding the cause behind `no report returned`. That distinction is required before changing the model/runtime implementation.

## Validation
- XML parse: 27/27, 0 parse errors.
- Duplicate IDs within individual layouts: 0.
- Production `runBlocking`/`GlobalScope`/`Thread.sleep`: 0.
- Production `catch(Throwable)`: 0.
- Stale v8.3.163/versionCode 260 source references: 0 after version bump.
- Architecture audit: PASS with existing large-file warnings.
- Architecture regression audit: FAIL — 51 singleton/object declarations vs baseline 44. This is intentionally not suppressed or rebaselined.
- Full Android Gradle compilation: not claimed; current environment cannot reliably download the configured Gradle distribution due external DNS restrictions. CI remains the build gate.

## Next device evidence required
Run the Full EmbeddingGemma Contract + NPU Test once on v8.3.164. If it fails, share the exact failure reason/report. If the isolated process dies, collect the Android/CI runtime log as well; do not replace the failure with speculative model/runtime changes.
