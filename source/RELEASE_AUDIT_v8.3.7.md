# Rovex v8.3.7 Release Audit

- Baseline: v8.3.6, versionCode 104
- Release: v8.3.7, versionCode 105
- applicationId: com.localqbank.library (unchanged)
- compileSdk: 37
- targetSdk: 36
- AGP: 9.1.1
- Gradle wrapper: 9.3.1
- zstd: com.github.luben:zstd-jni:1.5.7-16@aar

## Backup audit
- Lightweight automatic progress backup retained.
- Added manual full backup with qbank.db + flashcards.db, all non-transport SharedPreferences, and durable app-owned files.
- Temporary imports, backup staging, and cache are excluded.
- SQLite snapshots use VACUUM INTO rather than copying live WAL files directly.
- Manifest records path, size and SHA-256 for every payload file.
- Full restore is staged first and validated before any live state changes.
- Full restore is applied before StartupCoordinator initializes managers/databases in a fresh process.
- Existing database files are rollback-protected during replacement.
- Full restore requires manual app reopen; no process killing/restart logic was introduced.
- Google sign-in was deliberately not added: SAF can already target Google Drive and does not introduce account/authentication complexity.

## Regression checks
- XML/findViewById audit: PASS by static inspection.
- Duplicate IDs: checked per layout; cross-layout reuse remains legal.
- Unsafe SQLite PRAGMA execSQL: none detected.
- Native autosizing path: disabled.
- Automatic wrong-answer -> flashcard conversion: absent.
- Blocking Thread.sleep/runBlocking/GlobalScope: absent.
- zstd ARM64 APK assertion retained in CI.

## Compilation
Local compilation remains unverified because the environment cannot resolve services.gradle.org for Gradle 9.3.1. GitHub Actions is authoritative.
