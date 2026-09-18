# Rovex 8.3.224 — Qualcomm QAIRT/LiteRT CI Cache Hardening

## Purpose
Prevent GitHub-hosted CI from downloading the ~2.35 GB official LiteRT Qualcomm runtime archive on every cache-cold job.

## Changes
- CI restores/saves only the prepared `app/src/main/jniLibs/arm64-v8a` SM8650/Hexagon-v75 runtime payload.
- Cache key is tied to runner OS/architecture, LiteRT 2.2.0, SM8650/v75 target, the Qualcomm preparation script, and the app runtime contract in `app/build.gradle.kts`.
- CI explicitly prewarms the `prepareQualcommRuntime` Gradle task before Kotlin compilation and saves the prepared payload immediately after successful preparation. This means a later compile/test failure does not discard a successfully downloaded runtime cache.
- The release-integrity job now waits for `static-and-unit`, then restores the same cache, preventing two parallel jobs from independently downloading the 2.35 GB archive on a cache miss.
- A cache hit leaves `prepareQualcommRuntime` as a no-op because the existing Gradle `onlyIf` gate verifies the required libraries and v75 stub/skel payload.
- No proprietary Qualcomm archive is committed to source control and no secrets are cached.

## Expected behavior
- First cache-cold main build: one Qualcomm download, then the prepared runtime is cached.
- Subsequent compatible CI runs: no Qualcomm archive download; the prepared runtime is restored directly.
- If GitHub evicts the cache or the runtime contract changes: the build safely regenerates it. GitHub documents cache eviction and a default 10 GB per-repository cache allowance.

## Version
- versionName: 8.3.224
- versionCode: 318
