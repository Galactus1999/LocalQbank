# Rovex v8.3.221 — CI Compile Correction Audit

## Baseline
- Source: v8.3.220 compile-correction source
- Version name: 8.3.221
- Version code: 316
- Package: `com.localqbank.library`

## CI failure analyzed
The supplied CI log `logs_95055176291.zip` failed at `:app:compileDebugKotlin`.

### Root causes
1. `BenGeminiCoordinator.kt`
   - `AppManagers.ren.searchQuestionIds(...)` referenced a nonexistent `AppManagers.ren` property.
   - The authoritative manager is `AppManagers.renCognitive` (`RenCognitiveEngine`).
   - The PYQ row string was malformed: a literal multiline Kotlin string was created without escaped `\n`, producing parser errors and cascading unresolved-token errors (`QUESTION`, `KEY`).
2. `SettingsScreen.kt`
   - `rounded(...)` accepts an `Int` radius, but the runtime telemetry card passed `16f` (`Float`).
   - `telemetry.lastElapsedMs` is `Long`, so `coerceIn(0,100)` used `Int` bounds and failed type checking.

## Corrections implemented
- Replaced `AppManagers.ren.searchQuestionIds` with `AppManagers.renCognitive.searchQuestionIds`.
- Corrected the PYQ row string to use escaped newline sequences.
- Changed telemetry card radius `16f` → `16`.
- Changed telemetry clamp to `coerceIn(0L, 100L)`.
- Bumped version to 8.3.221 / versionCode 316.

## Static validation
- `tools/ruthless_audit.sh`: PASS
- XML parsing: PASS
- Per-layout duplicate-ID audit: PASS
- Forbidden concurrency/runtime patterns checked by project audit: PASS
- Stale v8.3.220 version references in active source/build files: none found
- Persistent signing certificate validation remains present in CI.

## Compilation status
A local `./gradlew :app:compileDebugKotlin` attempt could not reach compilation because this environment cannot resolve `downloads.gradle.org` while downloading Gradle 9.3.1 (`curl: (6) Could not resolve host: downloads.gradle.org`).

Therefore **v8.3.221 is not claimed CI-green**. The authoritative next gate is the project's actual CI run.
