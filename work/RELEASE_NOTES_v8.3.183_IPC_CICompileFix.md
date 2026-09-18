# Rovex v8.3.183 — IPC FGS admission + CI compile hardening

Based on v8.3.182 IPC Deep Hardening.

## CI failure corrected
GitHub Actions failed at `:app:compileDebugKotlin` in `BenInferenceProcessClient.kt:517:51` because `awaitForegroundReady()` referenced its `timeout` local before declaration.

The timeout runnable is now declared before the Binder reply handler.

## FGS admission hardening
- `awaitForegroundReady()` now has exactly-once completion fencing across reply, timeout, cancellation, and send-failure paths.
- The isolated service re-attempts FGS promotion through its main-thread admission bridge if work arrives while the promotion flag is unexpectedly false.
- Foreground promotion failures are classified as explicit IPC diagnostic codes, including background-start rejection, missing/invalid type, security failure, and did-not-start-in-time.
- Multiple concurrent foreground-operation accounting no longer leaves a stale operation count if admission fails.

## Preserved
- Explicit FGS admission handshake.
- Android 14+ `specialUse` FGS type and manifest permission.
- One-shot retry policy.
- SharedMemory bulk UTF-8 transport above 32 KiB.
- 50 ms / 8 KiB token coalescing.
- Existing lifecycle/generation fencing and single-flight native execution.
- Existing AppManagers/engine ownership boundaries.

## Build status
The uploaded CI run for v8.3.182 failed only at Kotlin compilation due to the local `timeout` declaration error. A local Gradle build attempt cannot run because `downloads.gradle.org` is DNS-unreachable in the current environment. CI remains the authoritative build gate.
