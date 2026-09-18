# Rovex v8.3.187 — Ben IPC Diagnostic Compile Correction

## Purpose
Corrects the v8.3.186 CI Kotlin compilation failure in `BenInferenceProcessService.kt`.

## CI failure addressed
The v8.3.186 CI log reported:
- line 469: `Argument type mismatch: actual type is 'CoroutineScope', but 'Context' was expected`
- line 475: same error.

Cause: inside a coroutine lambda, Kotlin `this` resolved to the coroutine scope rather than the Android Service context when calling `BenIpcDiagnosticRecorder.event(...)`.

Correction:
- use `this@BenInferenceProcessService` for the three affected diagnostic recorder calls.

## Version
- versionName: 8.3.187
- versionCode: 284

## Validation
- XML parse: PASS (0 errors)
- duplicate IDs within individual XML layouts: 0
- GlobalScope: 0
- runBlocking: 0
- Thread.sleep: 0
- BIND_NOT_FOREGROUND: 0
- setSilent: 0
- broad catch(Throwable): 0
- FGS declarations: 1
- true android:isolatedProcess declarations: 0
- ZIP integrity: to be verified after packaging

## Build gate
Local Gradle compilation could not execute because `downloads.gradle.org` DNS resolution is unavailable in the current environment. CI remains the authoritative compile gate. Do not claim green until CI succeeds.
