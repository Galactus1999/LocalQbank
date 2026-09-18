# Rovex v8.3.33 Release Audit

## Baseline
- Baseline source: `Rovex_v8.3.32_RequestedCorrections_Audited.zip`
- Baseline SHA-256: `0e5f25cbf742dd149c8537f8a29045a687e4baa625605a7e51062fe0e2ac1038`
- Update is in-place; applicationId unchanged.

## v8.3.33 correction
- Hardened Notes attachment integrity in `QBankDb`.
- Clearing note text now preserves the Notes record when an attached image exists, using the existing `[Image note]` representation so the image remains visible and associated.
- Deleting a note now removes both the `question_note` row and its `question_note_image` rows transactionally, then removes the private image files.
- No new progress, SRS, flashcard, or parallel business-logic system was introduced.

## Build identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.33`
- versionCode: `131`
- compileSdk: `37`
- targetSdk: `36`
- JDK: `17`
- Gradle: `9.3.1`
- zstd dependency: `com.github.luben:zstd-jni:1.5.7-16@aar`

## Static audit
- XML parsing: PASS
- Duplicate IDs within individual XML layout: PASS
- findViewById/XML widget compatibility: PASS
- Broad `Throwable` catches: PASS
- `Thread.sleep` / `runBlocking` / `GlobalScope`: PASS
- `killProcess`: PASS
- Native Android text autosizing regression: PASS
- Unsafe `execSQL("PRAGMA ...")`: PASS
- Automatic wrong-answer -> flashcard regression: PASS
- applicationId/version identity: PASS
- zstd Android AAR dependency: PASS
- Notes image relationship integrity: PASS
- CI version/signing/zstd assertions updated for v8.3.33: PASS

## Runtime-risk/source review
- Reviewed Activity startup paths and touched Notes/database code.
- Notes database writes are transactional in the modified paths.
- No new UI-thread blocking primitive introduced.
- Existing authoritative managers/engines were preserved.
- Existing flashcard controls, continuous counting, import UI, theme system, header bird and routing were not replaced or duplicated.

## Compilation
Local compilation was attempted with the project's Gradle wrapper. It could not start because this environment cannot resolve `services.gradle.org` (DNS failure). **No APK compilation is claimed.** GitHub Actions remains authoritative.

## Signing
CI retains persistent Rovex signing enforcement and checks the **actual APK certificate fingerprint** against:
`8D:EC:BE:AC:90:6A:40:54:6B:33:2F:35:62:93:13:EF:6B:A6:86:0D:10:06:AA:D0:7B:76:15:38:22:8C:01:53`

The private signing key is not present in the source tree.

## Provenance
The v8.3.32 Ed25519 provenance certificate remains historical. A new v8.3.33 provenance signature cannot be legitimately regenerated in this environment because the private provenance signing key is intentionally outside the source tree and is not available to this session. No forged or reused signature is presented as a v8.3.33 signature.

**Release status: source-audited candidate; APK compilation and new provenance signature require the secure CI/release environment.**
