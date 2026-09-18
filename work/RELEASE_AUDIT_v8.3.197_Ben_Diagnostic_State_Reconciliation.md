# Rovex v8.3.197 Release Audit

## Source baseline
- Starting source: corrected v8.3.196 forensic diagnostic coverage baseline.
- Version: `8.3.197`
- Version code: `292`

## Targeted source review
Changed production files:
- `BenInferenceProcessClient.kt`
- `BenIpcDiagnosticRecorder.kt`
- `BenIsolatedEmbeddingDiagnosticCoordinator.kt`
- `RovexDiagnosticsDataCenter.kt`
- `RovexDiagnosticsStore.kt`
- `app/build.gradle.kts`

## Static checks performed
- XML resources parsed successfully: 26 files, 0 parse errors.
- Duplicate IDs within individual XML files: 0.
- Production `GlobalScope`: 0.
- Production `Thread.sleep`: 0.
- Production `runBlocking`: 0.
- Production `BIND_NOT_FOREGROUND`: 0.
- Production `setSilent`: 0.
- Production broad `catch(Throwable)`: 0.
- Changed Kotlin files: delimiter/structural balance pass after stripping comments/strings.
- Old `versionCode = 291` references: 0 in source tree.
- Old `versionName = "8.3.196"` references: 0 in source tree.
- `diagnosticRuntimeHold`: 0 in production source; the only remaining occurrence is historical audit text documenting that it is absent.
- Source ZIP integrity checked after packaging.

## Build status
Local Android compilation remains **UNVERIFIED**. The repository Gradle wrapper requires Gradle 9.3.1 and the current environment cannot resolve `downloads.gradle.org` (`curl: Could not resolve host`). Therefore this release must not be described as compile-green until CI completes successfully.

## Architectural review
The correction preserves the existing architecture:
- `BenInferenceProcessClient` remains IPC transport/client authority.
- `BenInferenceProcessService` remains isolated worker authority.
- `BenIpcDiagnosticRecorder` remains the durable forensic journal.
- `RovexDiagnosticsStore` remains persistent diagnostic state storage.
- `BenIsolatedEmbeddingDiagnosticCoordinator` remains process-lifetime diagnostic ownership.
- `RovexDiagnosticsDataCenter` remains presentation/export only.

No parallel model/business-logic owner was introduced.

## External research basis
Android documents that bound services have distinct active and lifetime phases and that `onDestroy()` is the cleanup/death callback for a service; Android also documents that a process can be killed by the system and that `onDestroy()` is not guaranteed for process death. The implementation therefore treats durable event evidence as more reliable than a volatile top-level status and explicitly distinguishes worker reply from client acknowledgement.

Current LiteRT Qualcomm documentation also supports separate AOT/JIT and native QNN reproduction paths for future host-level investigation. Those paths are intentionally not invoked by this release because the supplied device trace already proves the NPU execution path for this test.
