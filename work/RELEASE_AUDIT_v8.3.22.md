# Rovex v8.3.23 Build-Fix Audit

## Build failure investigated
GitHub CI v8.3.21 failed during `:app:compileDebugKotlin` with:
- `HtmlImportActivity.kt:431,459,639`: unresolved reference `detectProvider`
- `SubjectFocusSearchEngine.kt:118`: unsupported escape sequence

## Corrections
- Restored a local `detectProvider(List<ImportedTest>)` implementation in `HtmlImportActivity` with conservative provider recognition and `Imported` fallback.
- Corrected the Kotlin whitespace regex to `Regex("\\s+")`.
- Bumped version from 8.3.21/versionCode 119 to 8.3.23/versionCode 121.
- Updated CI version/artifact assertions.
- Regenerated and verified the Ed25519-signed/encrypted provenance certificate and build anchor.

## Static stability checks
- XML parsing: PASS
- Per-layout duplicate IDs: PASS
- Dangerous blocking/runtime patterns: PASS
- Broad `catch(Throwable)` / `catch(Error)`: PASS
- Unsafe SQLite PRAGMA: PASS
- No stale 8.3.21/versionCode 119 references in active app/CI configuration: PASS
- Provenance signature: PASS
- ZIP integrity: PASS

## Compilation limitation
Local Gradle compilation was attempted but Gradle 9.3.1 could not be downloaded because `services.gradle.org` DNS resolution is unavailable in the current environment. No local APK compilation claim is made. GitHub CI remains the authoritative compile check.
