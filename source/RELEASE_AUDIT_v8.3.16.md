# Rovex v8.3.17 Release Audit

## Final visual pass
- Launcher icon: smooth centered R, no pixel-grid logo artwork.
- Main header: complete Rovex wordmark with one-time cursive writing reveal.
- Header energy effect: sparse red/gold electrical arcs at intervals; lifecycle-cancelled.
- Launcher-only splash: dark procedural particle/wave field inspired by the supplied reference video, with coloured ink flow from the centre and a gold cursive Rovex reveal.
- Splash duration: 2.1 seconds maximum; no video, bitmap sequence, service, worker thread, or external font dependency.

## Stability checks
- XML resources parse successfully.
- Per-layout duplicate ID policy retained.
- findViewById type audit retained.
- No Thread.sleep/runBlocking/GlobalScope/killProcess patterns.
- No unsafe PRAGMA execSQL pattern.
- Full backup/restore and zstd Android AAR retained.
- Provenance anchor contains no plaintext owner identity.
- CI version assertions updated to versionCode 114 / versionName 8.3.16.

Local APK compilation is not claimed in this environment because Gradle 9.3.1 distribution download is network-blocked; CI remains the authoritative build verification.
