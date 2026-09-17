# Rovex v8.3.191 — Release Audit

## Scope
Focused correction of the real isolated EmbeddingGemma diagnostic lifecycle. The v8.3.189 Binder/FGS transport path and the EmbeddingGemma engine/runtime path were intentionally not rewritten.

## Corrected
- Real isolated EmbeddingGemma diagnostic is now owned by `BenIsolatedEmbeddingDiagnosticCoordinator`, instantiated once by `LocalQBankApplication`.
- The diagnostics Activity no longer owns the long-running remote model test in `lifecycleScope`.
- Activity recreation/navigation can no longer cancel the diagnostic merely because its UI lifecycle ends.
- The Activity polls durable journal state only for presentation.
- Explicit STOP remains user-controlled and cancels the coordinator request.
- Diagnostic journal records a kind (`IPC` vs `EMBEDDINGGEMMA`) so the real model test is not mislabeled as transport-only.
- EmbeddingGemma report header/model requirements are corrected accordingly.
- Coordinator startup is exception-safe.
- Manager contract registry documents the new architectural coordinator boundary.

## Static checks
- XML files parsed: 27
- XML parse errors: 0
- Duplicate IDs per layout: 0
- `GlobalScope`: 0
- `runBlocking`: 0
- `Thread.sleep`: 0
- `BIND_NOT_FOREGROUND`: 0
- `setSilent`: 0
- Changed Kotlin delimiter balance: PASS
- Architecture cohesion audit: PASS
- Architecture regression audit: FAIL on pre-existing singleton/object count: 52 vs historical baseline 44. This count was already 52 in the supplied v8.3.190 source and was not increased by this correction.

## Build gate
Android Gradle compilation was attempted but could not execute because the environment could not resolve `downloads.gradle.org` while downloading Gradle 9.3.1. Therefore this source is **not claimed CI-green**. CI remains the authoritative Android build gate.

## Device test status
The supplied v8.3.190 device report established that the real EmbeddingGemma path reached LiteRT, Qualcomm runtime/NPU attachment, tensor contract and the first semantic smoke-test inference, then received `JobCancellationException`. v8.3.191 specifically removes Activity lifecycle ownership as the cancellation vector. A new device run is required to verify the correction.
