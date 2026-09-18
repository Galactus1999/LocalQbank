# Release Audit — v8.3.145

- Version: 8.3.145
- versionCode: 243
- applicationId: com.localqbank.library
- Baseline: v8.3.144 Ben IPC/Thermal Hardening

## Phase 2 lifecycle hardening

- Client terminal state and service/native release state are distinct.
- Generation cancellation watchdog keys off `nativeReleased`, not terminal-message delivery.
- Gemma generation, EmbeddingGemma one-shot operations, and runtime trimming share the service-level `singleFlight` barrier.
- `onTrimMemory()` cannot close a runtime concurrently with an active native operation.
- `Service.onDestroy()` does not synchronously close native runtimes while a native operation may still be unwinding; the isolated process is the final cleanup boundary.
- One-shot IPC requests have explicit request state and cancellation suppression.
- Severe thermal state denies new one-shot neural work; generation is actively cancelled by the thermal listener.
- Existing Binder death + service-generation fencing remains intact.

## Static validation

- Kotlin source files: 155
- Java source files: 0
- XML files: 28
- XML parse errors: 0
- Duplicate IDs within individual layouts: 0
- `findViewById<T>()` vs XML audit: PASS
- `runBlocking`: 0
- `GlobalScope`: 0
- `Thread.sleep`: 0
- broad `catch(Throwable)`: 0
- stale executable versionCode 242 references: 0
- stale executable versionName 8.3.144 references in CI: 0
- zstd Android AAR assertion retained
- persistent signing certificate assertions retained
- APK ARM64 zstd native-library assertions retained
- ZIP integrity: PASS

## Existing audit-script status

`tools/architecture_regression_audit.py` fails identically on the unmodified v8.3.144 baseline and v8.3.145 with:

`main source singleton/object declarations increased 46 > baseline 44`

Therefore this is a pre-existing baseline-audit failure, not introduced by Phase 2. It remains a release-gate issue that should be reconciled separately rather than falsely marked green.

`tools/audit_architecture.py` is also stale relative to the current architecture and fails on the v8.3.144 baseline before these changes.

## Android build status

NOT VERIFIED. `./gradlew :app:compileDebugKotlin --offline` attempted to resolve Gradle 9.3.1 and failed because `downloads.gradle.org` DNS resolution is unavailable. No Android compilation/CI success is claimed.

## Device validation status

Pending execution on the physical Qualcomm SM8650 device. Required scenarios remain:

- rapid A→B→C→D generation switching
- cancellation during prefill/decode
- Binder death during generation
- Binder death during EmbeddingGemma reranking
- cancellation + Binder-death race
- request replacement while old native work is stopping
- isolated-process restart/rebind
- native release latency / zombie-runtime check
- warm/cold latency
- thermal escalation/de-escalation
- UI/ANR observation
- deterministic fallback verification
