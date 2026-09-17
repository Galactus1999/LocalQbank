# Rovex v8.3.71 Release Audit

- versionName: 8.3.71
- versionCode: 169
- Package: com.localqbank.library
- Baseline: v8.3.70

## Stability changes
- Unified Ben input/response text normalization in `BoundedTextPolicy`.
- Hard-bounded scanning and output; unsafe ISO controls removed; whitespace canonicalized.
- Isolated UTF-16 surrogates are dropped; valid surrogate pairs are preserved; truncation cannot split a pair.
- Added regression tests for null/empty text, malformed UTF-16, valid emoji, hard bounds, and movable-control edge cases.
- Preserved the existing Ben backend boundary and local fallback; no new runtime/model dependency introduced.
- Preserved continuous flashcard counting beyond 100 and the normalized movable Mark/Bookmark coordinates.

## Verification performed
- Pure Kotlin production-policy compilation: PASS.
- Pure Kotlin executable regression harness: PASS (`PURE_CHECK_71_OK`).
- XML parsing: PASS.
- Per-layout duplicate-ID audit: PASS.
- Stale v8.3.70/versionCode 168 scan: PASS.
- CI version identity text checks updated to 8.3.71 / 169.
- Full Android Gradle unit-test execution: NOT LOCALLY VERIFIABLE; Gradle 9.3.1 distribution download is blocked by DNS resolution of services.gradle.org in this environment.

## Web research basis
- Android Macrobenchmark current guidance supports StartupTimingMetric, FrameTimingMetric, and trace metrics for regression testing.
- LiteRT-LM Kotlin API requires background initialization for potentially long engine initialization and explicit resource closing; therefore model integration remains behind the existing Ben adapter boundary until the Android build/benchmark baseline is green.
