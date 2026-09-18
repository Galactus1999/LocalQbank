# Rovex v8.1.5 — Stability/UI Recovery Audit

## Root causes addressed
- Disabled global AdaptiveTypographyManager autosizing that was shrinking the home screen and other views to tiny text. Existing XML/Kotlin text sizes are now preserved.
- Replaced the in-app logo alpha-mask animation. The previous complete opaque logo bitmap was being used as a mask, so its entire rectangular background became the animated color block. The header now uses a dedicated mark-only asset.
- Corrected launcher wordmark from REVEX to ROVEX and updated the legacy mdpi launcher raster.
- Restored all SRS settings defaults in the flashcard database, including daily new cards, daily reviews, daily maximum, learning delay, interval, multipliers and leech threshold.
- Made SRS EditText controls explicitly focusable/clickable with readable 16sp text and correct numeric input types; values are validated and persisted on SAVE.

## Regression checks
- SQLite foreign-key setup remains on setForeignKeyConstraintsEnabled(true).
- SQLite WAL remains enableWriteAheadLogging().
- Legacy SharedPreferences numeric migration remains intact.
- findViewById inventory has no references to missing XML IDs.
- FlashcardActivity brace/syntax regression from v8.1.4 remains corrected.
- ZIP integrity checked after packaging.

## Release
- versionName 8.1.5
- versionCode 83

Final gate: CI assembleDebug and device smoke test.
