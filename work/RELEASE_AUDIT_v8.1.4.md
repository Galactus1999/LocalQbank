# Rovex v8.1.4 — CI Failure / Stability Correction

## Trigger
The supplied CI log for v8.1.3 failed at `:app:compileDebugKotlin`. The failure was a source-level regression, not an Android runtime crash.

## Confirmed compile errors

### 1. AdaptiveTypographyManager.kt
`setAutoSizeTextTypeUniformWithConfiguration()` was called with `Float` SP values even though this Android API overload requires integer pixel sizes.

**Correction:** the already-computed, pixel-validated `minPx` / `maxPx` values are now passed with `TypedValue.COMPLEX_UNIT_PX`. The existing `maxPx > minPx` guard remains in place to prevent the earlier runtime autosize crash.

### 2. FlashcardActivity.kt
The SRS SAVE button's nested `forEachIndexed` lambda was missing one closing brace. This caused the Kotlin parser to treat subsequent functions as local functions and generated the cascade of `unresolved reference`, `private is not applicable to local function`, and `Missing '}'` errors.

**Correction:** restored the missing closing brace. Brace balance is now zero for this source file.

## Previous crash classes rechecked
- QBank SQLite foreign-key setup uses `setForeignKeyConstraintsEnabled(true)`, not `execSQL(PRAGMA foreign_keys=ON)`.
- WAL uses `enableWriteAheadLogging()`, not `execSQL(PRAGMA journal_mode=WAL)`.
- `ProgressStore` numeric legacy reads use `PrefsCompat` for Float/Long migration.
- Flashcard database PRAGMA calls are read through `rawQuery`, which is appropriate for result-returning PRAGMAs.
- Adaptive typography retains the pixel-level min/max guard against Android's `Maximum auto-size text size ... <= minimum` crash.
- The previous `appLogoText` ImageView/TextView type mismatch is not present; the current XML/source use `RovexWaveLogoView`.

## Release metadata
- versionName: `8.1.4`
- versionCode: `82`
- Gradle wrapper: `8.11.1`
- CI setup-gradle aligned to `8.11.1` to match the wrapper.

## Verification
- ZIP integrity verified after packaging.
- Local Gradle compilation could not be executed because the environment has no cached Gradle 8.11.1 distribution and DNS access to `services.gradle.org` is unavailable.
- The supplied CI log is the authoritative compile failure evidence; the two reported source errors have been corrected in this release candidate.

**Do not label v8.1.4 stable until CI `assembleDebug` passes and the resulting APK is smoke-tested on-device.**
