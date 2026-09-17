# Rovex v8.3.29 — Persistent Signing & Update-Path Audit

## Scope

Focused change: make GitHub CI debug APKs use one persistent signing certificate so Rovex can update in place instead of requiring an uninstall on every CI build.

No application business logic, database schema, QBank importer, SRS behavior, Ben/Dr. Frankenstein cognition, or Flashcard functionality was intentionally changed by this release.

## Version / identity

- applicationId: `com.localqbank.library`
- versionName: `8.3.29`
- versionCode: `127`
- compileSdk: `37`
- targetSdk: `36`
- zstd dependency: `com.github.luben:zstd-jni:1.5.7-16@aar`

## Signing architecture

- CI receives a dedicated persistent keystore through GitHub Actions secrets.
- The private keystore is **not stored in source control** and is excluded by `.gitignore`.
- CI fails before compilation if any of the four required signing secrets is absent.
- Gradle uses the persistent CI signing config only when the CI signing environment is present; ordinary local debug builds retain the normal local Android debug key.
- CI validates the keystore with `keytool`, signs `assembleDebug`, and verifies the resulting APK with `apksigner`.
- CI continues to assert that ARM64 zstd JNI is packaged.

## Device migration limitation

Android requires an installed update to use the same signing identity as the installed application. Existing Rovex APKs produced by disposable GitHub-hosted debug keystores cannot be retroactively adopted by the new persistent key.

Therefore, the device currently running the old CI-signed APK requires **one final uninstall/reinstall** to migrate to the persistent signing identity. After that migration, future CI APKs signed by the persistent key can update in place, provided package ID and signing identity remain unchanged and `versionCode` increases.

## Static source audit

- `findViewById<T>()` vs XML widget type: PASS
- duplicate XML IDs within each individual layout: PASS
- broad `Throwable` catches: PASS
- unsafe SQLite PRAGMA via `execSQL`: PASS
- native adaptive text autosize path: PASS
- automatic wrong-answer → flashcard conversion: PASS
- blocking `Thread.sleep` / `runBlocking` / `GlobalScope`: PASS
- Activity window-policy coverage: PASS
- APKG Android zstd AAR: PASS
- persistent-signing CI configuration: PASS
- signing material absent from source tree: PASS

## Runtime-risk audit

The signing change does not execute application code before startup and does not alter runtime managers. The only new build-time dependency is the CI-provided keystore. APK certificate verification is performed after assembly in CI.

## Local compilation note

Local Gradle compilation is not claimed as verified because the current environment cannot resolve `services.gradle.org` to download Gradle 9.3.1. GitHub Actions remains the authoritative compilation environment.
