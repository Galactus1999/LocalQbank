# Rovex v8.3.226 — Build Root-Cause Correction

## Recurrent CI failure
The supplied CI log reached Kotlin compilation after spending 6m56s downloading/preparing the Qualcomm runtime. The concrete compiler failures were both in `RenActivity.kt`: `ThemeManager.background(...)` does not exist; the authoritative API is `ThemeManager.bg(...)`.

## Structural cause
The previous pipeline coupled compile-only work to `prepareQualcommRuntime` through `preBuild`. Consequently, a source-level Kotlin error could trigger a ~2.35 GB Qualcomm download before the compiler reported the error. Static XML/source audits could not catch an unresolved Kotlin symbol.

## Correction
- `RenActivity.kt`: `ThemeManager.background(...)` -> `ThemeManager.bg(...)`.
- Vendor runtime preparation removed from `preBuild`.
- Qualcomm preparation is attached to native-library/package-producing tasks only.
- CI compile/unit/benchmark compilation explicitly excludes `:app:prepareQualcommRuntime`.
- CI caches both the prepared arm64 runtime and the 2.35 GB vendor archive, so a cache miss of the prepared output can reuse the archive without a network download.
- Cache key is based on the preparation script + runtime contract, not app version.
- Runtime cache is saved only after compile/test gates pass, preventing a broken source build from being masked as a successful package cache.
