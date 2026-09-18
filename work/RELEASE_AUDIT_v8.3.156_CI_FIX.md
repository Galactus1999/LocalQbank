# Rovex v8.3.156 — CI compiler correction audit

## Baseline
v8.3.155 / versionCode 252

## CI failure analyzed
The uploaded CI run reached `:app:compileDebugKotlin` and failed only in `BenEmbeddingGemmaEngine.kt` because the Phase-2B extraction introduced references to `artifactInspector` and `appVersion()` without retaining their owner members/functions in the facade.

Reported compiler diagnostics:
- unresolved reference `artifactInspector` at lines 161, 166, 167, 168, 204, 212, 321, 333, 637
- unresolved reference `appVersion` at lines 210 and 319
- dependent lambda type-inference errors at lines 166, 167, 168, 637

## Correction
Restored the facade-owned dependencies:
- `private val artifactInspector = BenEmbeddingGemmaArtifactInspector()`
- `private fun appVersion(): String = ...`

The extraction remains architecturally intact: the artifact inspector owns hashing/graph/device/memory inspection; the engine remains the inference facade.

## Version
- versionName: `8.3.156`
- versionCode: `253`

All executable CI/source identity assertions were updated from 8.3.155/252 to 8.3.156/253.

## Static preflight
- XML files: 27
- XML parse errors: 0
- duplicate IDs within individual layouts: 0
- production `runBlocking`: 0
- production `GlobalScope`: 0
- production `Thread.sleep`: 0
- production `catch(Throwable)`: 0
- old 8.3.155/252 executable assertions: 0
- IPC stress test present: yes
- ZIP integrity: pass

## Build status
Pure/static preflight does not substitute for Android/AGP compilation. The next CI run is required to establish Android compilation status.
