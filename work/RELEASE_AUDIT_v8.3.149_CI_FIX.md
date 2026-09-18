# Rovex v8.3.149 — CI Compiler Correction Audit

## Baseline
- v8.3.148 CI reached Kotlin compilation and failed only in `ApkgImporter.kt` at the newly added bounded media-manifest reader call sites.
- Root cause: the caller-side security hardening was present, but its helper methods/constants were absent from the source artifact.

## Correction
Added:
- `MAX_MEDIA_MANIFEST_BYTES = 16 MiB`
- `readBytesLimited(InputStream, maxBytes)` with streaming 64 KiB chunks and hard byte cap
- `readUtf8Limited(InputStream, maxBytes)` using UTF-8 decoding of the bounded byte result

The limit is applied to both the ZIP `media` entry and the post-zstd decompressed payload.

## Version
- versionName: `8.3.149`
- versionCode: `246`

## Static validation
- XML parsed: 28 files
- XML parse errors: 0
- Duplicate IDs within individual layouts: 0
- `runBlocking` in production source: 0
- `GlobalScope` in production source: 0
- `Thread.sleep` in production source: 0
- active `catch(Throwable)`: 0

## Build status
Android Gradle compilation was attempted locally but could not start because the environment could not resolve `downloads.gradle.org`. Therefore Android compilation/CI is **UNVERIFIED** and must be confirmed by the repository CI workflow.
