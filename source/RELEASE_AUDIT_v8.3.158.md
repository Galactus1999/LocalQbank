# Rovex v8.3.158 — Phase Completion / CI Preflight

## Changes
- Advanced from v8.3.157 to versionCode 255.
- Removed stale release-version assumptions from CI workflow in favor of deriving release identity from `app/build.gradle.kts`.
- Added an explicit `:app:compileDebugKotlin` CI gate before JVM tests.
- Preserved Phase 2 IPC hardening, Phase 3 contrastive evidence/verifier boundary, Phase 4 import hardening/performance work, Phase 5 contextual SRS shadow policy, and Phase 6 optimization policy.
- Preserved persistent signing-certificate and ARM64 zstd assertions.

## Local validation
- Pure Kotlin compile for current pure components: PASS.
- XML parse / duplicate-ID audit: PASS.
- No production `runBlocking`, `GlobalScope`, `Thread.sleep`, or `catch(Throwable)`: PASS.
- Full Android/AGP compilation: NOT VERIFIED locally; Gradle distribution host is unavailable in the current environment.
- Actual CI remains the authoritative Android build gate.

## Known audit note
The legacy architecture-regression baseline reports the pre-existing singleton/object baseline delta; it is not suppressed by this release.
