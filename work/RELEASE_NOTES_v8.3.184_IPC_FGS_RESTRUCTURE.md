# Rovex v8.3.184 — Ben IPC FGS Architecture Restructure

## Root architectural correction

v8.3.183 still hosted the Android foreground service inside `:inference_process`. Repeated device failures persisted as a black-screen/return-to-Ben-Model-Lab symptom during the isolated IPC diagnostic.

v8.3.184 removes foreground-service ownership from the neural worker process entirely.

### New ownership model

- `BenInferenceForegroundService` runs in the **main application process** and owns all Android FGS admission, notification, and FGS lifecycle state.
- `BenInferenceProcessService` remains the disposable secondary `:inference_process` worker and is now a **normal bound service**, not an FGS host.
- `BenInferenceProcessClient` acquires a main-process FGS lease before real neural work, waits for an explicit ready acknowledgement, then binds the worker.
- Multiple client instances are supported through lease IDs; the FGS stops only after all leases are released plus a short idle grace period.
- The worker uses a bounded partial wake lock during active native work but never calls `startForeground()`/`stopForeground()`.
- The existing Binder generation fencing, single-flight/latest-request-wins, cancellation/release barrier, watchdogs, and deterministic failure paths remain intact.

## Why this is a structural fix

Android's documented FGS contract is attached to the service that is started with `startForegroundService()` and must be promoted promptly. The previous design made the neural worker itself responsible for that OS lifecycle while it also owned native model execution and could be deliberately terminated on a hard timeout. That unnecessarily coupled Android FGS admission failures with native worker lifetime.

The new design separates platform lifecycle governance from native inference execution.

## Compatibility / safety

- Android 14+ `specialUse` remains declared only for the main-process FGS.
- `FOREGROUND_SERVICE_SPECIAL_USE` remains required and declared.
- The worker remains non-exported and private to the application.
- No study-content policy or deterministic retrieval ownership was changed.
- No model/runtime backend was changed.
- No speculative decoding, model replacement, or unrelated architectural refactor was introduced.

## Validation

- 27 XML files parsed successfully.
- Duplicate IDs within individual XML files: 0.
- `GlobalScope`: 0.
- `runBlocking`: 0.
- `Thread.sleep`: 0.
- `BIND_NOT_FOREGROUND`: 0.
- `setSilent`: 0.
- Broad `catch(Throwable)`: 0.
- Changed Kotlin source delimiter/structural audit: PASS.
- Android Gradle build remains unverified because this environment cannot resolve `downloads.gradle.org` to obtain Gradle 9.3.1. CI is the build gate.

## Version

- Version name: 8.3.184
- Version code: 281
