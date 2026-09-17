# Rovex v8.3.176 — Ben IPC timeout/drain correction

## Objective
Correct the `IPC_TIMEOUT_150000MS` failure mode by making every pending IPC request terminal on disconnect/trim/close and by making the isolated service explicitly reject malformed/shutdown commands instead of silently dropping them.

## Root-cause validation against v8.3.175
The external diagnosis was **partially correct**. The current source did not have a literal `pendingRequests.clear()` abandonment bug: generation requests and one-shot requests were stored separately. However, `trim()` did not drain `oneShots` at all, and its generation cleanup could mark a request terminal without removing/closing its callback flow when the remote Messenger was already null. Therefore an explicit client trim/close during an in-flight one-shot could leave its caller suspended until the 150 s watchdog. This is a real lifecycle-drain defect.

The service did not have an `isModelReady` drop branch either. One-shot work was queued behind the service `singleFlight` mutex. Nevertheless, malformed commands, missing request IDs, shutdown-state messages, and missing reply Messengers had paths that could return without a terminal protocol response. v8.3.176 makes these protocol failures explicit.

## Changes
- Added `OneShotState.requestId` and a stored watchdog reference.
- Added `failAllPendingOneShots()` to atomically terminalize, remove, cancel watchdogs, report failure, and resume all affected one-shot continuations.
- `trim()` now drains both generation flows and one-shot requests before unbinding/stopping the service.
- `close()` explicitly drains one-shots as an additional lifecycle invariant.
- Binder/service-generation loss now uses the same one-shot drain helper.
- Ping decoding now surfaces service-side error replies instead of returning a false result with no failure reason.
- Isolated service has an explicit `shuttingDown` gate.
- Missing request IDs, unknown commands, missing reply Messengers, and shutdown requests now receive explicit error replies whenever a reply Messenger exists.
- Android stress test bounds a generated request to 5 seconds so lifecycle churn cannot stall the complete test suite.
- No model/runtime changes were made to the proven EmbeddingGemma Qualcomm NPU path.

## Static audit
- XML parsed: 27
- XML parse errors: 0
- Duplicate IDs within individual XML files: 0
- Production `runBlocking`: 0
- Production `GlobalScope`: 0
- Production `Thread.sleep`: 0
- Production broad `catch(Throwable)`: 0
- `BIND_NOT_FOREGROUND`: 0
- `setSilent`: 0
- `settings.gradle.kts`: exactly 1
- applicationId: `com.localqbank.library`
- versionName: `8.3.176`
- versionCode: `273`
- Architecture cohesion: PASS; existing large-file warnings retained

## Build gate
Local Android Gradle compilation was attempted but could not execute because the environment could not resolve `downloads.gradle.org`. Therefore Android/CI compilation is **not claimed green**. CI remains the authoritative build gate.
