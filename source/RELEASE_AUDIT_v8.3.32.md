# Rovex v8.3.32 Release Audit

## Scope
Focused stability/UI correction release. Existing QBank, SRS, importer, Ben/Dr. Frankenstein and authoritative manager architecture preserved.

## Requested corrections implemented
1. Main QBank names now use theme-safe dark/midnight pastel surfaces and readable theme text.
2. Flashcard Mark/Bookmark controls use the pastel theme palette and are independently draggable anywhere within the flashcard study surface; normalized positions persist per deck/mode.
3. Flashcard study ordering for a deck follows the stored global card position, while counts come from actual SQLite cardinality; no 100-card bucket is used.
4. Flashcard import/status text uses theme-aware rich spans for heading, progress, warning/error and checkpoint information.
5. In-progress sub-QBanks are visually highlighted with a theme-adaptive yellow/pastel treatment and explicit stroke.
6. Header lightning overlay removed. Replaced with `RovexHeaderBirdView`: visible in light/dark themes, flies across the header, perches on the wordmark, and includes a brief cartoon shot/fall cycle. Decorative only and non-interactive.
7. Splash Rovex wordmark now uses the flowing multicolour spectrum rather than a fixed blue tint.
8. Home Today Solved / Study Tools cards reduced in height on normal and compact layouts.
9. Notes -> Go to Question now passes the owning `testId` and exact position from `NoteRecord` for deterministic routing.
10. Whole-project source/runtime audit repeated after modifications.

## Persistent signing
CI now requires the exact persistent Rovex signing certificate fingerprint before accepting the APK:
`8D:EC:BE:AC:90:6A:40:54:6B:33:2F:35:62:93:13:EF:6B:A6:86:0D:10:06:AA:D0:7B:76:15:38:22:8C:01:53`

## Build identity
- applicationId: `com.localqbank.library`
- versionName: `8.3.32`
- versionCode: `130`
- compileSdk: 37
- targetSdk: 36
- zstd dependency: `com.github.luben:zstd-jni:1.5.7-16@aar`

## Static checks
- XML parse: PASS
- Duplicate IDs within each layout: PASS
- findViewById/XML widget compatibility audit: PASS
- Broad Throwable catch audit: PASS
- Unsafe PRAGMA audit: PASS
- Native autosize regression audit: PASS
- Thread.sleep/runBlocking/GlobalScope audit: PASS
- killProcess audit: PASS
- Removed lightning references: PASS
- Bird class/layout wiring: PASS
- CI YAML parse: PASS
- Persistent certificate fingerprint assertion present: PASS
- ARM64 zstd assertion present: PASS
- Provenance Ed25519 signature: VALID

## Compilation limitation
Local Gradle compilation was attempted but this environment cannot resolve `services.gradle.org` (DNS failure). No local APK compilation is claimed. GitHub Actions is the authoritative compiler/build environment.
