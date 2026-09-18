# Rovex v8.3.260 — Kotlin Float Literal Compile Fix

## CI defect fixed
GitHub Actions run #766 failed during `:app:testDebugUnitTest` with two Kotlin type errors in `RovexPerformanceLabView.kt`:

- `line 23:207`: `Double` assigned where `Float` was expected.
- `line 25:355`: `Double` assigned where `Float` was expected.

The cause was the unsuffixed decimal literals `6.5` and `7.2` multiplied by Android `DisplayMetrics.density` (`Float`) and assigned to `Paint.textSize` (`Float`).

## Correction
- `6.5` → `6.5f`
- `7.2` → `7.2f`

No behavioral change was intended.

## Validation
- Version: 8.3.260
- VersionCode: 354
- Static/source audit performed after correction.
- Full Android/Gradle build must still be verified by GitHub Actions; local Gradle distribution download is unavailable in the current environment.
