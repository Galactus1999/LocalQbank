# Rovex v8.3.179 — IPC Watchdog + FGS Admission Hardening

## Root-cause direction
The device failure `IPC_TIMEOUT_150000MS_STAGE=Remote_diagnostic_accepted_by_isolated_Ben_process` is consistent with a post-acceptance wedge. v8.3.178 moved the watchdog before foreground admission, but it still depended on coroutine scheduling and could not independently prove that the service's FGS admission path was progressing.

## Corrections
- Added a dedicated `ScheduledExecutorService` watchdog independent of `Dispatchers.IO`, native inference, and `singleFlight`.
- Watchdog is registered before foreground admission and before the diagnostic worker coroutine is dispatched.
- Diagnostic stages now explicitly progress through `accepted -> fgs_admission -> foreground_ready -> admission -> engine/complete`.
- Added bounded terminal-delivery grace before killing the disposable inference process.
- `startForeground()` is explicitly marshalled to the Service main looper when called from worker code and bounded by a 2-second admission wait; a wedged main looper becomes an explicit `IPC_FGS_PROMOTION_FAILED`/watchdog stage instead of an unbounded wait.
- Fixed the known Kotlin compilation issue from the previous CI baseline: `CancellableContinuation.isActive` was changed to `continuation.context.isActive`.
- Version bumped to 8.3.179 / versionCode 276.

## Preserved
- Messenger IPC transport and Binder death/rebind generation logic.
- Proven direct EmbeddingGemma/QNN/HTP runtime.
- Single-flight and deterministic fallback architecture.
- FGS `specialUse` declaration and notification.
- Zstandard Android AAR and all existing corrected Rovex features.

## Build status
Local Android compilation is **not claimed** because Gradle 9.3.1 cannot be downloaded in the current environment (`downloads.gradle.org` DNS unavailable). The source was additionally checked with local Kotlin parser/compiler tooling; Android/platform dependency errors are expected without the Android SDK classpath, and no syntax-level `expecting`/parser errors were found.

CI remains the authoritative Android compile gate.
