# Rovex v8.1.9 Stability Audit

## CI failure fixed
v8.1.8 CI failed at `MainActivity.kt:557:39` because `styleDashboardHero()` referenced `accuracy` without a local variable or parameter.

## Root-cause correction
- Changed `styleDashboardHero()` to `styleDashboardHero(accuracy: Int)`.
- Passed the already-calculated `accuracy` from `renderUi()`.
- Removed the redundant second call from `styleDashboardCards()`.
- This avoids recomputing dashboard state and prevents another unresolved-reference regression.

## Regression scan
- `findViewById<T>()` vs XML IDs/types: 0 missing/type mismatches in static scan.
- Unsafe `execSQL(PRAGMA...)`: 0.
- Legacy native autosize calls: 0.
- Invalid `selectAllOnFocus = ...` property assignment: 0.
- `setSelectAllOnFocus(...)`: retained only where supported by the Android widget API.
- ZIP integrity: verified.
- Previous SQLite, preference migration, typography, image/import, SRS, flashcard and dashboard changes retained.

## Build gate
CI must compile v8.1.9 before installation. Local Gradle distribution is not cached in this environment.
