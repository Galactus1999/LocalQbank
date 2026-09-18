# Rovex v8.3.70 Release Audit

## Scope
Peak-stability batch built on the v8.3.69 source baseline.

## Changes
- Ben response normalization now bounds text at 16,384 UTF-16 code units, removes unsafe control characters, normalizes whitespace, and truncates without leaving a dangling UTF-16 surrogate pair.
- Ben research input truncation now uses the same surrogate-safe boundary rule.
- Flashcard progress presentation retains continuous counts above 100 and clamps only the visual progress percentage to 0..100.
- Added regression coverage for counter overflow/boundary behavior and Ben response hygiene.
- Version advanced to 8.3.70 / versionCode 168.
- CI release identity gates updated accordingly.

## Verification
- XML parsing: PASS
- Per-layout duplicate IDs: PASS
- stale active version scan: PASS
- pure Kotlin production compilation of changed policy/presentation classes: PASS
- runtime checks of changed pure Kotlin policies: PASS
- Thread.sleep/runBlocking/GlobalScope scan: PASS
- profileable manifest guard: PASS
- zstd Android AAR gate: PASS
- reportFullyDrawn/JankStats lifecycle gates: PASS
- full Gradle Android unit test: NOT VERIFIED locally; Gradle 9.3.1 distribution download is blocked by DNS resolution of services.gradle.org in the current environment.

## Online engineering references checked
- Android Macrobenchmark documentation and metrics guidance.
- Google LiteRT-LM Kotlin API documentation, including background initialization and engine lifecycle requirements.
- Hugging Face LiteRT-LM model catalog.

No new model/runtime dependency was introduced in this stability release.
