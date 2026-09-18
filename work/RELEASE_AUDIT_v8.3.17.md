# Rovex v8.3.17 Release Audit

## Final UI change
- Ren display name changed to Frankenstein in the user-facing assistant surfaces.
- Frankenstein entry card is left aligned within its box.
- Existing configurable colour-flow implementation is retained unchanged.
- Sequential letter-jump animation remains 1800 ms and now progresses strictly left-to-right, one letter at a time, then restarts.
- Internal class/package/asset identifiers remain unchanged for compatibility.

## Provenance
- Versioned provenance certificate regenerated for v8.3.17 / versionCode 115.
- Provenance payload is encrypted and signed; owner identity is not stored as readable plaintext in the application source.
- Private signing key and AES key remain outside the source tree.

## Stability checks
- XML parsing and layout structure checked.
- Duplicate IDs checked within each individual XML layout file only.
- Typed findViewById references checked against actual XML widget classes.
- No Thread.sleep, runBlocking, GlobalScope, or killProcess patterns in Kotlin application source.
- No unsafe SQLite PRAGMA execSQL pattern.
- Existing backup/restore, APKG importer, Zstd Android AAR, and splash/header animation code retained.
- No new background service or database work introduced by the Frankenstein UI rename.
- Source contains no plaintext owner name/email under app/src/main.
- Provenance certificate Ed25519 signature verified.

## Build configuration
- applicationId: com.localqbank.library
- compileSdk: 37
- targetSdk: 36
- versionName: 8.3.17
- versionCode: 115
- zstd-jni: com.github.luben:zstd-jni:1.5.7-16@aar
- Gradle: 9.3.1
- AGP: 9.1.1

## Verification limitation
The source/static audit is complete. Local APK compilation is not claimed because the current environment cannot resolve/download the Gradle 9.3.1 distribution. GitHub CI remains the authoritative compile verification step.
