# Rovex v8.3.198 — Ben Diagnostic State Reconciliation Compile Fix

## Purpose
Correct the v8.3.197 Kotlin compilation failure reported by CI and preserve the diagnostic-state reconciliation architecture.

## CI failure fixed
CI v8.3.197 failed at:
`BenIpcDiagnosticRecorder.kt:140:81 Unresolved reference 'trace'`

The stale-terminal recovery branch referenced a nonexistent local variable `trace`. The correct durable journal variable in that scope is `journalText`.

Changed:
`resolvedKind(app, trace)` → `resolvedKind(app, journalText)`

No model, NPU, QNN, LiteRT, FGS, Binder, or business-logic behavior was changed.

## Additional verification
- 28 XML resources parsed successfully.
- Duplicate IDs within individual XML files: 0.
- Production GlobalScope: 0.
- Production Thread.sleep: 0.
- Production broad catch(Throwable): 0.
- BIND_NOT_FOREGROUND: 0.
- setSilent: 0.
- diagnosticRuntimeHold: 0.
- Test-only runBlocking occurrences remain confined to existing androidTest sources.
- Version bumped to 8.3.198 / versionCode 293.

## Build status
A local Gradle compile could not be completed because this environment cannot resolve `downloads.gradle.org`. CI remains the authoritative compilation gate. This release is therefore **not claimed CI-green** until the new CI run passes.
