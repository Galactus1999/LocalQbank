# Rovex v8.3.14 — CI-Corrected Stability Release Audit

## Release identity
- Version: 8.3.14
- versionCode: 112
- applicationId: com.localqbank.library
- compileSdk: 37
- targetSdk: 36
- Gradle: 9.3.1

## CI failure addressed from v8.3.13
The v8.3.13 CI log reached `:app:compileDebugKotlin` and reported compile errors in `BackupManager.kt` and `RovexWaveLogoView.kt`.

Corrections:
- Removed invalid `File(app.filesDir)` constructions where `app.filesDir` is already a `File`.
- Corrected wave-logo trigonometric results from `Double` to `Float` before constructing Android `RectF` values.
- Regenerated the versioned provenance certificate and encrypted anchor for v8.3.14 / versionCode 112.

## Static/runtime-risk audit
- Kotlin source count: 54
- XML resource count: 26
- No unsafe `PRAGMA foreign_keys=ON` execSQL pattern.
- Native text autosizing remains disabled.
- No `Thread.sleep`, `runBlocking`, `GlobalScope`, or process-kill patterns in app source.
- Full backup/restore paths retain staged restore, checksum validation, database rollback, preferences rollback, and durable-file rollback.
- Progress-backup and full-backup actions run off the Activity UI thread.
- Full restore remains applied before normal startup initialization.
- Provenance owner identity is not stored as readable plaintext in the Kotlin anchor; the payload is encrypted and signed.
- The provenance private signing key and AES key remain outside the source tree.
- zstd dependency remains the Android AAR `com.github.luben:zstd-jni:1.5.7-16@aar`.
- Package/application ID remains unchanged.

## Verification limitation
The source has been corrected against the actual v8.3.13 CI compiler errors. A fresh CI build is still required as the authoritative APK compilation verification; local environment compilation is not used as proof because the required Gradle distribution may not be available locally.
