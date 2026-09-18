# Rovex v8.3.204 — Phase 2 IPC Stress Hardening Audit

## Baseline
- Starting source: v8.3.203, which is the v8.3.200 known-good diagnostic lineage.
- Production diagnostic path was frozen except for one evidence-driven service-state correction.

## Surgical production correction
`BenInferenceProcessService.startGeneration()` previously returned from the `singleFlight` critical section when a generation had already been cancelled/superseded before acquiring the native lock. That path removed the request without sending a terminal reply. The correction sends exactly one terminal `Cancelled` event before returning.

## Device stress coverage added
- 10 warm EmbeddingGemma compare requests with latency capture.
- Genuine overlapping A→B latest-request-wins generation test.
- Cancellation followed by a new native inference request to exercise the release barrier.
- Isolated `:inference_process` death followed by Binder rebind and fresh inference.
- Repeated trim/rebind transport cycles.

## Static audit
- Kotlin source files: 184
- XML files: 28
- XML parse errors: 0
- Duplicate `@+id` values within an individual layout: 0
- `GlobalScope`: 0
- `Thread.sleep`: 0
- production `runBlocking`: 0
- Working-tree content differs from v8.3.203 in exactly three existing files: `app/build.gradle.kts`, `BenInferenceProcessService.kt`, and `BenInferenceProcessStressTest.kt`, plus this audit/release-note documentation.
- No dependency, manifest, model, FGS, zstd, signing, SRS, backup, flashcard, theme, import, or database production files were changed.

## Build status
Android Gradle compilation was attempted but could not start because the environment could not resolve `downloads.gradle.org`. Therefore Android compilation/CI is **UNVERIFIED**, not green.

## Release identity
- versionName: 8.3.204
- versionCode: 299
